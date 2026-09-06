package io.github.minh124199.viettemplate.tck.velocity.result;

import io.github.minh124199.viettemplate.tck.velocity.engine.EngineIdentity;
import java.util.Collections;
import java.util.Map;

/**
 * Normalized result of executing a scenario against a single template engine.
 *
 * @param engine The engine that executed the scenario.
 * @param outcome Whether execution succeeded or failed.
 * @param output Rendered output (including partial output before failure).
 * @param exception Exception observation if execution failed; null if successful.
 * @param contextAfter Observable context mutations after execution.
 * @param observations Side-effect and counter observations.
 */
public record EngineResult(
    EngineIdentity engine,
    ExecutionOutcome outcome,
    String output,
    ExceptionObservation exception,
    Map<String, Object> contextAfter,
    Map<String, Object> observations) {

  public EngineResult {
    output = output != null ? output : "";
    contextAfter =
        contextAfter != null ? Collections.unmodifiableMap(contextAfter) : Collections.emptyMap();
    observations =
        observations != null ? Collections.unmodifiableMap(observations) : Collections.emptyMap();
  }

  public boolean isSuccess() {
    return outcome == ExecutionOutcome.SUCCESS;
  }

  public boolean isFailure() {
    return outcome == ExecutionOutcome.FAILURE;
  }
}
