package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** AST node for {@code #set($target = $value)}. */
public record VtlSetDirectiveNode(VtlAssignmentTarget target, VtlExpression value, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlSetDirectiveNode {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public VtlExpression valueExpression() {
    return value();
  }
}
