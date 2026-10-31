package io.github.prakarshsrivastav.jobengine.api.dto;

import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobPriority;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        JobStatus status,
        JobPriority priority,
        Map<String, Object> payload,
        Map<String, Object> result,
        String errorMessage,
        int retryCount,
        int maxRetries,
        String workerId,
        OffsetDateTime createdAt,
        OffsetDateTime scheduledAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(), job.getType(), job.getStatus(), job.getPriority(),
                job.getPayload(), job.getResult(), job.getErrorMessage(),
                job.getRetryCount(), job.getMaxRetries(), job.getWorkerId(),
                job.getCreatedAt(), job.getScheduledAt(), job.getStartedAt(), job.getCompletedAt()
        );
    }
}
