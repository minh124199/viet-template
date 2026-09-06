package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;
import java.util.Optional;

/** AST node for {@code #break} or {@code #break($scope)}. */
public record VtlBreakDirectiveNode(Optional<VtlExpression> scopeExpression, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlBreakDirectiveNode {
    Objects.requireNonNull(scopeExpression, "scopeExpression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlBreakDirectiveNode of(SourceSpan span) {
    return new VtlBreakDirectiveNode(Optional.empty(), span);
  }
}
