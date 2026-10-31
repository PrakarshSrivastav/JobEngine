package io.github.prakarshsrivastav.jobengine.service;

import io.github.prakarshsrivastav.jobengine.domain.DeadLetterEntry;
import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.exception.InvalidJobStateException;
import io.github.prakarshsrivastav.jobengine.exception.JobNotFoundException;
import io.github.prakarshsrivastav.jobengine.repository.DeadLetterRepository;
import io.github.prakarshsrivastav.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final JobRepository jobRepository;
    private final JobEventService jobEventService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void moveToDeadLetter(Job job, String finalError) {
        job.setStatus(JobStatus.DEAD);
        job.setErrorMessage(finalError);
        job.setCompletedAt(OffsetDateTime.now());
        job.setWorkerId(null);
        job.setHeartbeatAt(null);
        jobRepository.save(job);

        DeadLetterEntry entry = DeadLetterEntry.builder()
                .jobId(job.getId())
                .failureReason(String.format("Exhausted %d retries", job.getMaxRetries()))
                .finalError(finalError)
                .build();

        deadLetterRepository.save(entry);

        jobEventService.recordTransition(
                job.getId(),
                JobStatus.FAILED,
                JobStatus.DEAD,
                "Exhausted all retries: " + finalError
        );

        log.warn("Job [{}] moved to dead letter queue after {} retries. Reason: {}",
                job.getId(), job.getRetryCount(), finalError);
    }

    @Transactional(readOnly = true)
    public Page<DeadLetterEntry> list(Pageable pageable) {
        return deadLetterRepository.findAll(pageable);
    }

    // Gives a dead job a fresh set of retries. The DLQ row is removed; the event log keeps the history.
    @Transactional
    public Job replay(UUID jobId) {
        Job job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        if (job.getStatus() != JobStatus.DEAD) {
            throw new InvalidJobStateException("Only DEAD jobs can be replayed; job is " + job.getStatus());
        }

        deadLetterRepository.findByJobId(jobId).ifPresent(deadLetterRepository::delete);

        job.setStatus(JobStatus.PENDING);
        job.setRetryCount(0);
        job.setErrorMessage(null);
        job.setScheduledAt(null);
        job.setCompletedAt(null);
        job.setResult(null);
        jobRepository.save(job);

        jobEventService.recordTransition(jobId, JobStatus.DEAD, JobStatus.PENDING, "Replayed from dead letter queue");
        return job;
    }
}
