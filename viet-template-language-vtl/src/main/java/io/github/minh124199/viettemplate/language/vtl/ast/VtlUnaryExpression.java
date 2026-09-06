package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Unary operation expression, e.g. {@code !$enabled}, {@code not $enabled}, or {@code -42}. */
public record VtlUnaryExpression(VtlUnaryOperator operator, VtlExpression operand, SourceSpan span)
    implements VtlExpression {

  public VtlUnaryExpression {
    Objects.requireNonNull(operator, "operator must not be null");
    Objects.requireNonNull(operand, "operand must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
