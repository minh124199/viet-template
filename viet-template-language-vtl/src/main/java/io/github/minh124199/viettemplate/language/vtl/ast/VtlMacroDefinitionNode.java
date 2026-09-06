package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** AST node for {@code #macro(name $param1 $param2) body #end}. */
public record VtlMacroDefinitionNode(
    String name, List<VtlMacroParameter> parameters, List<VtlNode> body, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlMacroDefinitionNode {
    Objects.requireNonNull(name, "name must not be null");
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
    body = List.copyOf(Objects.requireNonNull(body, "body must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
