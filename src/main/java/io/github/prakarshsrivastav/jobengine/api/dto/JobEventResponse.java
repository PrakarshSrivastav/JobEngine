package io.github.prakarshsrivastav.jobengine.api.dto;

import io.github.prakarshsrivastav.jobengine.domain.JobEvent;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;

import java.time.OffsetDateTime;

public record JobEventResponse(JobStatus fromStatus, JobStatus toStatus, String message, OffsetDateTime occurredAt) {
    public static JobEventResponse from(JobEvent event) {
        return new JobEventResponse(event.getFromStatus(), event.getToStatus(), event.getMessage(), event.getOccurredAt());
    }
}
