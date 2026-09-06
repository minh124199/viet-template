package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured loop statement iterating over an expression using an explicit {@link LoopPlan}.
 *
 * <p>Contains body block, optional else block, element local variable, and optional loop state
 * metadata local ($foreach).
 */
public record IrLoop(
    LoopPlan plan,
    IrExpression iterable,
    IrLocal elementLocal,
    Optional<IrLocal> loopStateLocal,
    IrBlock body,
    Optional<IrBlock> elseBody,
    SourceSpan span)
    implements IrStatement {

  public IrLoop {
    Objects.requireNonNull(plan, "plan must not be null");
    Objects.requireNonNull(iterable, "iterable must not be null");
    Objects.requireNonNull(elementLocal, "elementLocal must not be null");
    Objects.requireNonNull(loopStateLocal, "loopStateLocal must not be null");
    Objects.requireNonNull(body, "body must not be null");
    Objects.requireNonNull(elseBody, "elseBody must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrLoop of(
      LoopPlan plan,
      IrExpression iterable,
      IrLocal elementLocal,
      IrLocal loopStateLocal,
      IrBlock body,
      SourceSpan span) {
    return new IrLoop(
        plan,
        iterable,
        elementLocal,
        Optional.ofNullable(loopStateLocal),
        body,
        Optional.empty(),
        span);
  }

  public static IrLoop of(
      LoopPlan plan,
      IrExpression iterable,
      IrLocal elementLocal,
      IrLocal loopStateLocal,
      IrBlock body,
      IrBlock elseBody,
      SourceSpan span) {
    return new IrLoop(
        plan,
        iterable,
        elementLocal,
        Optional.ofNullable(loopStateLocal),
        body,
        Optional.ofNullable(elseBody),
        span);
  }
}
