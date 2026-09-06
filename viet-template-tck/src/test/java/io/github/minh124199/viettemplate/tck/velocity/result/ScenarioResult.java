package io.github.minh124199.viettemplate.tck.velocity.result;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;

/**
 * Result of executing and comparing a scenario across both engines.
 *
 * @param scenario The evaluated scenario.
 * @param velocityResult Execution result from Apache Velocity 2.4.1.
 * @param vietResult Execution result from Viet Template reference interpreter.
 * @param actualClassification Computed actual classification.
 * @param expectedClassification Expected classification from registry (defaults to EXACT_MATCH).
 * @param rationale Architectural rationale if expected to differ.
 * @param differenceDetail Descriptive explanation of any observed divergence.
 */
public record ScenarioResult(
    CompatibilityScenario scenario,
    EngineResult velocityResult,
    EngineResult vietResult,
    CompatibilityClassification actualClassification,
    CompatibilityClassification expectedClassification,
    String rationale,
    String differenceDetail) {

  public boolean isMatch() {
    return actualClassification == CompatibilityClassification.EXACT_MATCH;
  }

  public boolean conformsToExpectation() {
    return actualClassification == expectedClassification;
  }

  public boolean isUnexpectedMismatch() {
    return actualClassification != expectedClassification;
  }
}
