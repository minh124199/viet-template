package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** AST node for dynamic evaluation {@code #evaluate($expression)}. */
public record VtlEvaluateDirectiveNode(VtlExpression expression, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlEvaluateDirectiveNode {
    Objects.requireNonNull(expression, "expression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
