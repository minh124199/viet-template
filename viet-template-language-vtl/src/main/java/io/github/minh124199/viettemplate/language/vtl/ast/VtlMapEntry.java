package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Key-value entry in a map literal expression, e.g. {@code 'key': 'value'}. */
public record VtlMapEntry(VtlExpression key, VtlExpression value, SourceSpan span) {

  public VtlMapEntry {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
