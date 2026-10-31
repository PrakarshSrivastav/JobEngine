package io.github.prakarshsrivastav.jobengine;

import io.github.prakarshsrivastav.jobengine.api.dto.SubmitJobRequest;
import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobEvent;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.repository.DeadLetterRepository;
import io.github.prakarshsrivastav.jobengine.service.DeadLetterService;
import io.github.prakarshsrivastav.jobengine.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Runs with the real dispatcher, worker pool and scheduler.
@Import({TestcontainersConfiguration.class, TestJobHandlers.class})
@SpringBootTest
@ActiveProfiles("test")
class JobExecutionIntegrationTest {

    @Autowired
    JobService jobService;

    @Autowired
    DeadLetterService deadLetterService;

    @Autowired
    DeadLetterRepository deadLetterRepository;

    @Test
    void successfulJobStoresResultAndFullHistory() {
        Job job = submit("DATA_PROCESSING", Map.of("records", 42), 3);

        Job done = awaitStatus(job.getId(), JobStatus.SUCCESS);

        assertThat(done.getResult()).containsEntry("recordsProcessed", 42);
        assertThat(done.getStartedAt()).isNotNull();
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(transitions(job.getId())).containsExactly(
                "null->PENDING", "PENDING->QUEUED", "QUEUED->RUNNING", "RUNNING->SUCCESS");
    }

    @Test
    void transientFailureIsRetriedThenSucceeds() {
        Job job = submit("FLAKY", Map.of("key", UUID.randomUUID().toString()), 3);

        Job done = awaitStatus(job.getId(), JobStatus.SUCCESS);

        assertThat(done.getRetryCount()).isEqualTo(1);
        // The claim after a retry must be recorded as RETRYING -> QUEUED, not PENDING -> QUEUED.
        assertThat(transitions(job.getId())).containsExactly(
                "null->PENDING", "PENDING->QUEUED", "QUEUED->RUNNING", "RUNNING->FAILED", "FAILED->RETRYING",
                "RETRYING->QUEUED", "QUEUED->RUNNING", "RUNNING->SUCCESS");
    }

    @Test
    void exhaustedRetriesGoToDeadLetterQueueAndCanBeReplayed() {
        Job job = submit("ALWAYS_FAIL", null, 2);

        Job dead = awaitStatus(job.getId(), JobStatus.DEAD);

        assertThat(dead.getRetryCount()).isEqualTo(2);
        assertThat(dead.getErrorMessage()).contains("boom");
        assertThat(deadLetterRepository.findByJobId(job.getId())).isPresent();

        Job replayed = deadLetterService.replay(job.getId());
        assertThat(replayed.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(replayed.getRetryCount()).isZero();

        // It still fails, so it lands back in the DLQ with a fresh set of retries spent.
        awaitStatus(job.getId(), JobStatus.DEAD);
        assertThat(transitions(job.getId())).contains("DEAD->PENDING");
        assertThat(deadLetterRepository.findByJobId(job.getId())).isPresent();
    }

    @Test
    void jobsRunInParallelAcrossTheWorkerPool() {
        // 8 x 500ms jobs on 4 threads: ~1s in parallel, 4s+ if executed one at a time.
        List<UUID> ids = IntStream.range(0, 8)
                .mapToObj(i -> submit("SLEEP", null, 0).getId())
                .toList();

        await().atMost(Duration.ofMillis(3000)).pollInterval(Duration.ofMillis(50)).until(() ->
                ids.stream().allMatch(id -> jobService.get(id).getStatus() == JobStatus.SUCCESS));
    }

    private Job submit(String type, Map<String, Object> payload, int maxRetries) {
        return jobService.submit(new SubmitJobRequest(type, null, payload, maxRetries, null));
    }

    private Job awaitStatus(UUID id, JobStatus status) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> jobService.get(id).getStatus() == status);
        return jobService.get(id);
    }

    private List<String> transitions(UUID id) {
        return jobService.history(id).stream()
                .map(JobExecutionIntegrationTest::transition)
                .toList();
    }

    private static String transition(JobEvent e) {
        return e.getFromStatus() + "->" + e.getToStatus();
    }
}
