package io.github.minh124199.viettemplate.tck.velocity.report;

import io.github.minh124199.viettemplate.tck.velocity.result.CompatibilityClassification;
import io.github.minh124199.viettemplate.tck.velocity.result.ScenarioResult;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Generates deterministic human-readable Markdown and machine-readable JSON reports from
 * differential compatibility results.
 *
 * <p>Enforces strict structural and mathematical invariants on scenario results before generating
 * reports, ensuring reporting is always consistent with the underlying test execution.
 */
public final class ReportGenerator {

  private ReportGenerator() {}

  /**
   * Validates all reporting invariants on the provided results list.
   *
   * @param results list of scenario results to validate
   * @throws IllegalStateException if any invariant is violated
   */
  public static void validateInvariants(List<ScenarioResult> results) {
    if (results == null) {
      throw new IllegalArgumentException("Results list must not be null");
    }

    // 1. Scenario ID uniqueness and non-emptiness
    Set<String> seenIds = new HashSet<>();
    for (ScenarioResult r : results) {
      if (r == null
          || r.scenario() == null
          || r.scenario().id() == null
          || r.scenario().id().isBlank()) {
        throw new IllegalStateException("Encountered null or invalid ScenarioResult / scenario ID");
      }
      String id = r.scenario().id();
      if (!seenIds.add(id)) {
        throw new IllegalStateException("Duplicate scenario ID detected in results: '" + id + "'");
      }
    }

    // 2. Every executed scenario is represented exactly once
    if (seenIds.size() != results.size()) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Unique scenario ID count (%d) does not match total result count (%d)",
              seenIds.size(),
              results.size()));
    }

    // 3. Classification sum equals global total
    int exact = 0;
    int expectedDiff = 0;
    int unsupported = 0;
    int extension = 0;
    int bug = 0;
    for (ScenarioResult r : results) {
      switch (r.actualClassification()) {
        case EXACT_MATCH -> exact++;
        case EXPECTED_DIFFERENCE -> expectedDiff++;
        case UNSUPPORTED -> unsupported++;
        case VIET_EXTENSION -> extension++;
        case BUG -> bug++;
      }
    }
    int classificationSum = exact + expectedDiff + unsupported + extension + bug;
    if (classificationSum != results.size()) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of classification counts (%d) does not match total scenarios (%d)",
              classificationSum,
              results.size()));
    }

    // 4. Category breakdown and subtotal invariants
    Map<ScenarioCategory, List<ScenarioResult>> byCategory = new EnumMap<>(ScenarioCategory.class);
    for (ScenarioResult r : results) {
      byCategory.computeIfAbsent(r.scenario().category(), k -> new ArrayList<>()).add(r);
    }

    int catTotalSum = 0;
    int catExactSum = 0;
    int catExpDiffSum = 0;
    int catUnsupSum = 0;
    int catExtSum = 0;
    int catBugSum = 0;

    for (Map.Entry<ScenarioCategory, List<ScenarioResult>> entry : byCategory.entrySet()) {
      ScenarioCategory cat = entry.getKey();
      List<ScenarioResult> catResults = entry.getValue();
      if (catResults.isEmpty()) {
        throw new IllegalStateException("Category '" + cat + "' has empty result list");
      }
      int cTotal = catResults.size();
      catTotalSum += cTotal;

      int cExact = 0;
      int cExp = 0;
      int cUnsup = 0;
      int cExt = 0;
      int cBug = 0;
      for (ScenarioResult r : catResults) {
        switch (r.actualClassification()) {
          case EXACT_MATCH -> cExact++;
          case EXPECTED_DIFFERENCE -> cExp++;
          case UNSUPPORTED -> cUnsup++;
          case VIET_EXTENSION -> cExt++;
          case BUG -> cBug++;
        }
      }

      if (cExact + cExp + cUnsup + cExt + cBug != cTotal) {
        throw new IllegalStateException(
            "Category '" + cat + "' classification sum does not match category total");
      }

      catExactSum += cExact;
      catExpDiffSum += cExp;
      catUnsupSum += cUnsup;
      catExtSum += cExt;
      catBugSum += cBug;
    }

    // Invariant: sum of category totals == global scenario total
    if (catTotalSum != results.size()) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of category totals (%d) does not match global scenario total (%d)",
              catTotalSum,
              results.size()));
    }

    // Invariant: sum of category classification counts == global classification counts
    if (catExactSum != exact) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of category EXACT_MATCH (%d) does not match global EXACT_MATCH (%d)",
              catExactSum,
              exact));
    }
    if (catExpDiffSum != expectedDiff) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of category EXPECTED_DIFFERENCE (%d) does not match global EXPECTED_DIFFERENCE"
                  + " (%d)",
              catExpDiffSum,
              expectedDiff));
    }
    if (catUnsupSum != unsupported) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of category UNSUPPORTED (%d) does not match global UNSUPPORTED (%d)",
              catUnsupSum,
              unsupported));
    }
    if (catExtSum != extension) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of category VIET_EXTENSION (%d) does not match global VIET_EXTENSION (%d)",
              catExtSum,
              extension));
    }
    if (catBugSum != bug) {
      throw new IllegalStateException(
          String.format(
              Locale.ROOT,
              "Sum of category BUG (%d) does not match global BUG (%d)",
              catBugSum,
              bug));
    }

    // Invariant: every category represented by scenarios is emitted into the category scorecard
    Set<ScenarioCategory> representedCategories = new HashSet<>();
    for (ScenarioResult r : results) {
      representedCategories.add(r.scenario().category());
    }
    if (!byCategory.keySet().equals(representedCategories)) {
      throw new IllegalStateException(
          "Set of emitted scorecard categories does not match represented scenario categories");
    }
  }

  public static void writeReports(List<ScenarioResult> results, Path outputDir) throws IOException {
    validateInvariants(results);
    Files.createDirectories(outputDir);

    String markdown = generateMarkdown(results);
    String json = generateJson(results);

    Files.writeString(
        outputDir.resolve("velocity-compatibility-report.md"), markdown, StandardCharsets.UTF_8);
    Files.writeString(
        outputDir.resolve("velocity-compatibility-report.json"), json, StandardCharsets.UTF_8);
  }

  public static String generateMarkdown(List<ScenarioResult> results) {
    validateInvariants(results);

    List<ScenarioResult> sorted = new ArrayList<>(results);
    sorted.sort(Comparator.comparing(r -> r.scenario().id()));

    int total = sorted.size();
    Map<CompatibilityClassification, Integer> counts =
        new EnumMap<>(CompatibilityClassification.class);
    for (CompatibilityClassification c : CompatibilityClassification.values()) {
      counts.put(c, 0);
    }
    for (ScenarioResult r : sorted) {
      counts.put(r.actualClassification(), counts.get(r.actualClassification()) + 1);
    }

    int exact = counts.get(CompatibilityClassification.EXACT_MATCH);
    int expectedDiff = counts.get(CompatibilityClassification.EXPECTED_DIFFERENCE);
    int unsupported = counts.get(CompatibilityClassification.UNSUPPORTED);
    int extension = counts.get(CompatibilityClassification.VIET_EXTENSION);
    int bug = counts.get(CompatibilityClassification.BUG);

    double exactPercent = total > 0 ? (exact * 100.0 / total) : 0.0;
    double supportedPercent =
        total > 0 ? ((exact + expectedDiff + extension) * 100.0 / total) : 0.0;

    StringBuilder sb = new StringBuilder();
    sb.append("# Viet Template — Apache Velocity 2.4.1 Differential Compatibility Report\n\n");
    sb.append(
        "- **Reference Baseline**: Apache Velocity Engine 2.4.1"
            + " (`org.apache.velocity:velocity-engine-core:2.4.1`)\n");
    sb.append(
        "- **Evaluated Engine**: Viet Template Reference Interpreter"
            + " (`viet-template-vtl-interpreter`)\n\n");

    sb.append("## 1. Overall Compatibility Summary\n\n");
    sb.append("> **Summary Statement**: Apache Velocity 2.4.1 differential compatibility: ");
    sb.append(
        String.format(
            Locale.ROOT,
            "%d/%d scenarios exact (%.2f%%), %d documented intentional differences, %d Viet"
                + " Template extension, %d unsupported scenarios, and %d unclassified regressions"
                + " (%.2f%% accounted behavior coverage).\n\n",
            exact,
            total,
            exactPercent,
            expectedDiff,
            extension,
            unsupported,
            bug,
            supportedPercent));

    sb.append("| Metric | Count | Percentage | Description |\n");
    sb.append("| :--- | :--- | :--- | :--- |\n");
    sb.append(
        String.format(
            Locale.ROOT,
            "| **Total Scenarios** | %d | 100.00%% | Total differential scenarios evaluated |\n",
            total));
    sb.append(
        String.format(
            Locale.ROOT,
            "| **EXACT_MATCH** | %d | %.2f%% | Byte-for-byte output and outcome match |\n",
            exact,
            exactPercent));
    sb.append(
        String.format(
            Locale.ROOT,
            "| **EXPECTED_DIFFERENCE** | %d | %.2f%% | Documented intentional architectural"
                + " differences |\n",
            expectedDiff,
            total > 0 ? (expectedDiff * 100.0 / total) : 0.0));
    sb.append(
        String.format(
            Locale.ROOT,
            "| **VIET_EXTENSION** | %d | %.2f%% | Intentional Viet Template extensions |\n",
            extension,
            total > 0 ? (extension * 100.0 / total) : 0.0));
    sb.append(
        String.format(
            Locale.ROOT,
            "| **UNSUPPORTED** | %d | %.2f%% | Unsupported Apache Velocity features |\n",
            unsupported,
            total > 0 ? (unsupported * 100.0 / total) : 0.0));
    sb.append(
        String.format(
            Locale.ROOT,
            "| **BUG** | %d | %.2f%% | Unclassified differences or defects |\n",
            bug,
            total > 0 ? (bug * 100.0 / total) : 0.0));
    sb.append(
        String.format(
            Locale.ROOT,
            "| **Accounted Behavior Coverage** | %d | **%.2f%%** | Scenarios conforming to"
                + " specification |\n\n",
            (exact + expectedDiff + extension),
            supportedPercent));

    // Category breakdown
    sb.append("## 2. Compatibility by Category\n\n");
    sb.append(
        "| Category | Total | Exact | Expected Diff | Extension | Unsupported | Bug | Exact Parity"
            + " | Accounted |\n");
    sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");

    Map<ScenarioCategory, List<ScenarioResult>> byCategory =
        new TreeMap<>(Comparator.comparing(Enum::name));
    for (ScenarioResult r : sorted) {
      byCategory.computeIfAbsent(r.scenario().category(), k -> new ArrayList<>()).add(r);
    }

    for (Map.Entry<ScenarioCategory, List<ScenarioResult>> entry : byCategory.entrySet()) {
      ScenarioCategory cat = entry.getKey();
      List<ScenarioResult> catResults = entry.getValue();
      int cTotal = catResults.size();
      int cExact = 0;
      int cExp = 0;
      int cExt = 0;
      int cUnsup = 0;
      int cBug = 0;
      for (ScenarioResult r : catResults) {
        switch (r.actualClassification()) {
          case EXACT_MATCH -> cExact++;
          case EXPECTED_DIFFERENCE -> cExp++;
          case VIET_EXTENSION -> cExt++;
          case UNSUPPORTED -> cUnsup++;
          case BUG -> cBug++;
        }
      }
      double exactParity = cTotal > 0 ? (cExact * 100.0 / cTotal) : 0.0;
      double accounted = cTotal > 0 ? ((cExact + cExp + cExt) * 100.0 / cTotal) : 0.0;
      sb.append(
          String.format(
              Locale.ROOT,
              "| %s | %d | %d | %d | %d | %d | %d | %.2f%% | %.2f%% |\n",
              cat.name(),
              cTotal,
              cExact,
              cExp,
              cExt,
              cUnsup,
              cBug,
              exactParity,
              accounted));
    }

    sb.append(
        String.format(
            Locale.ROOT,
            "| **Total** | **%d** | **%d** | **%d** | **%d** | **%d** | **%d** | **%.2f%%** |"
                + " **%.2f%%** |\n\n",
            total,
            exact,
            expectedDiff,
            extension,
            unsupported,
            bug,
            exactPercent,
            supportedPercent));

    sb.append("> **Metric Definitions**:\n");
    sb.append(
        "> - **Exact Parity**: `(Exact / Total) * 100%` (Byte-for-byte output and outcome"
            + " match)\n");
    sb.append(
        "> - **Accounted**: `((Exact + Expected Diff + Extension) / Total) * 100%` (Behaviors"
            + " classified and accounted for, with zero unexpected regressions)\n\n");

    // Expected Differences
    List<ScenarioResult> diffs =
        sorted.stream()
            .filter(
                r -> r.actualClassification() == CompatibilityClassification.EXPECTED_DIFFERENCE)
            .toList();
    if (!diffs.isEmpty()) {
      sb.append("## 3. Expected Differences (Deliberate Architectural Decisions)\n\n");
      for (ScenarioResult r : diffs) {
        sb.append(String.format(Locale.ROOT, "### `%s`\n", r.scenario().id()));
        sb.append(String.format(Locale.ROOT, "- **Category**: `%s`\n", r.scenario().category()));
        sb.append(
            String.format(
                Locale.ROOT,
                "- **Rationale**: %s\n",
                r.rationale() != null ? r.rationale() : "None provided"));
        if (!r.differenceDetail().isEmpty()) {
          sb.append(
              String.format(Locale.ROOT, "- **Observed Divergence**: %s\n", r.differenceDetail()));
        }
        sb.append("\n");
      }
    }

    // Unsupported
    List<ScenarioResult> unsup =
        sorted.stream()
            .filter(r -> r.actualClassification() == CompatibilityClassification.UNSUPPORTED)
            .toList();
    if (!unsup.isEmpty()) {
      sb.append("## 4. Unsupported Velocity 2.4.1 Features\n\n");
      for (ScenarioResult r : unsup) {
        sb.append(
            String.format(
                Locale.ROOT,
                "- **`%s`** (%s): %s\n",
                r.scenario().id(),
                r.scenario().category(),
                r.differenceDetail()));
      }
      sb.append("\n");
    }

    // Extensions
    List<ScenarioResult> exts =
        sorted.stream()
            .filter(r -> r.actualClassification() == CompatibilityClassification.VIET_EXTENSION)
            .toList();
    if (!exts.isEmpty()) {
      sb.append("## 5. Viet Template Extensions\n\n");
      for (ScenarioResult r : exts) {
        sb.append(
            String.format(
                Locale.ROOT,
                "- **`%s`** (%s): %s\n",
                r.scenario().id(),
                r.scenario().category(),
                r.differenceDetail()));
      }
      sb.append("\n");
    }

    // Bugs
    List<ScenarioResult> bugs =
        sorted.stream()
            .filter(r -> r.actualClassification() == CompatibilityClassification.BUG)
            .toList();
    if (!bugs.isEmpty()) {
      sb.append("## 6. Compatibility Bugs (Mismatches)\n\n");
      for (ScenarioResult r : bugs) {
        sb.append(String.format(Locale.ROOT, "### `%s`\n", r.scenario().id()));
        sb.append(String.format(Locale.ROOT, "- **Category**: `%s`\n", r.scenario().category()));
        sb.append(String.format(Locale.ROOT, "- **Detail**: %s\n\n", r.differenceDetail()));
      }
    } else {
      sb.append("## 6. Compatibility Bugs\n\nNo unexpected compatibility bugs discovered.\n\n");
    }

    return sb.toString();
  }

  public static String generateJson(List<ScenarioResult> results) {
    validateInvariants(results);

    List<ScenarioResult> sorted = new ArrayList<>(results);
    sorted.sort(Comparator.comparing(r -> r.scenario().id()));

    int total = sorted.size();
    int exact = 0;
    int expectedDiff = 0;
    int unsupported = 0;
    int extension = 0;
    int bug = 0;
    for (ScenarioResult r : sorted) {
      switch (r.actualClassification()) {
        case EXACT_MATCH -> exact++;
        case EXPECTED_DIFFERENCE -> expectedDiff++;
        case UNSUPPORTED -> unsupported++;
        case VIET_EXTENSION -> extension++;
        case BUG -> bug++;
      }
    }

    double exactPercent = total > 0 ? (exact * 100.0 / total) : 0.0;
    double supportedPercent =
        total > 0 ? ((exact + expectedDiff + extension) * 100.0 / total) : 0.0;

    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"referenceEngine\": \"Apache Velocity 2.4.1\",\n");
    sb.append("  \"vietEngine\": \"Viet Template Reference Interpreter\",\n");
    sb.append("  \"summary\": {\n");
    sb.append(String.format(Locale.ROOT, "    \"total\": %d,\n", total));
    sb.append(String.format(Locale.ROOT, "    \"exactMatch\": %d,\n", exact));
    sb.append(String.format(Locale.ROOT, "    \"expectedDifference\": %d,\n", expectedDiff));
    sb.append(String.format(Locale.ROOT, "    \"vietExtension\": %d,\n", extension));
    sb.append(String.format(Locale.ROOT, "    \"unsupported\": %d,\n", unsupported));
    sb.append(String.format(Locale.ROOT, "    \"bug\": %d,\n", bug));
    sb.append(String.format(Locale.ROOT, "    \"exactParityPercent\": %.2f,\n", exactPercent));
    sb.append(
        String.format(Locale.ROOT, "    \"accountedCoveragePercent\": %.2f\n", supportedPercent));
    sb.append("  },\n");

    // Category breakdown in JSON
    Map<ScenarioCategory, List<ScenarioResult>> byCategory =
        new TreeMap<>(Comparator.comparing(Enum::name));
    for (ScenarioResult r : sorted) {
      byCategory.computeIfAbsent(r.scenario().category(), k -> new ArrayList<>()).add(r);
    }

    sb.append("  \"categories\": [\n");
    List<Map.Entry<ScenarioCategory, List<ScenarioResult>>> catEntries =
        new ArrayList<>(byCategory.entrySet());
    for (int i = 0; i < catEntries.size(); i++) {
      Map.Entry<ScenarioCategory, List<ScenarioResult>> entry = catEntries.get(i);
      ScenarioCategory cat = entry.getKey();
      List<ScenarioResult> catResults = entry.getValue();
      int cTotal = catResults.size();
      int cExact = 0;
      int cExp = 0;
      int cExt = 0;
      int cUnsup = 0;
      int cBug = 0;
      for (ScenarioResult r : catResults) {
        switch (r.actualClassification()) {
          case EXACT_MATCH -> cExact++;
          case EXPECTED_DIFFERENCE -> cExp++;
          case VIET_EXTENSION -> cExt++;
          case UNSUPPORTED -> cUnsup++;
          case BUG -> cBug++;
        }
      }
      double catExactParity = cTotal > 0 ? (cExact * 100.0 / cTotal) : 0.0;
      double catAccounted = cTotal > 0 ? ((cExact + cExp + cExt) * 100.0 / cTotal) : 0.0;

      sb.append("    {\n");
      sb.append(String.format(Locale.ROOT, "      \"category\": \"%s\",\n", cat.name()));
      sb.append(String.format(Locale.ROOT, "      \"total\": %d,\n", cTotal));
      sb.append(String.format(Locale.ROOT, "      \"exactMatch\": %d,\n", cExact));
      sb.append(String.format(Locale.ROOT, "      \"expectedDifference\": %d,\n", cExp));
      sb.append(String.format(Locale.ROOT, "      \"vietExtension\": %d,\n", cExt));
      sb.append(String.format(Locale.ROOT, "      \"unsupported\": %d,\n", cUnsup));
      sb.append(String.format(Locale.ROOT, "      \"bug\": %d,\n", cBug));
      sb.append(
          String.format(Locale.ROOT, "      \"exactParityPercent\": %.2f,\n", catExactParity));
      sb.append(
          String.format(Locale.ROOT, "      \"accountedCoveragePercent\": %.2f\n", catAccounted));
      sb.append("    }");
      if (i < catEntries.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ],\n");

    sb.append("  \"scenarios\": [\n");
    for (int i = 0; i < sorted.size(); i++) {
      ScenarioResult r = sorted.get(i);
      sb.append("    {\n");
      sb.append(
          String.format(Locale.ROOT, "      \"id\": \"%s\",\n", escapeJson(r.scenario().id())));
      sb.append(
          String.format(
              Locale.ROOT, "      \"category\": \"%s\",\n", r.scenario().category().name()));
      sb.append(
          String.format(
              Locale.ROOT, "      \"classification\": \"%s\",\n", r.actualClassification().name()));
      sb.append(
          String.format(
              Locale.ROOT,
              "      \"expectedClassification\": \"%s\",\n",
              r.expectedClassification().name()));
      sb.append(
          String.format(
              Locale.ROOT, "      \"velocityOutcome\": \"%s\",\n", r.velocityResult().outcome()));
      sb.append(
          String.format(Locale.ROOT, "      \"vietOutcome\": \"%s\",\n", r.vietResult().outcome()));
      sb.append(
          String.format(
              Locale.ROOT, "      \"conformsToExpectation\": %b", r.conformsToExpectation()));
      if (r.rationale() != null) {
        sb.append(",\n");
        sb.append(
            String.format(Locale.ROOT, "      \"rationale\": \"%s\"", escapeJson(r.rationale())));
      }
      if (!r.differenceDetail().isEmpty()) {
        sb.append(",\n");
        sb.append(
            String.format(
                Locale.ROOT,
                "      \"differenceDetail\": \"%s\"",
                escapeJson(r.differenceDetail())));
      }
      sb.append("\n    }");
      if (i < sorted.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}\n");

    return sb.toString();
  }

  private static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\b", "\\b")
        .replace("\f", "\\f")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
