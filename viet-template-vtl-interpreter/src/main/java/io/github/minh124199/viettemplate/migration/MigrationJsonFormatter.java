package io.github.minh124199.viettemplate.migration;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/**
 * Package-private formatter generating strictly deterministic JSON representation (formatVersion =
 * 1) for migration analysis reports with zero external dependencies.
 */
final class MigrationJsonFormatter {

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private MigrationJsonFormatter() {}

  public static String format(MigrationReport report) {
    Objects.requireNonNull(report, "report must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"formatVersion\": 1,\n");
    sb.append("  \"sourceEngine\": \"").append(escapeJson(report.sourceEngine())).append("\",\n");
    sb.append("  \"sourceVersion\": \"").append(escapeJson(report.sourceVersion())).append("\",\n");
    sb.append("  \"targetEngine\": \"").append(escapeJson(report.targetEngine())).append("\",\n");
    sb.append("  \"targetVersion\": \"").append(escapeJson(report.targetVersion())).append("\",\n");
    sb.append("  \"readinessStatus\": \"")
        .append(escapeJson(report.readinessStatus().name()))
        .append("\",\n");
    sb.append("  \"success\": ").append(report.success()).append(",\n");

    sb.append("  \"summary\": ");
    formatSummary(report.summary(), sb, "  ");
    sb.append(",\n");

    sb.append("  \"validationDiagnostics\": ");
    formatDiagnostics(report.validationDiagnostics(), sb, "  ");
    sb.append(",\n");

    sb.append("  \"templates\": ");
    formatTemplates(report.templates(), sb, "  ");
    sb.append("\n");

    sb.append("}\n");
    return sb.toString();
  }

  private static void formatSummary(MigrationSummary summary, StringBuilder sb, String indent) {
    String inner = indent + "  ";
    sb.append("{\n");
    sb.append(inner).append("\"totalTemplates\": ").append(summary.totalTemplates()).append(",\n");
    sb.append(inner).append("\"validTemplates\": ").append(summary.validTemplates()).append(",\n");
    sb.append(inner)
        .append("\"compatibleTemplates\": ")
        .append(summary.compatibleTemplates())
        .append(",\n");
    sb.append(inner).append("\"totalFindings\": ").append(summary.totalFindings()).append(",\n");
    sb.append(inner)
        .append("\"readinessStatus\": \"")
        .append(escapeJson(summary.readinessStatus().name()))
        .append("\",\n");

    sb.append(inner).append("\"findingsBySeverity\": {\n");
    MigrationSeverity[] sevs = MigrationSeverity.values();
    for (int i = 0; i < sevs.length; i++) {
      MigrationSeverity s = sevs[i];
      int count = summary.findingsBySeverity().getOrDefault(s, 0);
      sb.append(inner)
          .append("  \"")
          .append(s.name())
          .append("\": ")
          .append(count)
          .append(i < sevs.length - 1 ? "," : "")
          .append("\n");
    }
    sb.append(inner).append("},\n");

    sb.append(inner).append("\"findingsByCategory\": {\n");
    MigrationCategory[] cats = MigrationCategory.values();
    for (int i = 0; i < cats.length; i++) {
      MigrationCategory c = cats[i];
      int count = summary.findingsByCategory().getOrDefault(c, 0);
      sb.append(inner)
          .append("  \"")
          .append(c.name())
          .append("\": ")
          .append(count)
          .append(i < cats.length - 1 ? "," : "")
          .append("\n");
    }
    sb.append(inner).append("}\n");

    sb.append(indent).append("}");
  }

  private static void formatDiagnostics(
      List<TemplateAotDiagnostic> diagnostics, StringBuilder sb, String indent) {
    if (diagnostics.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < diagnostics.size(); i++) {
      TemplateAotDiagnostic d = diagnostics.get(i);
      String inner = indent + "  ";
      sb.append(inner).append("{\n");
      sb.append(inner)
          .append("  \"templateId\": \"")
          .append(escapeJson(d.templateId().value()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"sourcePath\": \"")
          .append(escapeJson(d.sourcePath()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"severity\": \"")
          .append(escapeJson(d.severity().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"code\": \"")
          .append(escapeJson(d.code().qualifiedCode()))
          .append("\",\n");
      sb.append(inner).append("  \"message\": \"").append(escapeJson(d.message())).append("\",\n");
      sb.append(inner).append("  \"startLine\": ").append(d.startLine()).append(",\n");
      sb.append(inner).append("  \"startColumn\": ").append(d.startColumn()).append(",\n");
      sb.append(inner).append("  \"endLine\": ").append(d.endLine()).append(",\n");
      sb.append(inner).append("  \"endColumn\": ").append(d.endColumn()).append("\n");
      sb.append(inner).append("}");
      if (i < diagnostics.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(indent).append("]");
  }

  private static void formatTemplates(
      List<SingleTemplateMigration> templates, StringBuilder sb, String indent) {
    if (templates.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < templates.size(); i++) {
      SingleTemplateMigration t = templates.get(i);
      String inner = indent + "  ";
      sb.append(inner).append("{\n");
      sb.append(inner)
          .append("  \"templateId\": \"")
          .append(escapeJson(t.templateId().value()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"relativePath\": \"")
          .append(escapeJson(t.relativePath()))
          .append("\",\n");
      sb.append(inner).append("  \"valid\": ").append(t.valid()).append(",\n");
      sb.append(inner).append("  \"compatible\": ").append(t.compatible()).append(",\n");

      sb.append(inner).append("  \"diagnostics\": ");
      formatDiagnostics(t.diagnostics(), sb, inner);
      sb.append(",\n");

      sb.append(inner).append("  \"findings\": ");
      formatFindings(t.findings(), sb, inner);
      sb.append("\n");

      sb.append(inner).append("}");
      if (i < templates.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(indent).append("]");
  }

  private static void formatFindings(
      List<MigrationFinding> findings, StringBuilder sb, String indent) {
    if (findings.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < findings.size(); i++) {
      MigrationFinding f = findings.get(i);
      String inner = indent + "  ";
      sb.append(inner).append("{\n");
      sb.append(inner).append("  \"ruleId\": \"").append(escapeJson(f.ruleId())).append("\",\n");
      sb.append(inner)
          .append("  \"templateId\": \"")
          .append(escapeJson(f.templateId().value()))
          .append("\",\n");
      sb.append(inner).append("  \"sourceSpan\": ");
      formatSpan(f.sourceSpan(), sb);
      sb.append(",\n");
      sb.append(inner)
          .append("  \"category\": \"")
          .append(escapeJson(f.category().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"severity\": \"")
          .append(escapeJson(f.severity().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"classification\": \"")
          .append(escapeJson(f.classification().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"confidence\": \"")
          .append(escapeJson(f.confidence().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"construct\": \"")
          .append(escapeJson(f.construct()))
          .append("\",\n");
      sb.append(inner).append("  \"message\": \"").append(escapeJson(f.message())).append("\",\n");
      sb.append(inner)
          .append("  \"velocityBehavior\": \"")
          .append(escapeJson(f.velocityBehavior()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"vietTemplateBehavior\": \"")
          .append(escapeJson(f.vietTemplateBehavior()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"migrationAction\": \"")
          .append(escapeJson(f.migrationAction()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"relatedDiagnosticCode\": ")
          .append(
              f.relatedDiagnosticCode()
                  .map(dc -> "\"" + escapeJson(dc.qualifiedCode()) + "\"")
                  .orElse("null"))
          .append("\n");
      sb.append(inner).append("}");
      if (i < findings.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(indent).append("]");
  }

  private static void formatSpan(SourceSpan span, StringBuilder sb) {
    if (span == null) {
      sb.append("null");
      return;
    }
    sb.append("{\"startLine\": ")
        .append(span.startLine())
        .append(", \"startColumn\": ")
        .append(span.startColumn())
        .append(", \"endLine\": ")
        .append(span.endLine())
        .append(", \"endColumn\": ")
        .append(span.endColumn())
        .append("}");
  }

  private static String escapeJson(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(s.length() + 8);
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
          if (c <= 0x1F) {
            sb.append("\\u00").append(HEX_DIGITS[(c >> 4) & 0x0F]).append(HEX_DIGITS[c & 0x0F]);
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
