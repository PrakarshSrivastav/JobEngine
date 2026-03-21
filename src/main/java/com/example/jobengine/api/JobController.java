package com.example.jobengine.api;

import com.example.jobengine.domain.Job;
import com.example.jobengine.domain.JobPriority;
import com.example.jobengine.domain.JobStatus;
import com.example.jobengine.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;

    // Submit a new job
    @PostMapping
    public ResponseEntity<Job> submitJob(@RequestBody Map<String, Object> request) {
        Job job = Job.builder()
                .type((String) request.get("type"))
                .priority(request.containsKey("priority")
                        ? JobPriority.valueOf((String) request.get("priority"))
                        : JobPriority.NORMAL)
                .payload((Map<String, Object>) request.get("payload"))
                .status(JobStatus.PENDING)
                .build();

        Job saved = jobRepository.save(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Get job by ID
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get all jobs
    @GetMapping
    public ResponseEntity<?> getAllJobs() {
        return ResponseEntity.ok(jobRepository.findAll());
    }
}