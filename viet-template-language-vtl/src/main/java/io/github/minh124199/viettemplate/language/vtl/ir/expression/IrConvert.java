package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/** Expression coercing or converting an expression to a target static {@link VType}. */
public record IrConvert(IrExpression expression, VType type, SourceSpan span)
    implements IrExpression {

  public IrConvert {
    Objects.requireNonNull(expression, "expression must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
