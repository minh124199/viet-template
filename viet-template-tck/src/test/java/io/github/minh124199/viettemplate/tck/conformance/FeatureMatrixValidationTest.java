package io.github.minh124199.viettemplate.tck.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.tck.conformance.model.TckScenario;
import io.github.minh124199.viettemplate.tck.conformance.suite.TckSuiteRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TCK Feature Matrix & Coverage Validation")
public class FeatureMatrixValidationTest {

  private static final Set<String> REQUIRED_PREFIXES =
      Set.of(
          "LEX", "REF", "PROP", "IDX", "METH", "EXPR", "TRUTH", "SET", "IF", "FOREACH", "MACRO",
          "DYN", "STATE3", "STRICT", "SEC", "ERR", "UNICODE", "APP", "DIFF", "EXT");

  private static final Pattern ID_PATTERN =
      Pattern.compile(
          "^(LEX|REF|PROP|IDX|METH|EXPR|TRUTH|SET|IF|FOREACH|MACRO|DYN|STATE3|STRICT|SEC|ERR|UNICODE|APP|DIFF|EXT)-[0-9]{3}$");

  private static String matrixJson;
  private static List<MatrixFeatureRecord> features;

  record MatrixFeatureRecord(
      String id,
      String category,
      String name,
      String description,
      String status,
      String classification,
      List<String> requiredBackends,
      String specificationReference,
      String rationale) {}

  @BeforeAll
  static void loadMatrix() throws IOException {
    Path matrixPath = Path.of("config/tck/vtl-feature-matrix.json");
    if (!Files.exists(matrixPath)) {
      matrixPath = Path.of("../config/tck/vtl-feature-matrix.json");
    }
    assertThat(matrixPath).exists();

    matrixJson = Files.readString(matrixPath, StandardCharsets.UTF_8);
    features = parseMatrixFeatures(matrixJson);
  }

  @Test
  @DisplayName("Matrix JSON schema and structural validity")
  void testMatrixSchemaValidity() {
    assertThat(features).isNotEmpty();
    assertThat(features.size()).isGreaterThanOrEqualTo(80);

    Set<String> seenIds = new HashSet<>();
    Set<String> prefixes = new HashSet<>();

    for (MatrixFeatureRecord feat : features) {
      assertThat(feat.id()).isNotBlank();
      assertThat(ID_PATTERN.matcher(feat.id()).matches())
          .as("Feature ID %s must match durable pattern", feat.id())
          .isTrue();

      assertThat(seenIds.add(feat.id())).as("Feature ID %s must be unique", feat.id()).isTrue();

      String prefix = feat.id().split("-")[0];
      prefixes.add(prefix);

      assertThat(feat.category()).isNotBlank();
      assertThat(feat.name()).isNotBlank();
      assertThat(feat.description()).isNotBlank();
      assertThat(feat.specificationReference()).isNotBlank();

      assertThat(feat.status()).isIn("SUPPORTED", "INTENTIONAL_DIFFERENCE", "EXTENSION");
      assertThat(feat.classification())
          .isIn("EXACT_MATCH", "EXPECTED_DIFFERENCE", "VIET_EXTENSION");

      assertThat(feat.requiredBackends()).isNotEmpty();
      for (String b : feat.requiredBackends()) {
        assertThat(b).isIn("IR", "AOT_BYTECODE");
      }

      if ("INTENTIONAL_DIFFERENCE".equals(feat.status())) {
        assertThat(feat.id()).startsWith("DIFF-");
        assertThat(feat.classification()).isEqualTo("EXPECTED_DIFFERENCE");
        assertThat(feat.rationale()).isNotBlank();
      }

      if ("EXTENSION".equals(feat.status())) {
        assertThat(feat.id()).startsWith("EXT-");
        assertThat(feat.classification()).isEqualTo("VIET_EXTENSION");
        assertThat(feat.rationale()).isNotBlank();
      }
    }

    assertThat(prefixes)
        .as("All 20 required durable categories must be present in feature matrix")
        .containsAll(REQUIRED_PREFIXES);
  }

  @Test
  @DisplayName("100% of claimed features have registered test coverage in TckSuiteRegistry")
  void testFeatureCoverageCompleteness() {
    Set<String> registryFeatureIds = TckSuiteRegistry.coveredFeatureIds();
    Set<String> matrixFeatureIds = new HashSet<>();
    for (MatrixFeatureRecord feat : features) {
      matrixFeatureIds.add(feat.id());
    }

    List<String> missing = new ArrayList<>();
    for (String id : matrixFeatureIds) {
      if (!registryFeatureIds.contains(id)) {
        missing.add(id);
      }
    }

    assertThat(missing)
        .as("Every feature in the matrix must have test coverage in TckSuiteRegistry")
        .isEmpty();

    assertThat(registryFeatureIds).containsAll(matrixFeatureIds);
  }

  @Test
  @DisplayName("Registry scenario IDs are unique and reference valid features")
  void testRegistryScenarioIntegrity() {
    List<TckScenario> scenarios = TckSuiteRegistry.allScenarios();
    assertThat(scenarios).isNotEmpty();

    Set<String> scenarioIds = new HashSet<>();
    Set<String> matrixFeatureIds = new HashSet<>();
    for (MatrixFeatureRecord feat : features) {
      matrixFeatureIds.add(feat.id());
    }

    for (TckScenario scenario : scenarios) {
      assertThat(scenarioIds.add(scenario.id()))
          .as("Scenario ID %s must be unique", scenario.id())
          .isTrue();

      assertThat(matrixFeatureIds)
          .as("Scenario %s references unknown feature %s", scenario.id(), scenario.featureId())
          .contains(scenario.featureId());
    }
  }

  private static List<MatrixFeatureRecord> parseMatrixFeatures(String json) {
    List<MatrixFeatureRecord> list = new ArrayList<>();
    int featuresIndex = json.indexOf("\"features\"");
    if (featuresIndex == -1) {
      return list;
    }
    int arrayStart = json.indexOf('[', featuresIndex);
    if (arrayStart == -1) {
      return list;
    }

    int i = arrayStart + 1;
    int len = json.length();
    while (i < len) {
      char c = json.charAt(i);
      if (c == ']') {
        break;
      }
      if (c == '{') {
        int objStart = i;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        while (i < len) {
          char ch = json.charAt(i);
          if (escape) {
            escape = false;
          } else if (ch == '\\' && inString) {
            escape = true;
          } else if (ch == '"') {
            inString = !inString;
          } else if (!inString) {
            if (ch == '{') {
              depth++;
            } else if (ch == '}') {
              depth--;
              if (depth == 0) {
                String block = json.substring(objStart, i + 1);
                String id = extractStringField(block, "id");
                String category = extractStringField(block, "category");
                String name = extractStringField(block, "name");
                String description = extractStringField(block, "description");
                String status = extractStringField(block, "status");
                String classification = extractStringField(block, "classification");
                List<String> backends = extractStringListField(block, "requiredBackends");
                String specRef = extractStringField(block, "specificationReference");
                String rationale = extractStringField(block, "rationale");

                list.add(
                    new MatrixFeatureRecord(
                        id,
                        category,
                        name,
                        description,
                        status,
                        classification,
                        backends,
                        specRef,
                        rationale));
                break;
              }
            }
          }
          i++;
        }
      }
      i++;
    }
    return list;
  }

  private static String extractStringField(String jsonBlock, String fieldName) {
    Pattern p =
        Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
    Matcher m = p.matcher(jsonBlock);
    return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
  }

  private static List<String> extractStringListField(String jsonBlock, String fieldName) {
    Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[([^\\]]*)\\]");
    Matcher m = p.matcher(jsonBlock);
    if (!m.find()) {
      return List.of();
    }
    String inner = m.group(1);
    List<String> result = new ArrayList<>();
    Matcher itemMatcher = Pattern.compile("\"([^\"]*)\"").matcher(inner);
    while (itemMatcher.find()) {
      result.add(itemMatcher.group(1));
    }
    return result;
  }
}
