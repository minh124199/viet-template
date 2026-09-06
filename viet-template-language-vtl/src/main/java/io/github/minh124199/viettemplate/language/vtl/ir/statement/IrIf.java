package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured conditional statement executing either the {@code thenBlock} or optional {@code
 * elseBlock}.
 *
 * <p>Implements the structured conditional branch IR operation.
 */
public record IrIf(
    IrExpression condition, IrBlock thenBlock, Optional<IrBlock> elseBlock, SourceSpan span)
    implements IrStatement {

  public IrIf {
    Objects.requireNonNull(condition, "condition must not be null");
    Objects.requireNonNull(thenBlock, "thenBlock must not be null");
    Objects.requireNonNull(elseBlock, "elseBlock must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrIf of(IrExpression condition, IrBlock thenBlock, SourceSpan span) {
    return new IrIf(condition, thenBlock, Optional.empty(), span);
  }

  public static IrIf of(
      IrExpression condition, IrBlock thenBlock, IrBlock elseBlock, SourceSpan span) {
    return new IrIf(condition, thenBlock, Optional.of(elseBlock), span);
  }
}
