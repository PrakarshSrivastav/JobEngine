package com.example.jobengine.service;

import com.example.jobengine.domain.Job;
import com.example.jobengine.domain.JobStatus;
import com.example.jobengine.repository.JobRepository;
import com.example.jobengine.worker.WorkerNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDispatcherService {

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;
    private final WorkerNode workerNode;

    @Scheduled(fixedDelay = 2000)
    public void dispatch() {
        try {
            claimAndDispatch();
        } catch (Exception e) {
            log.error("Dispatcher error: {}", e.getMessage());
        }
    }

    @Transactional
    public void claimAndDispatch() {
        Optional<Job> candidate = jobRepository.findAndLockNextPendingJob();

        if (candidate.isEmpty()) {
            return;
        }

        Job job = candidate.get();

        job.setStatus(JobStatus.QUEUED);
        job.setWorkerId(generateWorkerId());
        jobRepository.save(job);

        jobEventService.recordTransition(
                job.getId(),
                job.getStatus() == JobStatus.RETRYING ? JobStatus.RETRYING : JobStatus.PENDING,
                JobStatus.QUEUED,
                "Claimed by worker: " + job.getWorkerId()
        );

        log.info("Dispatched job [{}] type={} priority={}",
                job.getId(), job.getType(), job.getPriority());

        workerNode.execute(job);
    }

    private String generateWorkerId() {
        return "worker-" + Thread.currentThread().getName();
    }
}