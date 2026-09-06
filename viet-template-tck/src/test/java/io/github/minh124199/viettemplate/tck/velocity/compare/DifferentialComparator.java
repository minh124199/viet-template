package io.github.minh124199.viettemplate.tck.velocity.compare;

import io.github.minh124199.viettemplate.tck.velocity.registry.ExpectationRegistry;
import io.github.minh124199.viettemplate.tck.velocity.result.CompatibilityClassification;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.result.ScenarioResult;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable differential comparator that compares observable behavior between Apache Velocity 2.4.1
 * and Viet Template reference interpreter.
 */
public final class DifferentialComparator {

  private final ExpectationRegistry registry;

  public DifferentialComparator(ExpectationRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  public ScenarioResult compare(
      CompatibilityScenario scenario, EngineResult velocityResult, EngineResult vietResult) {

    List<String> divergences = new ArrayList<>();

    // 1. Compare Execution Outcomes (Success vs Failure)
    if (velocityResult.outcome() != vietResult.outcome()) {
      divergences.add(
          String.format(
              "Outcome mismatch: Velocity=%s, Viet=%s",
              velocityResult.outcome(), vietResult.outcome()));
    }

    // 2. Compare Rendered Output (Exact Character-for-Character Match)
    if (!Objects.equals(velocityResult.output(), vietResult.output())) {
      divergences.add(
          String.format(
              "Output mismatch:%nVelocity Output: [%s]%nViet Output:     [%s]",
              escapeString(velocityResult.output()), escapeString(vietResult.output())));
    }

    // 3. Compare Exceptions if failed
    if (velocityResult.isFailure() && vietResult.isFailure()) {
      var velEx = velocityResult.exception();
      var vietEx = vietResult.exception();

      if (velEx != null && vietEx != null) {
        if (velEx.category() != vietEx.category()) {
          divergences.add(
              String.format(
                  "Exception category mismatch: Velocity=%s (%s: %s), Viet=%s (%s: %s)",
                  velEx.category(),
                  velEx.rawExceptionClass(),
                  velEx.message(),
                  vietEx.category(),
                  vietEx.rawExceptionClass(),
                  vietEx.message()));
        }
      }
    }

    // 4. Compare Observable Context Mutations
    for (String key : scenario.observableContextKeys()) {
      Object velVal = velocityResult.contextAfter().get(key);
      Object vietVal = vietResult.contextAfter().get(key);
      if (!Objects.equals(velVal, vietVal)) {
        divergences.add(
            String.format(
                "Context mutation mismatch for key '%s': Velocity=[%s], Viet=[%s]",
                key, velVal, vietVal));
      }
    }

    // 5. Compare Side-Effect Observations (e.g. Probe counters)
    for (Map.Entry<String, Object> entry : velocityResult.observations().entrySet()) {
      String obsKey = entry.getKey();
      Object velObs = entry.getValue();
      Object vietObs = vietResult.observations().get(obsKey);
      if (!Objects.equals(velObs, vietObs)) {
        divergences.add(
            String.format(
                "Observation mismatch for '%s': Velocity=[%s], Viet=[%s]",
                obsKey, velObs, vietObs));
      }
    }

    boolean isExactMatch = divergences.isEmpty();
    CompatibilityClassification expected = registry.expectedClassification(scenario.id());
    String rationale = registry.rationale(scenario.id());

    CompatibilityClassification actual;
    String detail = "";

    if (isExactMatch) {
      actual = CompatibilityClassification.EXACT_MATCH;
      if (expected != CompatibilityClassification.EXACT_MATCH) {
        detail =
            String.format(
                "STALE EXPECTATION: Scenario '%s' was expected to be %s, but now matches Velocity"
                    + " 2.4.1 exactly!",
                scenario.id(), expected);
      }
    } else {
      detail = String.join("; ", divergences);
      if (expected != CompatibilityClassification.EXACT_MATCH) {
        // Known registered difference or bug
        actual = expected;
      } else {
        // Unregistered mismatch is a BUG
        actual = CompatibilityClassification.BUG;
      }
    }

    return new ScenarioResult(
        scenario, velocityResult, vietResult, actual, expected, rationale, detail);
  }

  private static String escapeString(String s) {
    if (s == null) return "null";
    return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }
}
