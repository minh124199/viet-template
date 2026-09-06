package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;

/**
 * Statement storing the result of an evaluated expression into a local variable.
 *
 * <p>Implements the {@code StoreLocal} IR operation (lowered from {@code #set}).
 */
public record IrStoreLocal(IrLocal local, IrExpression value, SourceSpan span)
    implements IrStatement {

  public IrStoreLocal {
    Objects.requireNonNull(local, "local must not be null");
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
