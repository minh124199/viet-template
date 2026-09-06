package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import java.util.Objects;

/**
 * Statement initializing loop iterator or index state before entering a loop.
 *
 * <p>Part of the linear control flow loop sequence (LoopSetup, LoopNext, LoopEnd).
 */
public record IrLoopSetup(
    LoopPlan plan, IrExpression iterable, IrLocal iteratorLocal, SourceSpan span)
    implements IrStatement {

  public IrLoopSetup {
    Objects.requireNonNull(plan, "plan must not be null");
    Objects.requireNonNull(iterable, "iterable must not be null");
    Objects.requireNonNull(iteratorLocal, "iteratorLocal must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
