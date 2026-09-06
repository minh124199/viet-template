package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Boolean literal expression ({@code true} or {@code false}). */
public record VtlBooleanLiteralExpression(boolean value, SourceSpan span) implements VtlExpression {

  public VtlBooleanLiteralExpression {
    Objects.requireNonNull(span, "span must not be null");
  }
}
