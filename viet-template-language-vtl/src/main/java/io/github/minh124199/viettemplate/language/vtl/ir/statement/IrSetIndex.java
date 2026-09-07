package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;

/**
 * Statement assigning an index on an indexed target object (Map, List, or array).
 *
 * <p>Implements indexed assignment (e.g. {@code #set($map['key'] = 'val')}).
 */
public record IrSetIndex(
    IrExpression target, IrExpression index, IrExpression value, SourceSpan span)
    implements IrStatement {

  public IrSetIndex {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(index, "index must not be null");
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
