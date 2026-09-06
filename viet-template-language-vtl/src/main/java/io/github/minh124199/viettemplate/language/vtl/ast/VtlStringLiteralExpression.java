package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Single-quoted uninterpolated string literal expression, e.g. {@code 'Hello $name'}. */
public record VtlStringLiteralExpression(String value, SourceSpan span) implements VtlExpression {

  public VtlStringLiteralExpression {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
