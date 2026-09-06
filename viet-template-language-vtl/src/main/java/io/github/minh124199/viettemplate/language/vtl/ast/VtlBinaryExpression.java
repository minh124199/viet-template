package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Binary operation expression, e.g. {@code 1 + 2}, {@code $a && $b}, {@code $x == $y}. */
public record VtlBinaryExpression(
    VtlExpression left, VtlBinaryOperator operator, VtlExpression right, SourceSpan span)
    implements VtlExpression {

  public VtlBinaryExpression {
    Objects.requireNonNull(left, "left must not be null");
    Objects.requireNonNull(operator, "operator must not be null");
    Objects.requireNonNull(right, "right must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
