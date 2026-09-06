package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** AST node for {@code #define($block) body #end}. */
public record VtlDefineDirectiveNode(
    VtlReference targetReference, List<VtlNode> body, SourceSpan span) implements VtlDirectiveNode {

  public VtlDefineDirectiveNode {
    Objects.requireNonNull(targetReference, "targetReference must not be null");
    body = List.copyOf(Objects.requireNonNull(body, "body must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
