package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import java.math.BigDecimal;
import java.util.Objects;

/** Implements value-based equality and relational comparisons for VTL. */
public final class VtlComparisonOperations {

  private VtlComparisonOperations() {}

  public static boolean equals(Object left, Object right) {
    if (left == right) return true;
    if (left == null || right == null) return false;

    if (VtlNumericOperations.isNumeric(left) && VtlNumericOperations.isNumeric(right)) {
      BigDecimal l = VtlNumericOperations.toBigDecimal(left);
      BigDecimal r = VtlNumericOperations.toBigDecimal(right);
      return l.compareTo(r) == 0;
    }

    if (VtlNumericOperations.isNumeric(left) && right instanceof CharSequence cs) {
      try {
        BigDecimal r = new BigDecimal(cs.toString().trim());
        return VtlNumericOperations.toBigDecimal(left).compareTo(r) == 0;
      } catch (NumberFormatException ignored) {
      }
    } else if (left instanceof CharSequence cs && VtlNumericOperations.isNumeric(right)) {
      try {
        BigDecimal l = new BigDecimal(cs.toString().trim());
        return l.compareTo(VtlNumericOperations.toBigDecimal(right)) == 0;
      } catch (NumberFormatException ignored) {
      }
    }

    if (left instanceof Boolean b1 && right instanceof Boolean b2) {
      return b1.equals(b2);
    }

    if (left instanceof CharSequence cs1 && right instanceof CharSequence cs2) {
      return cs1.toString().equals(cs2.toString());
    }

    return Objects.equals(left, right);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static int compare(Object left, Object right, SourceSpan span, TemplateId id) {
    if (left == null || right == null) {
      throw new TemplateRenderException(
          "Cannot perform relational comparison on null value",
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    if (VtlNumericOperations.isNumeric(left) && VtlNumericOperations.isNumeric(right)) {
      BigDecimal l = VtlNumericOperations.toBigDecimal(left);
      BigDecimal r = VtlNumericOperations.toBigDecimal(right);
      return l.compareTo(r);
    }

    if (VtlNumericOperations.isNumeric(left) && right instanceof CharSequence cs) {
      try {
        BigDecimal r = new BigDecimal(cs.toString().trim());
        return VtlNumericOperations.toBigDecimal(left).compareTo(r);
      } catch (NumberFormatException ignored) {
      }
    } else if (left instanceof CharSequence cs && VtlNumericOperations.isNumeric(right)) {
      try {
        BigDecimal l = new BigDecimal(cs.toString().trim());
        return l.compareTo(VtlNumericOperations.toBigDecimal(right));
      } catch (NumberFormatException ignored) {
      }
    }

    if (left instanceof Comparable c1 && right instanceof Comparable c2) {
      if (left.getClass().isAssignableFrom(right.getClass())
          || right.getClass().isAssignableFrom(left.getClass())) {
        return c1.compareTo(c2);
      }
    }

    throw new TemplateRenderException(
        "Incompatible types for comparison: "
            + left.getClass().getSimpleName()
            + " and "
            + right.getClass().getSimpleName(),
        id,
        span,
        InterpreterDiagnosticCodes.SYNTAX_ERROR);
  }
}
