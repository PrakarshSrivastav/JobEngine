package io.github.prakarshsrivastav.jobengine.handler;

import io.github.prakarshsrivastav.jobengine.exception.UnknownJobTypeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(JobHandler::type, Function.identity()));
    }

    public boolean supports(String type) {
        return handlers.containsKey(type);
    }

    public JobHandler get(String type) {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            throw new UnknownJobTypeException(type, supportedTypes());
        }
        return handler;
    }

    public Set<String> supportedTypes() {
        return new TreeSet<>(handlers.keySet());
    }
}
