package io.github.minh124199.viettemplate.tck.velocity.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.tck.velocity.corpus.CompatibilityCorpus;
import io.github.minh124199.viettemplate.tck.velocity.engine.EngineIdentity;
import io.github.minh124199.viettemplate.tck.velocity.registry.ExpectationRegistry;
import io.github.minh124199.viettemplate.tck.velocity.registry.StandardExpectations;
import io.github.minh124199.viettemplate.tck.velocity.result.CompatibilityClassification;
import io.github.minh124199.viettemplate.tck.velocity.result.EngineResult;
import io.github.minh124199.viettemplate.tck.velocity.result.ExecutionOutcome;
import io.github.minh124199.viettemplate.tck.velocity.result.ScenarioResult;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportGeneratorInvariantsTest {

  @Test
  @DisplayName("ReportGenerator validates canonical corpus results without invariant violations")
  void validatesCanonicalCorpusInvariants() {
    List<CompatibilityScenario> scenarios = CompatibilityCorpus.allScenarios();
    assertThat(scenarios).as("Corpus must contain 301 scenarios").hasSize(301);

    ExpectationRegistry expectations = StandardExpectations.create();
    List<ScenarioResult> results = new ArrayList<>();

    for (CompatibilityScenario s : scenarios) {
      CompatibilityClassification expectedCls = expectations.expectedClassification(s.id());
      String rationale = expectations.rationale(s.id());
      EngineResult dummyVel =
          new EngineResult(
              EngineIdentity.APACHE_VELOCITY_2_4_1,
              ExecutionOutcome.SUCCESS,
              "out",
              null,
              Map.of(),
              Map.of());
      EngineResult dummyViet =
          new EngineResult(
              EngineIdentity.VIET_TEMPLATE_REFERENCE,
              ExecutionOutcome.SUCCESS,
              "out",
              null,
              Map.of(),
              Map.of());

      results.add(
          new ScenarioResult(s, dummyVel, dummyViet, expectedCls, expectedCls, rationale, ""));
    }

    // Must validate successfully without throwing
    ReportGenerator.validateInvariants(results);

    // Verify markdown generation
    String md = ReportGenerator.generateMarkdown(results);
    assertThat(md)
        .contains("| **Total Scenarios** | 301 | 100.00% |")
        .contains("| **EXACT_MATCH** | 295 | 98.01% |")
        .contains("| **EXPECTED_DIFFERENCE** | 5 | 1.66% |")
        .contains("| **VIET_EXTENSION** | 1 | 0.33% |")
        .contains("| **UNSUPPORTED** | 0 | 0.00% |")
        .contains("| **BUG** | 0 | 0.00% |")
        .contains("| **Accounted Behavior Coverage** | 301 | **100.00%** |")
        .contains(
            "| **Total** | **301** | **295** | **5** | **1** | **0** | **0** | **98.01%** |"
                + " **100.00%** |");

    // Verify all 20 categories appear in markdown
    for (ScenarioCategory cat : ScenarioCategory.values()) {
      assertThat(md).contains("| " + cat.name() + " |");
    }

    // Verify JSON generation
    String json = ReportGenerator.generateJson(results);
    assertThat(json)
        .contains("\"total\": 301")
        .contains("\"exactMatch\": 295")
        .contains("\"expectedDifference\": 5")
        .contains("\"vietExtension\": 1")
        .contains("\"unsupported\": 0")
        .contains("\"bug\": 0")
        .contains("\"exactParityPercent\": 98.01")
        .contains("\"accountedCoveragePercent\": 100.00");
  }

  @Test
  @DisplayName("ReportGenerator rejects duplicate scenario IDs")
  void rejectsDuplicateScenarioId() {
    CompatibilityScenario s1 =
        CompatibilityScenario.simple("dup.scenario", ScenarioCategory.REFERENCE, "$x");
    CompatibilityScenario s2 =
        CompatibilityScenario.simple("dup.scenario", ScenarioCategory.EXPRESSION, "$y");

    EngineResult res =
        new EngineResult(
            EngineIdentity.APACHE_VELOCITY_2_4_1,
            ExecutionOutcome.SUCCESS,
            "out",
            null,
            Map.of(),
            Map.of());
    ScenarioResult r1 =
        new ScenarioResult(
            s1,
            res,
            res,
            CompatibilityClassification.EXACT_MATCH,
            CompatibilityClassification.EXACT_MATCH,
            null,
            "");
    ScenarioResult r2 =
        new ScenarioResult(
            s2,
            res,
            res,
            CompatibilityClassification.EXACT_MATCH,
            CompatibilityClassification.EXACT_MATCH,
            null,
            "");

    List<ScenarioResult> list = List.of(r1, r2);

    assertThatThrownBy(() -> ReportGenerator.validateInvariants(list))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate scenario ID");
  }

  @Test
  @DisplayName("ReportGenerator rejects null or blank scenario ID")
  void rejectsNullOrBlankScenarioId() {
    CompatibilityScenario s = CompatibilityScenario.simple("", ScenarioCategory.REFERENCE, "$x");

    EngineResult res =
        new EngineResult(
            EngineIdentity.APACHE_VELOCITY_2_4_1,
            ExecutionOutcome.SUCCESS,
            "out",
            null,
            Map.of(),
            Map.of());
    ScenarioResult r =
        new ScenarioResult(
            s,
            res,
            res,
            CompatibilityClassification.EXACT_MATCH,
            CompatibilityClassification.EXACT_MATCH,
            null,
            "");

    assertThatThrownBy(() -> ReportGenerator.validateInvariants(List.of(r)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("null or invalid");
  }

  @Test
  @DisplayName("ReportGenerator rejects null result list")
  void rejectsNullResultsList() {
    assertThatThrownBy(() -> ReportGenerator.validateInvariants(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
