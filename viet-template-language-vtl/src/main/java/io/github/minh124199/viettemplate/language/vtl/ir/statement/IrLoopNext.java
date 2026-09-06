package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import java.util.Objects;
import java.util.Optional;

/**
 * Statement advancing the loop cursor, checking exhaustion, and binding the current element.
 *
 * <p>Jumps to {@code exitLabel} if the loop is exhausted.
 */
public record IrLoopNext(
    IrLocal iteratorLocal,
    IrLocal elementLocal,
    Optional<IrLocal> loopStateLocal,
    String exitLabel,
    SourceSpan span)
    implements IrStatement {

  public IrLoopNext {
    Objects.requireNonNull(iteratorLocal, "iteratorLocal must not be null");
    Objects.requireNonNull(elementLocal, "elementLocal must not be null");
    Objects.requireNonNull(loopStateLocal, "loopStateLocal must not be null");
    Objects.requireNonNull(exitLabel, "exitLabel must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
