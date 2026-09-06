package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import java.util.Objects;

/**
 * Statement evaluating an expression and rendering its value to the output stream.
 *
 * <p>Implements the {@code WriteValue} IR operation with explicit escaping strategy and null
 * rendering semantics.
 */
public record IrWriteValue(
    IrExpression value, IrEscapeMode escapeMode, NullRenderMode nullMode, SourceSpan span)
    implements IrStatement {

  public IrWriteValue {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(escapeMode, "escapeMode must not be null");
    Objects.requireNonNull(nullMode, "nullMode must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
