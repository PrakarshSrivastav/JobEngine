package io.github.prakarshsrivastav.jobengine.worker;

import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.handler.JobHandlerRegistry;
import io.github.prakarshsrivastav.jobengine.service.JobLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs a single claimed job: QUEUED -> RUNNING -> handler -> SUCCESS or FAILED.
 * The handler runs outside any transaction, so a slow job never pins a DB connection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {

    private final JobLifecycleService lifecycle;
    private final JobHandlerRegistry handlers;

    public void run(UUID jobId, String workerId) {
        Optional<Job> started = lifecycle.markRunning(jobId, workerId);
        if (started.isEmpty()) {
            return;
        }
        Job job = started.get();

        Map<String, Object> result;
        try {
            result = handlers.get(job.getType()).handle(job.getPayload());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lifecycle.fail(jobId, workerId, "Interrupted: worker shutting down");
            return;
        } catch (Exception e) {
            log.error("Job [{}] failed: {}", jobId, e.getMessage());
            lifecycle.fail(jobId, workerId, describe(e));
            return;
        }

        lifecycle.complete(jobId, workerId, result);
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
