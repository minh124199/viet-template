package io.github.minh124199.viettemplate.tck.conformance.model;

import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;

/** Outcome of executing a conformance scenario against a specific backend tier. */
public record TckResult(
    String scenarioId,
    String featureId,
    ExecutionTier backend,
    boolean success,
    String actualOutput,
    Throwable error,
    String failureMessage,
    long durationNanos) {

  public static TckResult success(
      String scenarioId, String featureId, ExecutionTier backend, String output, long nanos) {
    return new TckResult(scenarioId, featureId, backend, true, output, null, null, nanos);
  }

  public static TckResult failure(
      String scenarioId,
      String featureId,
      ExecutionTier backend,
      String output,
      Throwable error,
      String failureMessage,
      long nanos) {
    return new TckResult(
        scenarioId, featureId, backend, false, output, error, failureMessage, nanos);
  }
}
