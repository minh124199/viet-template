package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;
import java.util.Optional;

/** Statement returning from the current function or macro, optionally with an evaluated value. */
public record IrReturn(Optional<IrExpression> value, SourceSpan span) implements IrStatement {

  public IrReturn {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrReturn voidReturn(SourceSpan span) {
    return new IrReturn(Optional.empty(), span);
  }

  public static IrReturn withValue(IrExpression value, SourceSpan span) {
    return new IrReturn(Optional.of(value), span);
  }
}
