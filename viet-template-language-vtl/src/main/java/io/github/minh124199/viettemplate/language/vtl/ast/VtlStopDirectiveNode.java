package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;
import java.util.Optional;

/** AST node for {@code #stop} or {@code #stop($message)}. */
public record VtlStopDirectiveNode(Optional<VtlExpression> messageExpression, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlStopDirectiveNode {
    Objects.requireNonNull(messageExpression, "messageExpression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlStopDirectiveNode of(SourceSpan span) {
    return new VtlStopDirectiveNode(Optional.empty(), span);
  }
}
