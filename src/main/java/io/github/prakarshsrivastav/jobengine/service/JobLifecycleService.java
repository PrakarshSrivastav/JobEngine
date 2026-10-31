package io.github.prakarshsrivastav.jobengine.service;

import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every state change a worker makes, each in its own short transaction. Handler code runs
 * between these calls with no transaction or connection held.
 *
 * <p>complete/fail are fenced on (status = RUNNING, worker_id = caller): if the lease expired
 * and the job was reaped or re-claimed elsewhere, a late result from the old worker is dropped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLifecycleService {

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;
    private final RetryService retryService;

    @Transactional
    public Optional<Job> claimNext(String workerId) {
        Optional<Job> candidate = jobRepository.findAndLockNextRunnable();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        Job job = candidate.get();
        JobStatus previous = job.getStatus();

        job.setStatus(JobStatus.QUEUED);
        job.setWorkerId(workerId);
        job.setHeartbeatAt(OffsetDateTime.now());
        jobRepository.save(job);

        jobEventService.recordTransition(job.getId(), previous, JobStatus.QUEUED, "Claimed by " + workerId);
        log.info("Claimed job [{}] type={} priority={}", job.getId(), job.getType(), job.getPriority());
        return Optional.of(job);
    }

    @Transactional
    public Optional<Job> markRunning(UUID jobId, String workerId) {
        Job job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.QUEUED || !workerId.equals(job.getWorkerId())) {
            log.warn("Job [{}] is no longer held by {}; skipping execution", jobId, workerId);
            return Optional.empty();
        }

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        job.setHeartbeatAt(OffsetDateTime.now());
        jobRepository.save(job);

        jobEventService.recordTransition(jobId, JobStatus.QUEUED, JobStatus.RUNNING, "Execution started on " + workerId);
        return Optional.of(job);
    }

    @Transactional
    public boolean complete(UUID jobId, String workerId, Map<String, Object> result) {
        Optional<Job> held = lockIfHeld(jobId, workerId);
        if (held.isEmpty()) {
            return false;
        }

        Job job = held.get();
        job.setStatus(JobStatus.SUCCESS);
        job.setResult(result);
        job.setErrorMessage(null);
        job.setCompletedAt(OffsetDateTime.now());
        job.setHeartbeatAt(null);
        jobRepository.save(job);

        long tookMs = Duration.between(job.getStartedAt(), job.getCompletedAt()).toMillis();
        jobEventService.recordTransition(jobId, JobStatus.RUNNING, JobStatus.SUCCESS,
                "Completed in " + tookMs + "ms");
        return true;
    }

    @Transactional
    public boolean fail(UUID jobId, String workerId, String errorMessage) {
        Optional<Job> held = lockIfHeld(jobId, workerId);
        if (held.isEmpty()) {
            return false;
        }

        Job job = held.get();
        job.setStatus(JobStatus.FAILED);
        jobEventService.recordTransition(jobId, JobStatus.RUNNING, JobStatus.FAILED, errorMessage);
        retryService.handleFailure(job, errorMessage);
        return true;
    }

    @Transactional
    public int heartbeat(String workerId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return 0;
        }
        return jobRepository.heartbeat(workerId, jobIds);
    }

    // Takes back jobs whose worker stopped heartbeating and counts it as a failed attempt.
    @Transactional
    public int reapExpiredLeases(Duration leaseTimeout, int batchSize) {
        List<Job> expired = jobRepository.findAndLockExpiredLeases(OffsetDateTime.now().minus(leaseTimeout), batchSize);
        for (Job job : expired) {
            String error = "Lease expired: worker " + job.getWorkerId() + " stopped heartbeating";
            JobStatus previous = job.getStatus();
            job.setStatus(JobStatus.FAILED);
            jobEventService.recordTransition(job.getId(), previous, JobStatus.FAILED, error);
            retryService.handleFailure(job, error);
        }
        if (!expired.isEmpty()) {
            log.warn("Reaped {} job(s) with expired leases", expired.size());
        }
        return expired.size();
    }

    private Optional<Job> lockIfHeld(UUID jobId, String workerId) {
        Optional<Job> job = jobRepository.findByIdForUpdate(jobId)
                .filter(j -> j.getStatus() == JobStatus.RUNNING && workerId.equals(j.getWorkerId()));
        if (job.isEmpty()) {
            log.warn("Dropping stale result for job [{}] from {}: lease was lost", jobId, workerId);
        }
        return job;
    }
}
