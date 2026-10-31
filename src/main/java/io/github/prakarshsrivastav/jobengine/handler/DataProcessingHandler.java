package io.github.prakarshsrivastav.jobengine.handler;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DataProcessingHandler implements JobHandler {

    @Override
    public String type() {
        return "DATA_PROCESSING";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> payload) throws InterruptedException {
        Thread.sleep(1000); // simulated batch work
        int records = payload != null && payload.get("records") instanceof Number n ? n.intValue() : 1000;
        return Map.of("recordsProcessed", records);
    }
}
