package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.Objects;

/** Expression checking if an operand evaluates to null or undefined. */
public record IrIsNull(IrExpression expression, SourceSpan span) implements IrExpression {

  public IrIsNull {
    Objects.requireNonNull(expression, "expression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  @Override
  public VType type() {
    return VTypes.BOOLEAN;
  }
}
