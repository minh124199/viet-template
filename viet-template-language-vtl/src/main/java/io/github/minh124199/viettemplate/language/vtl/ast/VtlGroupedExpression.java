package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Grouped/parenthesized expression, e.g. {@code (1 + 2)} or {@code ($a || $b)}. */
public record VtlGroupedExpression(VtlExpression expression, SourceSpan span)
    implements VtlExpression {

  public VtlGroupedExpression {
    Objects.requireNonNull(expression, "expression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
