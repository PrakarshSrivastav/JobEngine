package io.github.prakarshsrivastav.jobengine.worker;

import io.github.prakarshsrivastav.jobengine.config.JobEngineProperties;
import io.github.prakarshsrivastav.jobengine.service.JobLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Safe to run on every node at once: expired rows are locked with SKIP LOCKED.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jobengine.worker.enabled", havingValue = "true", matchIfMissing = true)
public class LeaseReaper {

    private static final int BATCH_SIZE = 100;

    private final JobLifecycleService lifecycle;
    private final JobEngineProperties properties;

    @Scheduled(fixedDelayString = "${jobengine.worker.reaper-interval:10s}")
    public void reap() {
        try {
            lifecycle.reapExpiredLeases(properties.getWorker().getLeaseTimeout(), BATCH_SIZE);
        } catch (Exception e) {
            log.error("Lease reaper failed: {}", e.getMessage());
        }
    }
}
