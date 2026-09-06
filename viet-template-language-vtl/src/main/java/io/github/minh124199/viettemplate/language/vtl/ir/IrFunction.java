package io.github.minh124199.viettemplate.language.vtl.ir;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/**
 * Representation of a lowered macro or template subroutine.
 *
 * <p>Contains its declared parameters, allocated local slots, and body block.
 */
public record IrFunction(
    String name,
    List<IrParameter> parameters,
    List<IrLocal> locals,
    IrBlock body,
    SourceSpan span) {

  public IrFunction {
    Objects.requireNonNull(name, "name must not be null");
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
    locals = List.copyOf(Objects.requireNonNull(locals, "locals must not be null"));
    Objects.requireNonNull(body, "body must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
