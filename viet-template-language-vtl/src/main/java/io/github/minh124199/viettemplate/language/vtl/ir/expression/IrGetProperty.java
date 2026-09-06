package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullAccessMode;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.Objects;

/**
 * Expression accessing a property on a receiver object according to an explicit {@link AccessPlan}.
 *
 * <p>Implements the {@code GetProperty} IR operation.
 */
public record IrGetProperty(
    IrExpression receiver,
    String propertyName,
    VType type,
    AccessPlan accessPlan,
    NullAccessMode nullMode,
    SourceSpan span)
    implements IrExpression {

  public IrGetProperty {
    Objects.requireNonNull(receiver, "receiver must not be null");
    Objects.requireNonNull(propertyName, "propertyName must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(accessPlan, "accessPlan must not be null");
    Objects.requireNonNull(nullMode, "nullMode must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
