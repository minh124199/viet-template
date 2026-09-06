package io.github.minh124199.viettemplate.tck.velocity.registry;

import io.github.minh124199.viettemplate.tck.velocity.result.CompatibilityClassification;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry holding expected non-EXACT_MATCH classifications with rationale and issue tracking. If a
 * scenario is not registered here, its expected classification is {@link
 * CompatibilityClassification#EXACT_MATCH}.
 */
public final class ExpectationRegistry {

  public record ExpectationEntry(
      String scenarioId,
      CompatibilityClassification classification,
      String rationale,
      String since,
      String trackingIssue) {

    public ExpectationEntry {
      Objects.requireNonNull(scenarioId, "scenarioId must not be null");
      Objects.requireNonNull(classification, "classification must not be null");
      if (classification != CompatibilityClassification.EXACT_MATCH) {
        if (rationale == null || rationale.isBlank()) {
          throw new IllegalArgumentException(
              "Rationale must be provided for non-exact classification: " + scenarioId);
        }
      }
    }
  }

  private final Map<String, ExpectationEntry> entries;

  public ExpectationRegistry(Map<String, ExpectationEntry> entries) {
    this.entries = Collections.unmodifiableMap(new HashMap<>(entries));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ExpectationRegistry empty() {
    return new ExpectationRegistry(Collections.emptyMap());
  }

  public CompatibilityClassification expectedClassification(String scenarioId) {
    ExpectationEntry entry = entries.get(scenarioId);
    return entry != null ? entry.classification() : CompatibilityClassification.EXACT_MATCH;
  }

  public String rationale(String scenarioId) {
    ExpectationEntry entry = entries.get(scenarioId);
    return entry != null ? entry.rationale() : null;
  }

  public ExpectationEntry get(String scenarioId) {
    return entries.get(scenarioId);
  }

  public Map<String, ExpectationEntry> entries() {
    return entries;
  }

  public void validateAgainstScenarioIds(Set<String> validScenarioIds) {
    for (String id : entries.keySet()) {
      if (!validScenarioIds.contains(id)) {
        throw new IllegalStateException(
            "Expectation registry references non-existent scenario: '" + id + "'");
      }
    }
  }

  public static final class Builder {
    private final Map<String, ExpectationEntry> map = new HashMap<>();

    public Builder expect(
        String scenarioId, CompatibilityClassification classification, String rationale) {
      return expect(scenarioId, classification, rationale, "0.1.0", null);
    }

    public Builder expect(
        String scenarioId,
        CompatibilityClassification classification,
        String rationale,
        String since,
        String trackingIssue) {
      if (map.containsKey(scenarioId)) {
        throw new IllegalStateException(
            "Duplicate expectation registered for scenario: " + scenarioId);
      }
      map.put(
          scenarioId,
          new ExpectationEntry(scenarioId, classification, rationale, since, trackingIssue));
      return this;
    }

    public ExpectationRegistry build() {
      return new ExpectationRegistry(map);
    }
  }
}
