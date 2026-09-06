package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;

/**
 * Unconditional branch statement transferring control to the block identified by {@code
 * targetLabel}.
 */
public record IrBranch(String targetLabel, SourceSpan span) implements IrStatement {

  public IrBranch {
    Objects.requireNonNull(targetLabel, "targetLabel must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
