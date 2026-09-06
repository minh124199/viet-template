package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.Objects;

/**
 * Expression converting an arbitrary expression to boolean truthiness according to VTL semantics.
 */
public record IrTruthiness(IrExpression expression, boolean emptyCheck, SourceSpan span)
    implements IrExpression {

  public IrTruthiness {
    Objects.requireNonNull(expression, "expression must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  @Override
  public VType type() {
    return VTypes.BOOLEAN;
  }
}
