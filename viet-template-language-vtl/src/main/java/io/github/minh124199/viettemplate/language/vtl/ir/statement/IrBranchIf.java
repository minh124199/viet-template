package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;
import java.util.Optional;

/**
 * Conditional branch statement jumping to {@code targetLabel} if {@code condition} evaluates to
 * true, or falling through / jumping to {@code elseTargetLabel} otherwise.
 */
public record IrBranchIf(
    IrExpression condition, String targetLabel, Optional<String> elseTargetLabel, SourceSpan span)
    implements IrStatement {

  public IrBranchIf {
    Objects.requireNonNull(condition, "condition must not be null");
    Objects.requireNonNull(targetLabel, "targetLabel must not be null");
    Objects.requireNonNull(elseTargetLabel, "elseTargetLabel must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrBranchIf of(IrExpression condition, String targetLabel, SourceSpan span) {
    return new IrBranchIf(condition, targetLabel, Optional.empty(), span);
  }

  public static IrBranchIf of(
      IrExpression condition, String targetLabel, String elseTargetLabel, SourceSpan span) {
    return new IrBranchIf(condition, targetLabel, Optional.of(elseTargetLabel), span);
  }
}
