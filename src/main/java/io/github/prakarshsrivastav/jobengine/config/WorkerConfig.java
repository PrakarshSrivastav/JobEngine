package io.github.prakarshsrivastav.jobengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(JobEngineProperties.class)
public class WorkerConfig {

    @Bean
    @ConditionalOnProperty(name = "jobengine.worker.enabled", havingValue = "true", matchIfMissing = true)
    public ThreadPoolTaskExecutor jobExecutor(JobEngineProperties properties) {
        int threads = properties.getWorker().getThreads();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        // The dispatcher never has more than `threads` jobs in flight, so this queue only
        // absorbs the gap between a task releasing its permit and its thread going idle.
        executor.setQueueCapacity(threads);
        executor.setThreadNamePrefix("job-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
