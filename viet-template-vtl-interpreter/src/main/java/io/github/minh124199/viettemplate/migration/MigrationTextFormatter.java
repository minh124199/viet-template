package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import java.util.Map;
import java.util.Objects;

/** Package-private formatter generating deterministic human-readable text migration reports. */
final class MigrationTextFormatter {

  private MigrationTextFormatter() {}

  public static String format(MigrationReport report) {
    Objects.requireNonNull(report, "report must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append("=== Viet Template Migration Analysis Report ===\n");
    sb.append("Source Engine: ")
        .append(report.sourceEngine())
        .append(" ")
        .append(report.sourceVersion())
        .append("\n");
    sb.append("Target Engine: ")
        .append(report.targetEngine())
        .append(" ")
        .append(report.targetVersion())
        .append("\n");
    sb.append("Readiness Status: ").append(report.readinessStatus().name()).append("\n");
    sb.append("Success: ").append(report.success()).append("\n");

    MigrationSummary summary = report.summary();
    sb.append("\n--- Migration Summary ---\n");
    sb.append("Total Templates: ").append(summary.totalTemplates()).append("\n");
    sb.append("Valid Templates: ").append(summary.validTemplates()).append("\n");
    sb.append("Compatible Templates: ").append(summary.compatibleTemplates()).append("\n");
    sb.append("Total Findings: ").append(summary.totalFindings()).append("\n");

    sb.append("Findings by Severity:\n");
    for (MigrationSeverity sev : MigrationSeverity.values()) {
      int count = summary.findingsBySeverity().getOrDefault(sev, 0);
      sb.append("  - ").append(sev.name()).append(": ").append(count).append("\n");
    }

    sb.append("Findings by Category:\n");
    for (Map.Entry<MigrationCategory, Integer> entry : summary.findingsByCategory().entrySet()) {
      if (entry.getValue() > 0) {
        sb.append("  - ")
            .append(entry.getKey().name())
            .append(": ")
            .append(entry.getValue())
            .append("\n");
      }
    }

    if (!report.validationDiagnostics().isEmpty()) {
      sb.append("\nValidation Diagnostics (")
          .append(report.validationDiagnostics().size())
          .append("):\n");
      for (TemplateAotDiagnostic diag : report.validationDiagnostics()) {
        sb.append("  - ").append(diag.formattedMessage()).append("\n");
      }
    }

    for (SingleTemplateMigration tmpl : report.templates()) {
      sb.append("\n=== Template Migration: ").append(tmpl.templateId().value()).append(" ===\n");
      sb.append("Relative Path: ").append(tmpl.relativePath()).append("\n");
      sb.append("Valid: ").append(tmpl.valid()).append("\n");
      sb.append("Compatible: ").append(tmpl.compatible()).append("\n");

      if (!tmpl.diagnostics().isEmpty()) {
        sb.append("Diagnostics (").append(tmpl.diagnostics().size()).append("):\n");
        for (TemplateAotDiagnostic diag : tmpl.diagnostics()) {
          sb.append("  - ").append(diag.formattedMessage()).append("\n");
        }
      }

      sb.append("Findings (").append(tmpl.findings().size()).append("):\n");
      for (MigrationFinding finding : tmpl.findings()) {
        formatFinding(finding, sb);
      }
    }

    return sb.toString();
  }

  private static void formatFinding(MigrationFinding f, StringBuilder sb) {
    sb.append("  [")
        .append(f.severity().name())
        .append("] [")
        .append(f.category().name())
        .append("] [")
        .append(f.ruleId())
        .append("] Line ")
        .append(f.sourceSpan().startLine())
        .append(":")
        .append(f.sourceSpan().startColumn())
        .append(" - ")
        .append(f.message())
        .append("\n");
    sb.append("    Construct: ").append(f.construct()).append("\n");
    sb.append("    Velocity Behavior: ").append(f.velocityBehavior()).append("\n");
    sb.append("    Viet Template Behavior: ").append(f.vietTemplateBehavior()).append("\n");
    sb.append("    Migration Action: ").append(f.migrationAction()).append("\n");
    f.relatedDiagnosticCode()
        .ifPresent(
            dc -> sb.append("    Diagnostic Code: ").append(dc.qualifiedCode()).append("\n"));
  }
}
