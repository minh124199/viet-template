package io.github.minh124199.viettemplate.explanation;

import io.github.minh124199.viettemplate.aot.TemplateAotDiagnostic;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import java.util.Objects;

/** Package-private formatter generating deterministic human-readable text explanation reports. */
final class ExplanationTextFormatter {

  private ExplanationTextFormatter() {}

  public static String format(TemplateExplanation explanation) {
    Objects.requireNonNull(explanation, "explanation must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append("=== Viet Template Compiler Explanation ===\n");
    sb.append("Total Templates: ").append(explanation.totalTemplates()).append("\n");
    sb.append("Total Expressions: ").append(explanation.totalExpressions()).append("\n");
    sb.append("Success: ").append(explanation.success()).append("\n");
    sb.append("Diagnostics: ").append(explanation.diagnostics().size()).append("\n");

    if (!explanation.diagnostics().isEmpty()) {
      sb.append("\nDiagnostics:\n");
      for (TemplateAotDiagnostic diag : explanation.diagnostics()) {
        sb.append("  - ").append(diag.formattedMessage()).append("\n");
      }
    }

    for (SingleTemplateExplanation tmpl : explanation.templates()) {
      sb.append("\n=== Template Explanation: ").append(tmpl.templateId().value()).append(" ===\n");
      sb.append("Relative Path: ").append(tmpl.relativePath()).append("\n");
      sb.append("Profile: ").append(tmpl.profile().name()).append("\n");
      sb.append("Type Checking: ").append(tmpl.typeCheckingMode().name()).append("\n");
      sb.append("Strict References: ").append(tmpl.strictReferences()).append("\n");
      sb.append("Null Render Mode: ").append(tmpl.nullRenderMode().name()).append("\n");
      sb.append("Escape Mode: ").append(tmpl.escapeMode().name()).append("\n");
      sb.append("Typed: ").append(tmpl.typed());
      if (tmpl.contractClass().isPresent()) {
        sb.append(" (Contract: ").append(tmpl.contractClass().get()).append(")");
      }
      sb.append("\n");

      sb.append("Compilation Status: ")
          .append(tmpl.compilationStatus())
          .append(" (AOT Eligible: ")
          .append(tmpl.aotEligible())
          .append(")\n");

      if (!tmpl.aotRejectionReasons().isEmpty()) {
        sb.append("AOT Rejection Reasons:\n");
        for (String reason : tmpl.aotRejectionReasons()) {
          sb.append("  - ").append(reason).append("\n");
        }
      }

      sb.append("Dependencies (").append(tmpl.dependencies().size()).append("):\n");
      for (TemplateDependency dep : tmpl.dependencies()) {
        sb.append("  - ")
            .append(dep.kind().name())
            .append(" -> ")
            .append(dep.target().value())
            .append("\n");
      }

      sb.append("Expressions (").append(tmpl.expressions().size()).append("):\n");
      for (ExpressionExplanation expr : tmpl.expressions()) {
        formatExpression(expr, sb);
      }
    }

    return sb.toString();
  }

  private static void formatExpression(ExpressionExplanation expr, StringBuilder sb) {
    sb.append("  ")
        .append(expr.sourceText())
        .append(" [")
        .append(expr.span().startLine())
        .append(":")
        .append(expr.span().startColumn())
        .append(" - ")
        .append(expr.span().endLine())
        .append(":")
        .append(expr.span().endColumn())
        .append("]\n");

    sb.append("    Kind: ").append(expr.expressionKind()).append("\n");
    sb.append("    Type: ")
        .append(expr.inferredType())
        .append(" (Confidence: ")
        .append(expr.typeConfidence())
        .append(", Nullable: ")
        .append(expr.nullability())
        .append(")\n");
    sb.append("    Symbol Origin: ").append(expr.symbolOrigin()).append("\n");

    if (expr.receiverType().isPresent()) {
      sb.append("    Receiver: ").append(expr.receiverType().get()).append("\n");
    }
    if (expr.resolvedMember().isPresent()) {
      sb.append("    Member: ").append(expr.resolvedMember().get()).append("\n");
    }

    sb.append("    Access: ")
        .append(expr.resolutionStrategy())
        .append(" (Direct: ")
        .append(expr.directAccess())
        .append(")\n");

    sb.append("    AOT Eligible: ").append(expr.aotEligible());
    if (!expr.aotRejectionReasons().isEmpty()) {
      sb.append(" [Reasons: ").append(String.join(", ", expr.aotRejectionReasons())).append("]");
    }
    sb.append("\n");

    if (expr.outputDispatch().isPresent()) {
      sb.append("    Output Dispatch: ")
          .append(expr.outputDispatch().get())
          .append(" -> ")
          .append(expr.outputMethod().orElse(""))
          .append("\n");
    }
    if (expr.escaping().isPresent()) {
      sb.append("    Escaping: ").append(expr.escaping().get()).append("\n");
    }
    if (expr.securityPolicy().isPresent()) {
      sb.append("    Security Policy: ").append(expr.securityPolicy().get()).append("\n");
    }

    if (!expr.optimizationRejections().isEmpty()) {
      sb.append("    Optimization Rejections: ")
          .append(String.join(", ", expr.optimizationRejections()))
          .append("\n");
    }
  }
}
