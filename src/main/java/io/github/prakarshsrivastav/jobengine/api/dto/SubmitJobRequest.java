package io.github.prakarshsrivastav.jobengine.api.dto;

import io.github.prakarshsrivastav.jobengine.domain.JobPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record SubmitJobRequest(
        @NotBlank @Size(max = 100) String type,
        JobPriority priority,
        Map<String, Object> payload,
        @Min(0) @Max(10) Integer maxRetries,
        @Min(0) @Max(86_400) Long delaySeconds
) {
}
