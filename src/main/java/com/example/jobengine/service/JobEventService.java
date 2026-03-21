package com.example.jobengine.service;

import com.example.jobengine.domain.JobEvent;
import com.example.jobengine.domain.JobStatus;
import com.example.jobengine.repository.JobEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobEventService {

    private final JobEventRepository jobEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransition(UUID jobId, JobStatus from, JobStatus to, String message) {
        JobEvent event = JobEvent.builder()
                .jobId(jobId)
                .fromStatus(from)
                .toStatus(to)
                .message(message)
                .build();

        jobEventRepository.save(event);
        log.info("Job [{}] transitioned {} -> {} | {}", jobId, from, to, message);
    }

    @Transactional(readOnly = true)
    public List<JobEvent> getJobHistory(UUID jobId) {
        return jobEventRepository.findByJobIdOrderByOccurredAtAsc(jobId);
    }
}