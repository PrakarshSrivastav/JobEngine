package io.github.prakarshsrivastav.jobengine.api;

import io.github.prakarshsrivastav.jobengine.api.dto.JobEventResponse;
import io.github.prakarshsrivastav.jobengine.api.dto.JobResponse;
import io.github.prakarshsrivastav.jobengine.api.dto.SubmitJobRequest;
import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import io.github.prakarshsrivastav.jobengine.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> submitJob(@Valid @RequestBody SubmitJobRequest request) {
        Job saved = jobService.submit(request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(saved.getId()))
                .body(JobResponse.from(saved));
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return JobResponse.from(jobService.get(id));
    }

    @GetMapping
    public Page<JobResponse> listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return jobService.list(status, type, pageable).map(JobResponse::from);
    }

    @GetMapping("/{id}/events")
    public List<JobEventResponse> getJobEvents(@PathVariable UUID id) {
        return jobService.history(id).stream().map(JobEventResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancelJob(@PathVariable UUID id) {
        return JobResponse.from(jobService.cancel(id));
    }
}
