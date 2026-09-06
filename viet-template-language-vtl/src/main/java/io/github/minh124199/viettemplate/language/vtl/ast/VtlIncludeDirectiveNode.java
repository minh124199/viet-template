package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** AST node for {@code #include("one.txt", "two.txt")}. */
public record VtlIncludeDirectiveNode(List<VtlExpression> arguments, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlIncludeDirectiveNode {
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
