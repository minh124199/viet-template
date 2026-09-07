package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.Objects;

/**
 * Expression evaluating an alternate (fallback) expression if the primary value is null, undefined,
 * or empty (e.g. {@code ${var|'fallback'}}).
 */
public record IrAlternateValue(
    IrExpression primary, IrExpression fallback, VType type, SourceSpan span)
    implements IrExpression {

  public IrAlternateValue {
    Objects.requireNonNull(primary, "primary must not be null");
    Objects.requireNonNull(fallback, "fallback must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrAlternateValue of(IrExpression primary, IrExpression fallback, SourceSpan span) {
    return new IrAlternateValue(primary, fallback, VTypes.DYNAMIC, span);
  }
}
