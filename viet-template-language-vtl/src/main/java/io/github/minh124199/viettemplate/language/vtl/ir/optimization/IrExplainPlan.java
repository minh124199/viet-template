package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrInvokeAllowedMethod;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostic utility explaining the execution and optimization plan of an {@link IrTemplate}.
 *
 * <p>Implements {@code docs/06-optimization-pipeline.md §19} output explaining bound access plans,
 * specialized loops, dynamic dispatches, and constant chunks.
 */
public final class IrExplainPlan {

  private IrExplainPlan() {}

  /** Formats a structured explain-plan string representation for the given template. */
  public static String explain(IrTemplate template) {
    return explain(template, null);
  }

  /** Formats a structured explain-plan string with optimization statistics. */
  public static String explain(IrTemplate template, OptimizationStatistics statistics) {
    Objects.requireNonNull(template, "template must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append("=== IrExplainPlan: ").append(template.id().value()).append(" ===\n");
    if (statistics != null) {
      sb.append("Optimization Statistics:\n  ").append(statistics.toString()).append("\n");
    }

    // Constants summary
    int totalConstants = template.constants().size();
    long utf8Count =
        template.constants().allTextConstants().stream()
            .filter(c -> c != null && c.utf8Bytes().isPresent())
            .count();
    sb.append(
        String.format("Constants: %d entries (%d UTF-8 pre-encoded)\n", totalConstants, utf8Count));

    // Parameters summary
    sb.append("Parameters: ").append(template.parameters().size()).append("\n");
    for (IrParameter p : template.parameters()) {
      sb.append(
          String.format("  - $%s (slot=%d, type=%s)\n", p.name(), p.slot(), p.type().typeName()));
    }

    // Root block
    sb.append("Root Execution Plan:\n");
    explainBlock(template.root(), template, sb, "  ");

    // Functions / Macros
    if (!template.functions().isEmpty()) {
      sb.append("Functions (").append(template.functions().size()).append("):\n");
      for (IrFunction fn : template.functions()) {
        sb.append("  Function #")
            .append(fn.name())
            .append(" (params=")
            .append(fn.parameters().size())
            .append("):\n");
        explainBlock(fn.body(), template, sb, "    ");
      }
    }

    return sb.toString();
  }

  private static void explainBlock(
      IrBlock block, IrTemplate template, StringBuilder sb, String indent) {
    for (IrStatement s : block.statements()) {
      explainStatement(s, template, sb, indent);
    }
  }

  private static void explainStatement(
      IrStatement stmt, IrTemplate template, StringBuilder sb, String indent) {
    if (stmt instanceof IrWriteConst wc) {
      Optional<IrTextConstant> tc = template.constants().getTextConstant(wc.constantId());
      String text = tc.map(IrTextConstant::text).orElse("<missing>");
      String preview = text.replace("\n", "\\n").replace("\r", "\\r");
      if (preview.length() > 30) {
        preview = preview.substring(0, 27) + "...";
      }
      boolean hasUtf8 = tc.flatMap(IrTextConstant::utf8Bytes).isPresent();
      sb.append(indent)
          .append("WRITE_CONST id=")
          .append(wc.constantId())
          .append(" len=")
          .append(text.length())
          .append(hasUtf8 ? " [UTF-8]" : "")
          .append(" \"")
          .append(preview)
          .append("\"\n");
      return;
    }

    if (stmt instanceof IrWriteValue wv) {
      sb.append(indent)
          .append("WRITE_VALUE escape=")
          .append(wv.escapeMode())
          .append(" null=")
          .append(wv.nullMode())
          .append(" expr=");
      explainExpression(wv.value(), sb);
      sb.append("\n");
      return;
    }

    if (stmt instanceof IrIf ifStmt) {
      sb.append(indent).append("IF cond=");
      explainExpression(ifStmt.condition(), sb);
      sb.append(" then:\n");
      explainBlock(ifStmt.thenBlock(), template, sb, indent + "  ");
      if (ifStmt.elseBlock().isPresent()) {
        sb.append(indent).append("ELSE:\n");
        explainBlock(ifStmt.elseBlock().get(), template, sb, indent + "  ");
      }
      return;
    }

    if (stmt instanceof IrLoop loop) {
      sb.append(indent)
          .append("LOOP plan=")
          .append(loop.plan())
          .append(" var=$")
          .append(loop.elementLocal().name())
          .append(" in=");
      explainExpression(loop.iterable(), sb);
      sb.append(":\n");
      explainBlock(loop.body(), template, sb, indent + "  ");
      if (loop.elseBody().isPresent()) {
        sb.append(indent).append("LOOP_ELSE:\n");
        explainBlock(loop.elseBody().get(), template, sb, indent + "  ");
      }
      return;
    }

    if (stmt instanceof IrCallMacro cm) {
      sb.append(indent)
          .append("CALL_MACRO #")
          .append(cm.macroName())
          .append(" (args=")
          .append(cm.arguments().size())
          .append(")\n");
      return;
    }

    sb.append(indent).append(stmt.getClass().getSimpleName()).append("\n");
  }

  private static void explainExpression(IrExpression expr, StringBuilder sb) {
    if (expr instanceof IrGetProperty gp) {
      sb.append("DIRECT_GET [")
          .append(gp.accessPlan().getClass().getSimpleName())
          .append("] .")
          .append(gp.propertyName())
          .append(" -> ")
          .append(gp.type().typeName());
      return;
    }

    if (expr instanceof IrInvokeAllowedMethod im) {
      sb.append("DIRECT_INVOKE [")
          .append(im.targetMethod().getName())
          .append("] .")
          .append(im.methodName())
          .append("() -> ")
          .append(im.type().typeName());
      return;
    }

    if (expr instanceof IrDynamicDispatch dd) {
      sb.append("DYNAMIC_DISPATCH site=")
          .append(dd.callSite().id())
          .append(" target=")
          .append(dd.targetName())
          .append(" kind=")
          .append(dd.callSite().kind());
      return;
    }

    sb.append(expr.getClass().getSimpleName())
        .append("(")
        .append(expr.type().typeName())
        .append(")");
  }
}
