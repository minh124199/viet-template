package io.github.minh124199.viettemplate.tck.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.tck.conformance.model.TckResult;
import io.github.minh124199.viettemplate.tck.conformance.model.TckScenario;
import io.github.minh124199.viettemplate.tck.conformance.runner.TckRunner;
import io.github.minh124199.viettemplate.tck.conformance.suite.TckSuiteRegistry;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gradle Independent TCK Consumer Verification")
public class GradleTckConsumerTest {

  @Test
  @DisplayName("Verify zero reactor source path imports (packaged jar consumption)")
  void verifyZeroReactorSourcePathImports() {
    CodeSource tckSource = TckRunner.class.getProtectionDomain().getCodeSource();
    assertThat(tckSource).isNotNull();
    String tckLocation = tckSource.getLocation().toString();
    assertThat(tckLocation)
        .as("viet-template-tck must be consumed as a packaged jar, not reactor classes")
        .endsWith(".jar")
        .doesNotContain("viet-template-tck/target/classes")
        .doesNotContain("viet-template-tck/build/classes");

    CodeSource interpreterSource = ExecutionTier.class.getProtectionDomain().getCodeSource();
    assertThat(interpreterSource).isNotNull();
    String interpreterLocation = interpreterSource.getLocation().toString();
    assertThat(interpreterLocation)
        .as("viet-template-vtl-interpreter must be consumed as a packaged jar")
        .endsWith(".jar")
        .doesNotContain("viet-template-vtl-interpreter/target/classes")
        .doesNotContain("viet-template-vtl-interpreter/build/classes");
  }

  @Test
  @DisplayName("Execute authoritative TCK suite and assert 100% pass rate & IR/AOT parity")
  void verifyTckSuiteConformanceAndParity() {
    List<TckScenario> scenarios = TckSuiteRegistry.allScenarios();
    assertThat(scenarios)
        .as("TCK Suite Registry must provide conformance scenarios to consumer")
        .isNotEmpty();

    int totalExecuted = 0;
    int totalPassed = 0;
    Map<String, Map<ExecutionTier, TckResult>> scenarioTierResults = new HashMap<>();

    for (TckScenario scenario : scenarios) {
      VtlProfile profile = scenario.profile() != null ? scenario.profile() : VtlProfile.VTL_CORE;

      for (ExecutionTier tier : scenario.backends()) {
        totalExecuted++;
        TckResult result = TckRunner.executeScenario(scenario, tier, profile);
        scenarioTierResults
            .computeIfAbsent(scenario.id(), k -> new HashMap<>())
            .put(tier, result);

        if (result.success()) {
          totalPassed++;
        } else {
          fail(
              String.format(
                  "TCK scenario %s failed on tier %s: %s",
                  scenario.id(), tier, result.failureMessage()),
              result.error());
        }
      }
    }

    assertThat(totalExecuted).isGreaterThan(0);
    assertThat(totalPassed).isEqualTo(totalExecuted);

    // Parity verification between IR and AOT_BYTECODE
    for (TckScenario scenario : scenarios) {
      Map<ExecutionTier, TckResult> tierMap = scenarioTierResults.get(scenario.id());
      if (tierMap != null
          && tierMap.containsKey(ExecutionTier.IR)
          && tierMap.containsKey(ExecutionTier.AOT_BYTECODE)) {
        TckResult irResult = tierMap.get(ExecutionTier.IR);
        TckResult aotResult = tierMap.get(ExecutionTier.AOT_BYTECODE);

        assertThat(aotResult.success())
            .as("Parity success mismatch for %s", scenario.id())
            .isEqualTo(irResult.success());

        if (irResult.success() && !scenario.expectsError()) {
          assertThat(aotResult.actualOutput())
              .as("Parity output mismatch for %s", scenario.id())
              .isEqualTo(irResult.actualOutput());
        }
      }
    }
  }

  @Test
  @DisplayName("Execute TckRunner CLI entrypoint and verify summary JSON creation")
  void verifyTckRunnerCliExecution() {
    Path reportPath = Path.of("build/tck-consumer-report.json");
    TckRunner.main(
        new String[] {
          "--backend", "ALL",
          "--profile", "VTL_CORE",
          "--json-output", reportPath.toString()
        });

    assertThat(Files.exists(reportPath))
        .as("TckRunner must generate JSON report at designated output path")
        .isTrue();
  }
}
