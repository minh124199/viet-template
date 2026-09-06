package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** Map literal expression, e.g. {@code {"name": "Minh", "age": 20}}. */
public record VtlMapLiteralExpression(List<VtlMapEntry> entries, SourceSpan span)
    implements VtlExpression {

  public VtlMapLiteralExpression {
    entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
