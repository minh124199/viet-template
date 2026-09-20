package io.github.minh124199.viettemplate.tck.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.tck.conformance.model.TckResult;
import io.github.minh124199.viettemplate.tck.conformance.model.TckScenario;
import io.github.minh124199.viettemplate.tck.conformance.runner.TckRunner;
import io.github.minh124199.viettemplate.tck.conformance.suite.TckSuiteRegistry;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * M18.3 Backend Parity Verification.
 *
 * <p>Verifies that:
 *
 * <ol>
 *   <li>All 80 feature scenarios are registered in {@link TckSuiteRegistry}.
 *   <li>For dual-backend features (IR + AOT_BYTECODE), both backends produce bit-identical output
 *       for every conformance scenario.
 *   <li>For IR-only features (LEX-003, LEX-004, FOREACH-006, SEC-003, EXT-001), the scenario runs
 *       and passes on IR; AOT parity is intentionally out-of-scope and documented in the feature
 *       matrix.
 * </ol>
 *
 * <p>Uses only public APIs: {@link TckSuiteRegistry}, {@link TckRunner}, {@link ExecutionTier}.
 * Zero internal imports.
 */
@DisplayName("M18.3 Backend Parity Verification (IR vs AOT_BYTECODE)")
public class BackendParityTest {

  /**
   * IR-only features: classified in config/tck/vtl-feature-matrix.json as requiredBackends:[IR].
   */
  private static final Set<String> IR_ONLY_FEATURE_IDS =
      Set.of("LEX-003", "LEX-004", "FOREACH-006", "SEC-003", "EXT-001");

  @Test
  @DisplayName("TCK registry must contain exactly 80 feature scenarios")
  void registryContainsExactly80Scenarios() {
    List<TckScenario> scenarios = TckSuiteRegistry.allScenarios();
    assertThat(scenarios)
        .as(
            "TckSuiteRegistry must register exactly 80 scenarios covering all claimed features in"
                + " vtl-feature-matrix.json")
        .hasSize(80);
  }

  @Test
  @DisplayName("Dual-backend feature count must be 75 (80 total minus 5 IR-only)")
  void dualBackendFeatureCountIs75() {
    long dualBackendCount =
        TckSuiteRegistry.allScenarios().stream()
            .filter(s -> s.backends().contains(ExecutionTier.IR))
            .filter(s -> s.backends().contains(ExecutionTier.AOT_BYTECODE))
            .count();
    assertThat(dualBackendCount)
        .as(
            "Expected 75 dual-backend scenarios (80 total, 5 IR-only: LEX-003, LEX-004,"
                + " FOREACH-006, SEC-003, EXT-001)")
        .isEqualTo(75);
  }

  @Test
  @DisplayName("IR-only feature scenarios must declare IR backend only")
  void irOnlyFeaturesAreDeclaredCorrectly() {
    for (TckScenario scenario : TckSuiteRegistry.allScenarios()) {
      if (IR_ONLY_FEATURE_IDS.contains(scenario.featureId())) {
        assertThat(scenario.backends())
            .as(
                "Feature %s is classified IR-only in vtl-feature-matrix.json; scenario %s must"
                    + " declare only IR backend",
                scenario.featureId(), scenario.id())
            .containsExactly(ExecutionTier.IR);
      }
    }
  }

  @TestFactory
  @DisplayName("Dual-backend scenarios: IR and AOT_BYTECODE must produce bit-identical output")
  Stream<DynamicTest> dualBackendParityIsIdentical() {
    return TckSuiteRegistry.allScenarios().stream()
        .filter(s -> s.backends().contains(ExecutionTier.IR))
        .filter(s -> s.backends().contains(ExecutionTier.AOT_BYTECODE))
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    "[PARITY] [" + scenario.featureId() + "] " + scenario.id(),
                    () -> assertBitIdenticalParity(scenario)));
  }

  @TestFactory
  @DisplayName("IR-only scenarios: must pass on IR tier (AOT parity is intentionally out-of-scope)")
  Stream<DynamicTest> irOnlyScenariosPassOnIr() {
    return TckSuiteRegistry.allScenarios().stream()
        .filter(s -> IR_ONLY_FEATURE_IDS.contains(s.featureId()))
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    "[IR-ONLY] [" + scenario.featureId() + "] " + scenario.id(),
                    () -> assertIrOnlyPass(scenario)));
  }

  // --- private helpers ---

  private static void assertBitIdenticalParity(TckScenario scenario) {
    VtlProfile profile = scenario.profile() != null ? scenario.profile() : VtlProfile.VTL_CORE;

    TckResult irResult = TckRunner.executeScenario(scenario, ExecutionTier.IR, profile);
    if (!irResult.success()) {
      fail(
          String.format(
              "[PARITY] Scenario %s failed on IR tier: %s",
              scenario.id(), irResult.failureMessage()),
          irResult.error());
    }

    TckResult aotResult = TckRunner.executeScenario(scenario, ExecutionTier.AOT_BYTECODE, profile);
    if (!aotResult.success()) {
      fail(
          String.format(
              "[PARITY] Scenario %s failed on AOT_BYTECODE tier: %s",
              scenario.id(), aotResult.failureMessage()),
          aotResult.error());
    }

    if (!scenario.expectsError()) {
      assertThat(aotResult.actualOutput())
          .as(
              "[PARITY] AOT_BYTECODE output must be bit-identical to IR output for scenario %s"
                  + " (feature %s)",
              scenario.id(), scenario.featureId())
          .isEqualTo(irResult.actualOutput());
    }
  }

  private static void assertIrOnlyPass(TckScenario scenario) {
    VtlProfile profile = scenario.profile() != null ? scenario.profile() : VtlProfile.VTL_CORE;

    TckResult irResult = TckRunner.executeScenario(scenario, ExecutionTier.IR, profile);
    if (!irResult.success()) {
      fail(
          String.format(
              "[IR-ONLY] Scenario %s failed on IR tier: %s",
              scenario.id(), irResult.failureMessage()),
          irResult.error());
    }

    if (!scenario.expectsError()) {
      assertThat(irResult.actualOutput())
          .as(
              "[IR-ONLY] IR output must match expected for scenario %s (feature %s,"
                  + " AOT parity intentionally not required)",
              scenario.id(), scenario.featureId())
          .isEqualTo(scenario.expectedOutput());
    }
  }
}
