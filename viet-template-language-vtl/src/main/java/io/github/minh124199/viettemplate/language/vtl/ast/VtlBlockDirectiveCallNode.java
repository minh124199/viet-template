package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** AST node representing a block macro/directive call, e.g. {@code #@panel($title) body #end}. */
public record VtlBlockDirectiveCallNode(
    String name, List<VtlExpression> arguments, List<VtlNode> body, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlBlockDirectiveCallNode {
    Objects.requireNonNull(name, "name must not be null");
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    body = List.copyOf(Objects.requireNonNull(body, "body must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
