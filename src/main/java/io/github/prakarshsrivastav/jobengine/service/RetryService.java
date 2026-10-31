package io.github.prakarshsrivastav.jobengine.service;

import io.github.prakarshsrivastav.jobengine.config.JobEngineProperties;
import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;
    private final DeadLetterService deadLetterService;
    private final JobEngineProperties properties;

    // Expects a job already moved to FAILED and locked by the caller's transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void handleFailure(Job job, String errorMessage) {
        job.setErrorMessage(errorMessage);

        if (job.getRetryCount() >= job.getMaxRetries()) {
            deadLetterService.moveToDeadLetter(job, errorMessage);
            return;
        }

        int nextRetryCount = job.getRetryCount() + 1;
        Duration backoff = backoffFor(nextRetryCount);

        job.setRetryCount(nextRetryCount);
        job.setStatus(JobStatus.RETRYING);
        job.setScheduledAt(OffsetDateTime.now().plus(backoff));
        job.setWorkerId(null);
        job.setHeartbeatAt(null);
        jobRepository.save(job);

        jobEventService.recordTransition(
                job.getId(),
                JobStatus.FAILED,
                JobStatus.RETRYING,
                String.format("Retry %d/%d scheduled in %dms. Error: %s",
                        nextRetryCount, job.getMaxRetries(), backoff.toMillis(), errorMessage)
        );

        log.warn("Job [{}] failed. Retry {}/{} scheduled in {}ms",
                job.getId(), nextRetryCount, job.getMaxRetries(), backoff.toMillis());
    }

    // base * 2^(attempt-1), capped: 2s, 4s, 8s, ... with the default base.
    Duration backoffFor(int attempt) {
        Duration base = properties.getRetry().getBaseBackoff();
        Duration max = properties.getRetry().getMaxBackoff();
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration backoff = base.multipliedBy(multiplier);
        return backoff.compareTo(max) > 0 ? max : backoff;
    }
}
