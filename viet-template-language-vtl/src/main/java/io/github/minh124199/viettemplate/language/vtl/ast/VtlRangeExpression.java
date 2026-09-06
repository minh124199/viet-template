package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Range expression, e.g. {@code [1..10]} or {@code [$start..$end]}. */
public record VtlRangeExpression(VtlExpression start, VtlExpression end, SourceSpan span)
    implements VtlExpression {

  public VtlRangeExpression {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
