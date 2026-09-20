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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@DisplayName("TCK Conformance & Cross-Tier Parity Test")
public class TckConformanceTest {

  @TestFactory
  @DisplayName("Execute all registered TCK scenarios on IR and AOT_BYTECODE tiers")
  Stream<DynamicTest> testAllRegisteredScenarios() {
    List<TckScenario> scenarios = TckSuiteRegistry.allScenarios();

    return scenarios.stream()
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    "[" + scenario.featureId() + "] " + scenario.id(),
                    () -> runConformanceScenario(scenario)));
  }

  private void runConformanceScenario(TckScenario scenario) {
    VtlProfile profile = scenario.profile() != null ? scenario.profile() : VtlProfile.VTL_CORE;

    TckResult irResult = null;
    if (scenario.backends().contains(ExecutionTier.IR)) {
      irResult = TckRunner.executeScenario(scenario, ExecutionTier.IR, profile);
      if (!irResult.success()) {
        fail(
            String.format(
                "Scenario %s failed on IR tier: %s", scenario.id(), irResult.failureMessage()),
            irResult.error());
      }
    }

    TckResult aotResult = null;
    if (scenario.backends().contains(ExecutionTier.AOT_BYTECODE)) {
      aotResult = TckRunner.executeScenario(scenario, ExecutionTier.AOT_BYTECODE, profile);
      if (!aotResult.success()) {
        fail(
            String.format(
                "Scenario %s failed on AOT_BYTECODE tier: %s",
                scenario.id(), aotResult.failureMessage()),
            aotResult.error());
      }
    }

    if (irResult != null && aotResult != null) {
      if (scenario.expectsError()) {
        assertThat(irResult.success()).as("IR error success expectation").isTrue();
        assertThat(aotResult.success()).as("AOT error success expectation").isTrue();
      } else {
        assertThat(aotResult.actualOutput())
            .as("AOT_BYTECODE output must match IR output for %s", scenario.id())
            .isEqualTo(irResult.actualOutput());

        assertThat(irResult.actualOutput())
            .as("IR output must match scenario expected output for %s", scenario.id())
            .isEqualTo(scenario.expectedOutput());
      }
    } else if (irResult != null && !scenario.expectsError()) {
      assertThat(irResult.actualOutput())
          .as("IR output must match scenario expected output for %s", scenario.id())
          .isEqualTo(scenario.expectedOutput());
    } else if (aotResult != null && !scenario.expectsError()) {
      assertThat(aotResult.actualOutput())
          .as("AOT output must match scenario expected output for %s", scenario.id())
          .isEqualTo(scenario.expectedOutput());
    }
  }
}
