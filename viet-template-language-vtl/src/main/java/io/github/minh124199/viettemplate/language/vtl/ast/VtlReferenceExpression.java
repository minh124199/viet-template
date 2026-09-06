package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Expression wrapping a {@link VtlReference}. */
public record VtlReferenceExpression(VtlReference reference, SourceSpan span)
    implements VtlExpression {

  public VtlReferenceExpression {
    Objects.requireNonNull(reference, "reference must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlReferenceExpression of(VtlReference reference) {
    return new VtlReferenceExpression(reference, reference.span());
  }
}
