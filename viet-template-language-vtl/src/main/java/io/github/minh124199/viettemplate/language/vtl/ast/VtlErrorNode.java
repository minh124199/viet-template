package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Synthetic or recovery node representing a syntactic error in the template body. */
public record VtlErrorNode(String message, SourceSpan span) implements VtlNode {

  public VtlErrorNode {
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
