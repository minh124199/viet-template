package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** Syntactic target of a {@code #set} assignment directive. */
public sealed interface VtlAssignmentTarget permits VtlAssignmentTarget.ReferenceTarget {

  SourceSpan span();

  record ReferenceTarget(VtlReference reference, SourceSpan span) implements VtlAssignmentTarget {
    public ReferenceTarget {
      Objects.requireNonNull(reference, "reference must not be null");
      Objects.requireNonNull(span, "span must not be null");
    }

    public static ReferenceTarget of(VtlReference reference) {
      return new ReferenceTarget(reference, reference.span());
    }
  }
}
