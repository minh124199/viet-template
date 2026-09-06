package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/**
 * Statement halting entire template rendering immediately.
 *
 * <p>Implements the {@code #stop} stackless control signal.
 */
public record IrStop(SourceSpan span) implements IrStatement {

  public IrStop {
    Objects.requireNonNull(span, "span must not be null");
  }
}
