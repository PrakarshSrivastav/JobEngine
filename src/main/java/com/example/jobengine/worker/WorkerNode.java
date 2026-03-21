package com.example.jobengine.worker;

import com.example.jobengine.domain.Job;
import com.example.jobengine.domain.JobStatus;
import com.example.jobengine.repository.JobRepository;
import com.example.jobengine.service.JobEventService;
import com.example.jobengine.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerNode {

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;
    private final RetryService retryService;

    @Transactional
    public void execute(Job job) {
        try {
            log.info("Worker executing job [{}] type={}", job.getId(), job.getType());

            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.save(job);

            jobEventService.recordTransition(
                    job.getId(),
                    JobStatus.QUEUED,
                    JobStatus.RUNNING,
                    "Execution started"
            );

            // Route to correct handler based on job type
            Object result = switch (job.getType()) {
                case "EMAIL_NOTIFICATION" -> handleEmailNotification(job);
                case "DATA_PROCESSING" -> handleDataProcessing(job);
                case "REPORT_GENERATION" -> handleReportGeneration(job);
                default -> throw new IllegalArgumentException(
                        "Unknown job type: " + job.getType()
                );
            };

            // Success
            job.setStatus(JobStatus.SUCCESS);
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            jobEventService.recordTransition(
                    job.getId(),
                    JobStatus.RUNNING,
                    JobStatus.SUCCESS,
                    "Completed successfully"
            );

            log.info("Job [{}] completed successfully in {}ms",
                    job.getId(),
                    java.time.Duration.between(job.getStartedAt(),
                            job.getCompletedAt()).toMillis());

        } catch (Exception e) {
            log.error("Job [{}] failed: {}", job.getId(), e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);

            jobEventService.recordTransition(
                    job.getId(),
                    JobStatus.RUNNING,
                    JobStatus.FAILED,
                    e.getMessage()
            );

            retryService.handleFailure(job, e.getMessage());
        }
    }

    private String handleEmailNotification(Job job) {
        log.info("Sending email to: {}", job.getPayload().get("to"));
        // Simulate processing time
        sleep(500);
        return "Email sent to " + job.getPayload().get("to");
    }

    private String handleDataProcessing(Job job) {
        log.info("Processing data job: {}", job.getId());
        sleep(1000);
        return "Data processed successfully";
    }

    private String handleReportGeneration(Job job) {
        log.info("Generating report: {}", job.getId());
        sleep(1500);
        return "Report generated successfully";
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}