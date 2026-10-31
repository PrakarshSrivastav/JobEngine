package io.github.prakarshsrivastav.jobengine.api.dto;

import io.github.prakarshsrivastav.jobengine.domain.JobStatus;

import java.util.Map;
import java.util.Set;

public record StatsResponse(Map<JobStatus, Long> jobsByStatus, long totalJobs, Set<String> supportedTypes) {
}
