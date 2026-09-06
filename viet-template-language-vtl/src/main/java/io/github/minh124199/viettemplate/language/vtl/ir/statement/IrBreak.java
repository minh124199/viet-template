package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/**
 * Statement terminating the innermost enclosing loop.
 *
 * <p>Implements the {@code #break} stackless control signal.
 */
public record IrBreak(SourceSpan span) implements IrStatement {

  public IrBreak {
    Objects.requireNonNull(span, "span must not be null");
  }
}
