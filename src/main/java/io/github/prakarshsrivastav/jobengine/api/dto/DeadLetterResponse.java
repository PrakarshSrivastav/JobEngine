package io.github.prakarshsrivastav.jobengine.api.dto;

import io.github.prakarshsrivastav.jobengine.domain.DeadLetterEntry;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeadLetterResponse(UUID id, UUID jobId, String failureReason, String finalError, OffsetDateTime movedAt) {
    public static DeadLetterResponse from(DeadLetterEntry entry) {
        return new DeadLetterResponse(entry.getId(), entry.getJobId(), entry.getFailureReason(),
                entry.getFinalError(), entry.getMovedAt());
    }
}
