package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Top-level template node that outputs the evaluated value of a {@link VtlReference}. */
public record VtlReferenceOutputNode(VtlReference reference, SourceSpan span) implements VtlNode {

  public VtlReferenceOutputNode {
    Objects.requireNonNull(reference, "reference must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlReferenceOutputNode of(VtlReference reference) {
    return new VtlReferenceOutputNode(reference, reference.span());
  }
}
