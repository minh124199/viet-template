package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** AST node for {@code #foreach($item in $items) body #else emptyBody #end}. */
public record VtlForeachDirectiveNode(
    VtlReference loopVariable,
    VtlExpression iterable,
    List<VtlNode> body,
    Optional<List<VtlNode>> elseBody,
    SourceSpan span)
    implements VtlDirectiveNode {

  public VtlForeachDirectiveNode {
    Objects.requireNonNull(loopVariable, "loopVariable must not be null");
    Objects.requireNonNull(iterable, "iterable must not be null");
    body = List.copyOf(Objects.requireNonNull(body, "body must not be null"));
    Objects.requireNonNull(elseBody, "elseBody must not be null");
    elseBody = elseBody.map(List::copyOf);
    Objects.requireNonNull(span, "span must not be null");
  }
}
