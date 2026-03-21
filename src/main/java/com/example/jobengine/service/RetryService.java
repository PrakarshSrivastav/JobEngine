package com.example.jobengine.service;

import com.example.jobengine.domain.Job;
import com.example.jobengine.domain.JobStatus;
import com.example.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;
    private final DeadLetterService deadLetterService;

    @Transactional
    public void handleFailure(Job job, String errorMessage) {
        job.setErrorMessage(errorMessage);

        if (job.getRetryCount() >= job.getMaxRetries()) {
            deadLetterService.moveToDeadLetter(job, errorMessage);
            return;
        }

        int nextRetryCount = job.getRetryCount() + 1;
        long backoffSeconds = (long) Math.pow(2, nextRetryCount);
        OffsetDateTime nextScheduledAt = OffsetDateTime.now().plusSeconds(backoffSeconds);

        job.setRetryCount(nextRetryCount);
        job.setStatus(JobStatus.RETRYING);
        job.setScheduledAt(nextScheduledAt);
        job.setWorkerId(null);
        jobRepository.save(job);

        jobEventService.recordTransition(
                job.getId(),
                JobStatus.FAILED,
                JobStatus.RETRYING,
                String.format("Retry %d/%d scheduled in %ds. Error: %s",
                        nextRetryCount, job.getMaxRetries(), backoffSeconds, errorMessage)
        );

        log.warn("Job [{}] failed. Retry {}/{} scheduled in {}s",
                job.getId(), nextRetryCount, job.getMaxRetries(), backoffSeconds);
    }
}