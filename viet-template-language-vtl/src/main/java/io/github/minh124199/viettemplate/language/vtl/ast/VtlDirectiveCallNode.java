package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/**
 * AST node representing an invocation of a custom or user-defined directive or macro, e.g. {@code
 * #renderHeader("title", $user)}.
 */
public record VtlDirectiveCallNode(String name, List<VtlExpression> arguments, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlDirectiveCallNode {
    Objects.requireNonNull(name, "name must not be null");
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
