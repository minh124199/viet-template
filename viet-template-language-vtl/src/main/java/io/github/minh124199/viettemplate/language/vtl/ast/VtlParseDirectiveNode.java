package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** AST node for {@code #parse("template.vm")}. */
public record VtlParseDirectiveNode(VtlExpression templateExpression, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlParseDirectiveNode {
    Objects.requireNonNull(templateExpression, "templateExpression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
