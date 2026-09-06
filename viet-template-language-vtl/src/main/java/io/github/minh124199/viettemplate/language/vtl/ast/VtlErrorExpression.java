package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Synthetic or recovery node representing a malformed expression. */
public record VtlErrorExpression(String message, SourceSpan span) implements VtlExpression {

  public VtlErrorExpression {
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
