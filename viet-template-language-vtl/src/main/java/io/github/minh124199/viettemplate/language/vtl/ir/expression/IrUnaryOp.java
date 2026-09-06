package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/** Expression performing unary operations (negate or logical not). */
public record IrUnaryOp(UnaryOpKind op, IrExpression operand, VType type, SourceSpan span)
    implements IrExpression {

  public IrUnaryOp {
    Objects.requireNonNull(op, "op must not be null");
    Objects.requireNonNull(operand, "operand must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
