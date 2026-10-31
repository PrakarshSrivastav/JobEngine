package io.github.prakarshsrivastav.jobengine.service;

import io.github.prakarshsrivastav.jobengine.domain.JobEvent;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.repository.JobEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobEventService {

    private final JobEventRepository jobEventRepository;

    // Joins the caller's transaction so the audit trail commits (or rolls back) together
    // with the state change it describes.
    @Transactional
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
