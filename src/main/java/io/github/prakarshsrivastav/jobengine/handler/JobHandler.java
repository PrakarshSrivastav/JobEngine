package io.github.prakarshsrivastav.jobengine.handler;

import java.util.Map;

/**
 * Executes one job type. Implementations are Spring beans and are picked up automatically.
 * Throwing marks the attempt as failed and hands the job to the retry policy.
 */
public interface JobHandler {

    String type();

    Map<String, Object> handle(Map<String, Object> payload) throws Exception;
}
