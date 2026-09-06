package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.Objects;

/**
 * Constant literal value in IR.
 *
 * <p>Preserves unboxed primitive types (boolean, int, long, double) and reference types (string,
 * null, etc.).
 */
public record IrConst(Object value, VType type, SourceSpan span) implements IrExpression {

  public IrConst {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrConst ofBoolean(boolean value, SourceSpan span) {
    return new IrConst(value, VTypes.BOOLEAN, span);
  }

  public static IrConst ofInt(int value, SourceSpan span) {
    return new IrConst(value, VTypes.INT, span);
  }

  public static IrConst ofLong(long value, SourceSpan span) {
    return new IrConst(value, VTypes.LONG, span);
  }

  public static IrConst ofDouble(double value, SourceSpan span) {
    return new IrConst(value, VTypes.DOUBLE, span);
  }

  public static IrConst ofString(String value, SourceSpan span) {
    return new IrConst(value, VTypes.STRING, span);
  }

  public static IrConst ofNull(SourceSpan span) {
    return new IrConst(null, VTypes.NULL, span);
  }
}
