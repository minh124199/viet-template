package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import java.util.Objects;

/** Statement closing a loop structure and releasing loop iterator/state resources. */
public record IrLoopEnd(IrLocal iteratorLocal, SourceSpan span) implements IrStatement {

  public IrLoopEnd {
    Objects.requireNonNull(iteratorLocal, "iteratorLocal must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
