package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Null literal expression ({@code null}). */
public record VtlNullLiteralExpression(SourceSpan span) implements VtlExpression {

  public VtlNullLiteralExpression {
    Objects.requireNonNull(span, "span must not be null");
  }
}
