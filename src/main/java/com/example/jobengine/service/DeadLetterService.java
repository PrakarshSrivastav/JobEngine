package com.example.jobengine.service;

import com.example.jobengine.domain.DeadLetterEntry;
import com.example.jobengine.domain.Job;
import com.example.jobengine.domain.JobStatus;
import com.example.jobengine.repository.DeadLetterRepository;
import com.example.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final JobRepository jobRepository;
    private final JobEventService jobEventService;

    @Transactional
    public void moveToDeadLetter(Job job, String failureReason) {
        job.setStatus(JobStatus.DEAD);
        job.setErrorMessage(failureReason);
        jobRepository.save(job);

        DeadLetterEntry entry = DeadLetterEntry.builder()
                .jobId(job.getId())
                .failureReason(failureReason)
                .finalError(job.getErrorMessage())
                .build();

        deadLetterRepository.save(entry);

        jobEventService.recordTransition(
                job.getId(),
                JobStatus.FAILED,
                JobStatus.DEAD,
                "Exhausted all retries: " + failureReason
        );

        log.warn("Job [{}] moved to dead letter queue after {} retries. Reason: {}",
                job.getId(), job.getRetryCount(), failureReason);
    }
}