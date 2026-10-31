package io.github.prakarshsrivastav.jobengine.handler;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ReportGenerationHandler implements JobHandler {

    @Override
    public String type() {
        return "REPORT_GENERATION";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> payload) throws InterruptedException {
        Thread.sleep(1500); // simulated rendering
        String name = payload != null && payload.get("name") != null ? payload.get("name").toString() : "report";
        return Map.of("reportUrl", "/reports/" + name + "-" + UUID.randomUUID() + ".pdf");
    }
}
