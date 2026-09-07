package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;

/**
 * Statement representing dynamic runtime template evaluation ({@code #evaluate}).
 *
 * <p>Implements dynamic runtime evaluation, requiring interpreter execution and gated by profile.
 */
public record IrEvaluate(IrExpression expression, SourceSpan span) implements IrStatement {

  public IrEvaluate {
    Objects.requireNonNull(expression, "expression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
