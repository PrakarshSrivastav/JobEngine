package io.github.prakarshsrivastav.jobengine.service;

import io.github.prakarshsrivastav.jobengine.api.dto.StatsResponse;
import io.github.prakarshsrivastav.jobengine.api.dto.SubmitJobRequest;
import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobEvent;
import io.github.prakarshsrivastav.jobengine.domain.JobPriority;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.exception.InvalidJobStateException;
import io.github.prakarshsrivastav.jobengine.exception.JobNotFoundException;
import io.github.prakarshsrivastav.jobengine.exception.UnknownJobTypeException;
import io.github.prakarshsrivastav.jobengine.handler.JobHandlerRegistry;
import io.github.prakarshsrivastav.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;
    private final JobHandlerRegistry handlers;

    @Transactional
    public Job submit(SubmitJobRequest request) {
        if (!handlers.supports(request.type())) {
            throw new UnknownJobTypeException(request.type(), handlers.supportedTypes());
        }

        Job job = Job.builder()
                .type(request.type())
                .priority(request.priority() != null ? request.priority() : JobPriority.NORMAL)
                .payload(request.payload())
                .maxRetries(request.maxRetries() != null ? request.maxRetries() : 3)
                .scheduledAt(request.delaySeconds() != null && request.delaySeconds() > 0
                        ? OffsetDateTime.now().plusSeconds(request.delaySeconds())
                        : null)
                .status(JobStatus.PENDING)
                .build();

        Job saved = jobRepository.save(job);
        jobEventService.recordTransition(saved.getId(), null, JobStatus.PENDING, "Submitted");
        return saved;
    }

    @Transactional(readOnly = true)
    public Job get(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Job> list(JobStatus status, String type, Pageable pageable) {
        if (status != null && type != null) {
            return jobRepository.findByStatusAndType(status, type, pageable);
        }
        if (status != null) {
            return jobRepository.findByStatus(status, pageable);
        }
        if (type != null) {
            return jobRepository.findByType(type, pageable);
        }
        return jobRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<JobEvent> history(UUID id) {
        if (!jobRepository.existsById(id)) {
            throw new JobNotFoundException(id);
        }
        return jobEventService.getJobHistory(id);
    }

    // Only jobs no worker holds yet can be cancelled; running handlers are not interrupted.
    @Transactional
    public Job cancel(UUID id) {
        Job job = jobRepository.findByIdForUpdate(id).orElseThrow(() -> new JobNotFoundException(id));
        JobStatus previous = job.getStatus();
        if (previous != JobStatus.PENDING && previous != JobStatus.RETRYING) {
            throw new InvalidJobStateException("Only PENDING or RETRYING jobs can be cancelled; job is " + previous);
        }

        job.setStatus(JobStatus.CANCELLED);
        job.setCompletedAt(OffsetDateTime.now());
        jobRepository.save(job);

        jobEventService.recordTransition(id, previous, JobStatus.CANCELLED, "Cancelled via API");
        return job;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            counts.put(status, 0L);
        }
        long total = 0;
        for (Object[] row : jobRepository.countGroupedByStatus()) {
            long count = ((Number) row[1]).longValue();
            counts.put(JobStatus.valueOf((String) row[0]), count);
            total += count;
        }
        return new StatsResponse(counts, total, handlers.supportedTypes());
    }
}
