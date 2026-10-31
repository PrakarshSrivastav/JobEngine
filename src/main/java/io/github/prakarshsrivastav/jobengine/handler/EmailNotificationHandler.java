package io.github.prakarshsrivastav.jobengine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Component
public class EmailNotificationHandler implements JobHandler {

    @Override
    public String type() {
        return "EMAIL_NOTIFICATION";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> payload) throws InterruptedException {
        Object to = payload == null ? null : payload.get("to");
        if (to == null) {
            throw new IllegalArgumentException("payload.to is required");
        }
        log.info("Sending email to: {}", to);
        Thread.sleep(500); // simulated SMTP round trip
        return Map.of("deliveredTo", to, "sentAt", OffsetDateTime.now().toString());
    }
}
