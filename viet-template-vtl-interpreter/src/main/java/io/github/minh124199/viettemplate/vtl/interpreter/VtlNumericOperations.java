package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Implements deterministic numeric and string operations for VTL arithmetic expressions. */
public final class VtlNumericOperations {

  private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

  private VtlNumericOperations() {}

  public static Object add(Object left, Object right, SourceSpan span, TemplateId id) {
    if (left instanceof String || right instanceof String) {
      return String.valueOf(left) + String.valueOf(right);
    }
    if (isNumeric(left) && isNumeric(right)) {
      if (isNonFinite(left) || isNonFinite(right)) {
        return ((Number) left).doubleValue() + ((Number) right).doubleValue();
      }
      if (isDecimal(left) || isDecimal(right)) {
        return toBigDecimal(left).add(toBigDecimal(right), MATH_CONTEXT);
      }
      return toBigInteger(left).add(toBigInteger(right));
    }
    // Fall back to string concatenation if not both numeric
    return String.valueOf(left) + String.valueOf(right);
  }

  public static Object subtract(Object left, Object right, SourceSpan span, TemplateId id) {
    checkNumericOperands("-", left, right, span, id);
    if (isNonFinite(left) || isNonFinite(right)) {
      return ((Number) left).doubleValue() - ((Number) right).doubleValue();
    }
    if (isDecimal(left) || isDecimal(right)) {
      return toBigDecimal(left).subtract(toBigDecimal(right), MATH_CONTEXT);
    }
    return toBigInteger(left).subtract(toBigInteger(right));
  }

  public static Object multiply(Object left, Object right, SourceSpan span, TemplateId id) {
    checkNumericOperands("*", left, right, span, id);
    if (isNonFinite(left) || isNonFinite(right)) {
      return ((Number) left).doubleValue() * ((Number) right).doubleValue();
    }
    if (isDecimal(left) || isDecimal(right)) {
      return toBigDecimal(left).multiply(toBigDecimal(right), MATH_CONTEXT);
    }
    return toBigInteger(left).multiply(toBigInteger(right));
  }

  public static Object divide(Object left, Object right, SourceSpan span, TemplateId id) {
    checkNumericOperands("/", left, right, span, id);
    if (isNonFinite(left) || isNonFinite(right)) {
      double r = ((Number) right).doubleValue();
      if (r == 0.0) {
        throw new TemplateRenderException(
            "Division by zero", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return ((Number) left).doubleValue() / r;
    }
    if (isDecimal(left) || isDecimal(right)) {
      BigDecimal r = toBigDecimal(right);
      if (r.compareTo(BigDecimal.ZERO) == 0) {
        throw new TemplateRenderException(
            "Division by zero", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return toBigDecimal(left).divide(r, MATH_CONTEXT);
    }
    BigInteger r = toBigInteger(right);
    if (r.equals(BigInteger.ZERO)) {
      throw new TemplateRenderException(
          "Division by zero", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }
    return toBigInteger(left).divide(r);
  }

  public static Object remainder(Object left, Object right, SourceSpan span, TemplateId id) {
    checkNumericOperands("%", left, right, span, id);
    if (isNonFinite(left) || isNonFinite(right)) {
      double r = ((Number) right).doubleValue();
      if (r == 0.0) {
        throw new TemplateRenderException(
            "Division by zero in remainder", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return ((Number) left).doubleValue() % r;
    }
    if (isDecimal(left) || isDecimal(right)) {
      BigDecimal r = toBigDecimal(right);
      if (r.compareTo(BigDecimal.ZERO) == 0) {
        throw new TemplateRenderException(
            "Division by zero in remainder", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return toBigDecimal(left).remainder(r, MATH_CONTEXT);
    }
    BigInteger r = toBigInteger(right);
    if (r.equals(BigInteger.ZERO)) {
      throw new TemplateRenderException(
          "Division by zero in remainder", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }
    return toBigInteger(left).remainder(r);
  }

  public static Object negate(Object value, SourceSpan span, TemplateId id) {
    if (value instanceof BigDecimal bd) {
      return bd.negate();
    }
    if (value instanceof BigInteger bi) {
      return bi.negate();
    }
    if (value instanceof Double d) {
      return -d;
    }
    if (value instanceof Float f) {
      return -f;
    }
    if (value instanceof Number n) {
      return -n.longValue();
    }
    throw new TemplateRenderException(
        "Unary negation '-' requires a numeric operand, but was: "
            + (value == null ? "null" : value.getClass().getName()),
        id,
        span,
        InterpreterDiagnosticCodes.SYNTAX_ERROR);
  }

  public static boolean isNumeric(Object o) {
    return o instanceof Number;
  }

  public static boolean isDecimal(Object o) {
    return o instanceof BigDecimal || o instanceof Double || o instanceof Float;
  }

  public static boolean isNonFinite(Object o) {
    if (o instanceof Double d) {
      return Double.isNaN(d) || Double.isInfinite(d);
    }
    if (o instanceof Float f) {
      return Float.isNaN(f) || Float.isInfinite(f);
    }
    return false;
  }

  public static BigDecimal toBigDecimal(Object o) {
    if (o instanceof BigDecimal bd) return bd;
    if (o instanceof BigInteger bi) return new BigDecimal(bi);
    if (o instanceof Number n) return new BigDecimal(n.toString());
    return new BigDecimal(String.valueOf(o));
  }

  public static BigInteger toBigInteger(Object o) {
    if (o instanceof BigInteger bi) return bi;
    if (o instanceof BigDecimal bd) return bd.toBigInteger();
    if (o instanceof Number n) return BigInteger.valueOf(n.longValue());
    return new BigInteger(String.valueOf(o));
  }

  private static void checkNumericOperands(
      String op, Object left, Object right, SourceSpan span, TemplateId id) {
    if (!isNumeric(left) || !isNumeric(right)) {
      throw new TemplateRenderException(
          "Operator '"
              + op
              + "' requires numeric operands, but received: "
              + (left == null ? "null" : left.getClass().getSimpleName())
              + " and "
              + (right == null ? "null" : right.getClass().getSimpleName()),
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }
  }
}
