package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;

/**
 * Statement assigning a property on a target object.
 *
 * <p>Implements navigated property assignment (e.g. {@code #set($bean.text = 'val')}).
 */
public record IrSetProperty(
    IrExpression target, String propertyName, IrExpression value, SourceSpan span)
    implements IrStatement {

  public IrSetProperty {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(propertyName, "propertyName must not be null");
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
