package io.github.minh124199.viettemplate.tck.velocity;

import static org.junit.jupiter.api.Assertions.fail;

import io.github.minh124199.viettemplate.tck.velocity.compare.DifferentialComparator;
import io.github.minh124199.viettemplate.tck.velocity.corpus.CompatibilityCorpus;
import io.github.minh124199.viettemplate.tck.velocity.engine.EngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.engine.Velocity241EngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.engine.VietReferenceEngineAdapter;
import io.github.minh124199.viettemplate.tck.velocity.registry.ExpectationRegistry;
import io.github.minh124199.viettemplate.tck.velocity.registry.StandardExpectations;
import io.github.minh124199.viettemplate.tck.velocity.report.ReportGenerator;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.result.ScenarioResult;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Authoritative Milestone M4 Differential Compatibility TCK. Executes scenarios side-by-side
 * against Apache Velocity Engine 2.4.1 and the Viet Template Reference Interpreter.
 */
public class VelocityDifferentialTckTest {

  private static EngineAdapter velocityEngine;
  private static EngineAdapter vietEngine;
  private static ExpectationRegistry expectations;
  private static DifferentialComparator comparator;
  private static final List<ScenarioResult> testResults = new ArrayList<>();

  @BeforeAll
  static void setUp() {
    velocityEngine = new Velocity241EngineAdapter();
    vietEngine = new VietReferenceEngineAdapter();
    expectations = StandardExpectations.create();
    comparator = new DifferentialComparator(expectations);

    // Validate registry against corpus
    var allIds =
        CompatibilityCorpus.allScenarios().stream()
            .map(CompatibilityScenario::id)
            .collect(Collectors.toSet());
    expectations.validateAgainstScenarioIds(allIds);
  }

  @TestFactory
  @DisplayName("Apache Velocity 2.4.1 Differential Compatibility TCK")
  List<DynamicTest> executeDifferentialScenarios() {
    String scenarioFilter = System.getProperty("viet.tck.scenario");
    String categoryFilter = System.getProperty("viet.tck.category");
    String tagFilter = System.getProperty("viet.tck.tag");
    String mode = System.getProperty("viet.tck.mode", "STRICT");

    List<CompatibilityScenario> scenarios = CompatibilityCorpus.allScenarios();
    if (scenarioFilter != null && !scenarioFilter.isBlank()) {
      scenarios = scenarios.stream().filter(s -> s.id().contains(scenarioFilter)).toList();
    }
    if (categoryFilter != null && !categoryFilter.isBlank()) {
      scenarios =
          scenarios.stream()
              .filter(s -> s.category().name().equalsIgnoreCase(categoryFilter))
              .toList();
    }
    if (tagFilter != null && !tagFilter.isBlank()) {
      scenarios = scenarios.stream().filter(s -> s.tags().contains(tagFilter)).toList();
    }

    return scenarios.stream()
        .map(
            scenario ->
                DynamicTest.dynamicTest(
                    scenario.id() + " [" + scenario.category() + "]",
                    () -> runScenario(scenario, "STRICT".equalsIgnoreCase(mode))))
        .toList();
  }

  private void runScenario(CompatibilityScenario scenario, boolean isStrict) {
    // 1. Create fresh independent contexts
    Map<String, Object> velocityContext = scenario.contextFactory().create();
    Map<String, Object> vietContext = scenario.contextFactory().create();

    // 2. Execute on Apache Velocity 2.4.1
    EngineResult velResult =
        velocityEngine.execute(
            scenario, velocityContext, scenario.configuration(), scenario.resources());

    // 3. Execute on Viet Template Reference Interpreter
    EngineResult vietResult =
        vietEngine.execute(scenario, vietContext, scenario.configuration(), scenario.resources());

    // 4. Compare results
    ScenarioResult scenarioResult = comparator.compare(scenario, velResult, vietResult);
    synchronized (testResults) {
      testResults.add(scenarioResult);
    }

    // 5. Quality Gate in STRICT mode
    if (isStrict) {
      if (scenarioResult.isUnexpectedMismatch()) {
        String rerunCmd =
            String.format(
                "%nRe-run single scenario using:%n"
                    + "  ./gradlew :viet-template-tck:test -Dviet.tck.scenario=%s%n"
                    + "  ./mvnw test -pl viet-template-tck -Dviet.tck.scenario=%s%n",
                scenario.id(), scenario.id());

        fail(
            String.format(
                "Scenario '%s' failed differential assertion!%n"
                    + "Actual Classification:   %s%n"
                    + "Expected Classification: %s%n"
                    + "Difference Details:      %s%n"
                    + "Viet Exception:          %s%n"
                    + "%s",
                scenario.id(),
                scenarioResult.actualClassification(),
                scenarioResult.expectedClassification(),
                scenarioResult.differenceDetail(),
                vietResult.exception() != null
                    ? vietResult.exception().rawExceptionClass()
                        + ": "
                        + vietResult.exception().message()
                    : "none",
                rerunCmd));
      }
    }
  }

  @AfterAll
  static void generateReports() throws IOException {
    if (testResults.isEmpty()) {
      return;
    }

    // Write reports to build/reports/velocity-compat and target/reports/velocity-compat
    Path gradleReportDir = Paths.get("build", "reports", "velocity-compat");
    Path mavenReportDir = Paths.get("target", "reports", "velocity-compat");

    ReportGenerator.writeReports(testResults, gradleReportDir);
    ReportGenerator.writeReports(testResults, mavenReportDir);

    System.out.println("=== DIFFERENTIAL COMPATIBILITY REPORT GENERATED ===");
    System.out.println("Report files written to:");
    System.out.println("  " + gradleReportDir.toAbsolutePath());
    System.out.println("  " + mavenReportDir.toAbsolutePath());
  }
}
