package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/**
 * Expression performing binary operations (arithmetic, comparison, equality, or logical).
 *
 * <p>Implements arithmetic, comparison, and boolean operations in IR.
 */
public record IrBinaryOp(
    BinaryOpKind op, IrExpression left, IrExpression right, VType type, SourceSpan span)
    implements IrExpression {

  public IrBinaryOp {
    Objects.requireNonNull(op, "op must not be null");
    Objects.requireNonNull(left, "left must not be null");
    Objects.requireNonNull(right, "right must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
