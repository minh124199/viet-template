package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** List literal expression, e.g. {@code [1, 2, "three", $four]}. */
public record VtlListLiteralExpression(List<VtlExpression> elements, SourceSpan span)
    implements VtlExpression {

  public VtlListLiteralExpression {
    elements = List.copyOf(Objects.requireNonNull(elements, "elements must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
