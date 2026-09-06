package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/**
 * Expression loading a declared root model parameter.
 *
 * <p>Implements the {@code LoadParam} IR operation.
 */
public record IrLoadParam(String name, int slot, VType type, SourceSpan span)
    implements IrExpression {

  public IrLoadParam {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
