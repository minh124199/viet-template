package io.github.minh124199.viettemplate.tck.conformance.model;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Machine-readable execution summary for TCK conformance suite runs. */
public record TckSummary(
    Instant timestamp,
    String engineName,
    String profile,
    int totalFeatures,
    int coveredFeatures,
    double coveragePercentage,
    int totalScenarios,
    int passedScenarios,
    int failedScenarios,
    boolean backendParityVerified,
    List<String> backends,
    List<TckResult> results) {

  public boolean allPassed() {
    return failedScenarios == 0 && totalScenarios > 0;
  }

  public String toJson() {
    StringBuilder sb = new StringBuilder(4096);
    sb.append("{\n");
    sb.append("  \"timestamp\": \"").append(escape(timestamp.toString())).append("\",\n");
    sb.append("  \"engine\": \"").append(escape(engineName)).append("\",\n");
    sb.append("  \"profile\": \"").append(escape(profile)).append("\",\n");
    sb.append("  \"totalFeatures\": ").append(totalFeatures).append(",\n");
    sb.append("  \"coveredFeatures\": ").append(coveredFeatures).append(",\n");
    sb.append(String.format(Locale.ROOT, "  \"coveragePercentage\": %.2f,\n", coveragePercentage));
    sb.append("  \"totalScenarios\": ").append(totalScenarios).append(",\n");
    sb.append("  \"passedScenarios\": ").append(passedScenarios).append(",\n");
    sb.append("  \"failedScenarios\": ").append(failedScenarios).append(",\n");
    sb.append("  \"backendParityVerified\": ").append(backendParityVerified).append(",\n");

    sb.append("  \"backends\": [");
    for (int i = 0; i < backends.size(); i++) {
      sb.append("\"").append(escape(backends.get(i))).append("\"");
      if (i + 1 < backends.size()) {
        sb.append(", ");
      }
    }
    sb.append("],\n");

    sb.append("  \"results\": [\n");
    for (int i = 0; i < results.size(); i++) {
      TckResult r = results.get(i);
      sb.append("    {\n");
      sb.append("      \"scenarioId\": \"").append(escape(r.scenarioId())).append("\",\n");
      sb.append("      \"featureId\": \"").append(escape(r.featureId())).append("\",\n");
      sb.append("      \"backend\": \"").append(escape(r.backend().name())).append("\",\n");
      sb.append("      \"success\": ").append(r.success()).append(",\n");
      sb.append("      \"durationMs\": ")
          .append(String.format(Locale.ROOT, "%.3f", r.durationNanos() / 1_000_000.0))
          .append(",\n");
      if (r.failureMessage() != null) {
        sb.append("      \"error\": \"").append(escape(r.failureMessage())).append("\"\n");
      } else {
        sb.append("      \"error\": null\n");
      }
      sb.append("    }");
      if (i + 1 < results.size()) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < ' ') {
            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
