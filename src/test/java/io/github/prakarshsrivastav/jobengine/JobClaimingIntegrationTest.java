package io.github.prakarshsrivastav.jobengine;

import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobPriority;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.repository.DeadLetterRepository;
import io.github.prakarshsrivastav.jobengine.repository.JobRepository;
import io.github.prakarshsrivastav.jobengine.service.JobLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Worker disabled: the test drives claiming and reaping by hand, simulating several nodes.
@Import({TestcontainersConfiguration.class, TestJobHandlers.class})
@SpringBootTest(properties = "jobengine.worker.enabled=false")
@ActiveProfiles("test")
class JobClaimingIntegrationTest {

    @Autowired
    JobLifecycleService lifecycle;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    DeadLetterRepository deadLetterRepository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE jobs CASCADE");
    }

    @Test
    void concurrentWorkersNeverClaimTheSameJob() throws Exception {
        int jobs = 500;
        int workers = 8;
        jdbc.update("INSERT INTO jobs (type) SELECT 'DATA_PROCESSING' FROM generate_series(1, ?)", jobs);

        Map<UUID, String> claimedBy = new ConcurrentHashMap<>();
        AtomicInteger duplicates = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            String node = "node-" + i;
            futures.add(pool.submit(() -> {
                Optional<Job> claimed;
                while ((claimed = lifecycle.claimNext(node)).isPresent()) {
                    if (claimedBy.putIfAbsent(claimed.get().getId(), node) != null) {
                        duplicates.incrementAndGet();
                    }
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(duplicates).hasValue(0);
        assertThat(claimedBy).hasSize(jobs);
        assertThat(jobRepository.findByStatus(JobStatus.QUEUED, org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(jobs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_events WHERE to_status = 'QUEUED'", Long.class))
                .isEqualTo(jobs);
        // Work was actually spread out rather than serialized onto one worker.
        assertThat(claimedBy.values().stream().distinct().count()).isGreaterThan(1);
    }

    @Test
    void claimsHighestPriorityFirstAndSkipsDelayedJobs() {
        insert(JobPriority.LOW, null);
        insert(JobPriority.CRITICAL, "now() + interval '1 hour'");
        insert(JobPriority.HIGH, null);
        insert(JobPriority.NORMAL, null);

        List<JobPriority> order = new ArrayList<>();
        Optional<Job> claimed;
        while ((claimed = lifecycle.claimNext("node")).isPresent()) {
            order.add(claimed.get().getPriority());
        }

        assertThat(order).containsExactly(JobPriority.HIGH, JobPriority.NORMAL, JobPriority.LOW);
    }

    @Test
    void expiredLeaseIsReapedAndCountsAsAFailedAttempt() {
        UUID id = insertRunning("crashed-node", 0, 3);

        int reaped = lifecycle.reapExpiredLeases(Duration.ofSeconds(30), 100);

        assertThat(reaped).isEqualTo(1);
        Job job = jobRepository.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getWorkerId()).isNull();
        assertThat(job.getErrorMessage()).contains("Lease expired");
    }

    @Test
    void lateResultFromWorkerThatLostItsLeaseIsDropped() {
        UUID id = insertRunning("slow-node", 0, 3);
        lifecycle.reapExpiredLeases(Duration.ofSeconds(30), 100);

        boolean accepted = lifecycle.complete(id, "slow-node", Map.of("late", true));

        assertThat(accepted).isFalse();
        Job job = jobRepository.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RETRYING);
        assertThat(job.getResult()).isNull();
    }

    @Test
    void reapingAJobWithNoRetriesLeftSendsItToTheDeadLetterQueue() {
        UUID id = insertRunning("crashed-node", 3, 3);

        lifecycle.reapExpiredLeases(Duration.ofSeconds(30), 100);

        assertThat(jobRepository.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(deadLetterRepository.existsByJobId(id)).isTrue();
    }

    @Test
    void liveLeaseIsNotReaped() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (id, type, status, worker_id, heartbeat_at, started_at)
                VALUES (?, 'DATA_PROCESSING', 'RUNNING', 'healthy-node', now(), now())
                """, id);

        assertThat(lifecycle.reapExpiredLeases(Duration.ofSeconds(30), 100)).isZero();
        assertThat(lifecycle.heartbeat("healthy-node", List.of(id))).isEqualTo(1);
        assertThat(lifecycle.heartbeat("other-node", List.of(id))).isZero();
    }

    private void insert(JobPriority priority, String scheduledAtSql) {
        jdbc.update("INSERT INTO jobs (type, priority, scheduled_at) VALUES ('DATA_PROCESSING', ?::job_priority, "
                + (scheduledAtSql == null ? "NULL" : scheduledAtSql) + ")", priority.name());
    }

    private UUID insertRunning(String workerId, int retryCount, int maxRetries) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO jobs (id, type, status, worker_id, heartbeat_at, started_at, retry_count, max_retries)
                VALUES (?, 'DATA_PROCESSING', 'RUNNING', ?, now() - interval '1 hour', now() - interval '1 hour', ?, ?)
                """, id, workerId, retryCount, maxRetries);
        return id;
    }
}
