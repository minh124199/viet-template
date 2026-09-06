package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/** No-op statement, useful as placeholder or dead-code elimination landing pad. */
public record IrNoOp(SourceSpan span) implements IrStatement {

  public IrNoOp {
    Objects.requireNonNull(span, "span must not be null");
  }
}
