package io.github.minh124199.viettemplate.explanation;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Package-private formatter generating deterministic JSON representation (formatVersion = 1) for
 * compiler explanations with zero external dependencies.
 */
final class ExplanationJsonFormatter {

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private ExplanationJsonFormatter() {}

  public static String format(TemplateExplanation explanation) {
    Objects.requireNonNull(explanation, "explanation must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"formatVersion\": 1,\n");
    sb.append("  \"success\": ").append(explanation.success()).append(",\n");
    sb.append("  \"totalTemplates\": ").append(explanation.totalTemplates()).append(",\n");
    sb.append("  \"totalExpressions\": ").append(explanation.totalExpressions()).append(",\n");

    sb.append("  \"diagnostics\": ");
    formatDiagnostics(explanation.diagnostics(), sb, "  ");
    sb.append(",\n");

    sb.append("  \"templates\": ");
    formatTemplates(explanation.templates(), sb, "  ");
    sb.append("\n");

    sb.append("}\n");
    return sb.toString();
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
      List<SingleTemplateExplanation> templates, StringBuilder sb, String indent) {
    if (templates.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < templates.size(); i++) {
      SingleTemplateExplanation t = templates.get(i);
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
      sb.append(inner)
          .append("  \"profile\": \"")
          .append(escapeJson(t.profile().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"typeCheckingMode\": \"")
          .append(escapeJson(t.typeCheckingMode().name()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"strictReferences\": ")
          .append(t.strictReferences())
          .append(",\n");
      sb.append(inner)
          .append("  \"nullRenderMode\": \"")
          .append(escapeJson(t.nullRenderMode()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"escapeMode\": \"")
          .append(escapeJson(t.escapeMode()))
          .append("\",\n");
      sb.append(inner).append("  \"typed\": ").append(t.typed()).append(",\n");
      sb.append(inner)
          .append("  \"contractClass\": ")
          .append(formatOptionalString(t.contractClass()))
          .append(",\n");
      sb.append(inner)
          .append("  \"compilationStatus\": \"")
          .append(escapeJson(t.compilationStatus()))
          .append("\",\n");
      sb.append(inner).append("  \"aotEligible\": ").append(t.aotEligible()).append(",\n");

      sb.append(inner).append("  \"aotRejectionReasons\": ");
      formatStringList(t.aotRejectionReasons(), sb);
      sb.append(",\n");

      sb.append(inner).append("  \"dependencies\": ");
      formatDependencies(t.dependencies(), sb, inner);
      sb.append(",\n");

      sb.append(inner).append("  \"expressions\": ");
      formatExpressions(t.expressions(), sb, inner);
      sb.append("\n");

      sb.append(inner).append("}");
      if (i < templates.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(indent).append("]");
  }

  private static void formatDependencies(
      List<TemplateDependency> dependencies, StringBuilder sb, String indent) {
    if (dependencies.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < dependencies.size(); i++) {
      TemplateDependency d = dependencies.get(i);
      String inner = indent + "  ";
      sb.append(inner).append("{\n");
      sb.append(inner)
          .append("  \"source\": \"")
          .append(escapeJson(d.source().value()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"target\": \"")
          .append(escapeJson(d.target().value()))
          .append("\",\n");
      sb.append(inner).append("  \"kind\": \"").append(escapeJson(d.kind().name())).append("\"\n");
      sb.append(inner).append("}");
      if (i < dependencies.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(indent).append("]");
  }

  private static void formatExpressions(
      List<ExpressionExplanation> expressions, StringBuilder sb, String indent) {
    if (expressions.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < expressions.size(); i++) {
      ExpressionExplanation e = expressions.get(i);
      String inner = indent + "  ";
      sb.append(inner).append("{\n");

      sb.append(inner).append("  \"span\": ");
      formatSpan(e.span(), sb, inner);
      sb.append(",\n");

      sb.append(inner)
          .append("  \"sourceText\": \"")
          .append(escapeJson(e.sourceText()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"expressionKind\": \"")
          .append(escapeJson(e.expressionKind()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"inferredType\": \"")
          .append(escapeJson(e.inferredType()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"typeConfidence\": \"")
          .append(escapeJson(e.typeConfidence()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"nullability\": \"")
          .append(escapeJson(e.nullability()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"symbolOrigin\": \"")
          .append(escapeJson(e.symbolOrigin()))
          .append("\",\n");
      sb.append(inner)
          .append("  \"receiverType\": ")
          .append(formatOptionalString(e.receiverType()))
          .append(",\n");
      sb.append(inner)
          .append("  \"resolvedMember\": ")
          .append(formatOptionalString(e.resolvedMember()))
          .append(",\n");
      sb.append(inner)
          .append("  \"resolutionStrategy\": \"")
          .append(escapeJson(e.resolutionStrategy()))
          .append("\",\n");
      sb.append(inner).append("  \"directAccess\": ").append(e.directAccess()).append(",\n");
      sb.append(inner).append("  \"aotEligible\": ").append(e.aotEligible()).append(",\n");

      sb.append(inner).append("  \"aotRejectionReasons\": ");
      formatStringList(e.aotRejectionReasons(), sb);
      sb.append(",\n");

      sb.append(inner)
          .append("  \"outputDispatch\": ")
          .append(formatOptionalString(e.outputDispatch()))
          .append(",\n");
      sb.append(inner)
          .append("  \"outputMethod\": ")
          .append(formatOptionalString(e.outputMethod()))
          .append(",\n");
      sb.append(inner)
          .append("  \"escaping\": ")
          .append(formatOptionalString(e.escaping()))
          .append(",\n");
      sb.append(inner)
          .append("  \"securityPolicy\": ")
          .append(formatOptionalString(e.securityPolicy()))
          .append(",\n");

      sb.append(inner).append("  \"optimizationRejections\": ");
      formatStringList(e.optimizationRejections(), sb);
      sb.append("\n");

      sb.append(inner).append("}");
      if (i < expressions.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append(indent).append("]");
  }

  private static void formatSpan(SourceSpan span, StringBuilder sb, String indent) {
    if (!span.isKnown()) {
      sb.append("null");
      return;
    }
    sb.append("{\n");
    String inner = indent + "    ";
    sb.append(inner).append("\"startOffset\": ").append(span.startOffset()).append(",\n");
    sb.append(inner).append("\"endOffset\": ").append(span.endOffset()).append(",\n");
    sb.append(inner).append("\"startLine\": ").append(span.startLine()).append(",\n");
    sb.append(inner).append("\"startColumn\": ").append(span.startColumn()).append(",\n");
    sb.append(inner).append("\"endLine\": ").append(span.endLine()).append(",\n");
    sb.append(inner).append("\"endColumn\": ").append(span.endColumn()).append("\n");
    sb.append(indent).append("  }");
  }

  private static void formatStringList(List<String> list, StringBuilder sb) {
    if (list.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[");
    for (int i = 0; i < list.size(); i++) {
      sb.append("\"").append(escapeJson(list.get(i))).append("\"");
      if (i < list.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append("]");
  }

  private static String formatOptionalString(Optional<String> opt) {
    return opt.map(s -> "\"" + escapeJson(s) + "\"").orElse("null");
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
