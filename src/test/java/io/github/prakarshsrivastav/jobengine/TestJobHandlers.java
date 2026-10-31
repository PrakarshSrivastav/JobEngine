package io.github.prakarshsrivastav.jobengine;

import io.github.prakarshsrivastav.jobengine.handler.JobHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Deterministic handlers for exercising the failure paths.
@TestConfiguration(proxyBeanMethods = false)
public class TestJobHandlers {

    @Bean
    JobHandler alwaysFailHandler() {
        return new JobHandler() {
            @Override
            public String type() {
                return "ALWAYS_FAIL";
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> payload) {
                throw new IllegalStateException("boom");
            }
        };
    }

    // Fails the first attempt for each payload.key, succeeds afterwards.
    @Bean
    JobHandler flakyHandler() {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return new JobHandler() {
            @Override
            public String type() {
                return "FLAKY";
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> payload) {
                if (seen.add(payload.get("key"))) {
                    throw new IllegalStateException("transient failure");
                }
                return Map.of("ok", true);
            }
        };
    }

    @Bean
    JobHandler sleepHandler() {
        return new JobHandler() {
            @Override
            public String type() {
                return "SLEEP";
            }

            @Override
            public Map<String, Object> handle(Map<String, Object> payload) throws InterruptedException {
                Thread.sleep(500);
                return Map.of("slept", 500);
            }
        };
    }
}
