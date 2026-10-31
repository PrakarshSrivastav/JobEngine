package io.github.prakarshsrivastav.jobengine.worker;

import io.github.prakarshsrivastav.jobengine.config.JobEngineProperties;
import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.service.JobLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Polls for runnable jobs and fans them out to the worker pool. A semaphore sized to the
 * pool means we only claim what we can start right away, so idle capacity on other nodes
 * picks up the rest instead of jobs sitting in this node's memory.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jobengine.worker.enabled", havingValue = "true", matchIfMissing = true)
public class JobDispatcher {

    private final JobLifecycleService lifecycle;
    private final JobRunner runner;
    private final ThreadPoolTaskExecutor executor;
    private final String workerId;
    private final Semaphore capacity;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public JobDispatcher(JobLifecycleService lifecycle,
                         JobRunner runner,
                         @Qualifier("jobExecutor") ThreadPoolTaskExecutor executor,
                         JobEngineProperties properties) {
        this.lifecycle = lifecycle;
        this.runner = runner;
        this.executor = executor;
        this.workerId = properties.getNodeId();
        this.capacity = new Semaphore(properties.getWorker().getThreads());
        log.info("Worker node {} started with {} threads", workerId, properties.getWorker().getThreads());
    }

    @Scheduled(fixedDelayString = "${jobengine.worker.poll-interval:500ms}")
    public void poll() {
        try {
            while (capacity.tryAcquire()) {
                Optional<Job> claimed = lifecycle.claimNext(workerId);
                if (claimed.isEmpty()) {
                    capacity.release();
                    return;
                }
                submit(claimed.get().getId());
            }
        } catch (Exception e) {
            log.error("Dispatcher error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${jobengine.worker.heartbeat-interval:5s}")
    public void heartbeat() {
        try {
            lifecycle.heartbeat(workerId, Set.copyOf(inFlight));
        } catch (Exception e) {
            log.error("Heartbeat failed: {}", e.getMessage());
        }
    }

    public String workerId() {
        return workerId;
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    private void submit(UUID jobId) {
        inFlight.add(jobId);
        try {
            executor.execute(() -> {
                try {
                    runner.run(jobId, workerId);
                } catch (Exception e) {
                    // The lease reaper will recover the job if we could not record the outcome.
                    log.error("Unexpected error running job [{}]: {}", jobId, e.getMessage(), e);
                } finally {
                    inFlight.remove(jobId);
                    capacity.release();
                }
            });
        } catch (RuntimeException e) {
            inFlight.remove(jobId);
            capacity.release();
            throw e;
        }
    }
}
