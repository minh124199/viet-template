package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/**
 * Expression performing index navigation on an array, list, or map.
 *
 * <p>Implements the {@code IndexGet} IR operation.
 */
public record IrIndexGet(IrExpression receiver, IrExpression index, VType type, SourceSpan span)
    implements IrExpression {

  public IrIndexGet {
    Objects.requireNonNull(receiver, "receiver must not be null");
    Objects.requireNonNull(index, "index must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
