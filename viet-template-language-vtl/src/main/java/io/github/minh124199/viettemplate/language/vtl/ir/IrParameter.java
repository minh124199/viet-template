package io.github.minh124199.viettemplate.language.vtl.ir;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;
import java.util.Optional;

/** Declared model parameter accepted by a template or function. */
public record IrParameter(
    String name, VType type, int slot, Optional<IrExpression> defaultValue, SourceSpan span) {

  public IrParameter {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(defaultValue, "defaultValue must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public IrParameter(String name, VType type, int slot, SourceSpan span) {
    this(name, type, slot, Optional.empty(), span);
  }
}
