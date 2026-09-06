package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/**
 * Statement writing a static text chunk referenced by its constant pool ID to output.
 *
 * <p>Implements the {@code WriteStatic} IR operation.
 */
public record IrWriteConst(int constantId, SourceSpan span) implements IrStatement {

  public IrWriteConst {
    Objects.requireNonNull(span, "span must not be null");
  }
}
