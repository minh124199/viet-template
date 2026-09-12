package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import java.math.BigDecimal;
import java.util.Objects;

/** Implements value-based equality and relational comparisons for VTL. */
public final class VtlComparisonOperations {

  private VtlComparisonOperations() {}

  public static boolean equals(Object left, Object right) {
    return equals(left, right, (VtlSecurityPolicy) null);
  }

  public static boolean equals(Object left, Object right, LinkerAccessPolicy securityPolicy) {
    if (left == right) return true;
    if (left == null || right == null) return false;

    if (securityPolicy != null
        && securityPolicy.isSafeProfile()
        && (!securityPolicy.isClassPermitted(left.getClass())
            || !securityPolicy.isClassPermitted(right.getClass()))) {
      return left == right;
    }
    return equals(left, right, (VtlSecurityPolicy) null);
  }

  public static boolean equals(Object left, Object right, VtlSecurityPolicy securityPolicy) {
    if (left == right) return true;
    if (left == null || right == null) return false;

    if (securityPolicy != null
        && securityPolicy.isSafeProfile()
        && (!securityPolicy.isClassPermitted(left.getClass())
            || !securityPolicy.isClassPermitted(right.getClass()))) {
      return left == right;
    }

    if (VtlNumericOperations.isNumeric(left) && VtlNumericOperations.isNumeric(right)) {
      if (VtlNumericOperations.isNonFinite(left) || VtlNumericOperations.isNonFinite(right)) {
        return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
      }
      BigDecimal l = VtlNumericOperations.toBigDecimal(left);
      BigDecimal r = VtlNumericOperations.toBigDecimal(right);
      return l.compareTo(r) == 0;
    }

    if (VtlNumericOperations.isNumeric(left) && right instanceof CharSequence cs) {
      if (VtlNumericOperations.isNonFinite(left)) {
        return false;
      }
      try {
        BigDecimal r = new BigDecimal(cs.toString().trim());
        return VtlNumericOperations.toBigDecimal(left).compareTo(r) == 0;
      } catch (NumberFormatException ignored) {
      }
    } else if (left instanceof CharSequence cs && VtlNumericOperations.isNumeric(right)) {
      if (VtlNumericOperations.isNonFinite(right)) {
        return false;
      }
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

  public static int compare(Object left, Object right, SourceSpan span, TemplateId id) {
    return compare(left, right, span, id, (VtlSecurityPolicy) null);
  }

  public static int compare(
      Object left,
      Object right,
      SourceSpan span,
      TemplateId id,
      LinkerAccessPolicy securityPolicy) {
    if (left == null || right == null) {
      throw new TemplateRenderException(
          "Cannot perform relational comparison on null value",
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    if (securityPolicy != null
        && securityPolicy.isSafeProfile()
        && (!securityPolicy.isClassPermitted(left.getClass())
            || !securityPolicy.isClassPermitted(right.getClass()))) {
      throw new TemplateSecurityException(
          "Relational comparison on unpermitted class is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    return compare(left, right, span, id, (VtlSecurityPolicy) null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static int compare(
      Object left, Object right, SourceSpan span, TemplateId id, VtlSecurityPolicy securityPolicy) {
    if (left == null || right == null) {
      throw new TemplateRenderException(
          "Cannot perform relational comparison on null value",
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    if (securityPolicy != null
        && securityPolicy.isSafeProfile()
        && (!securityPolicy.isClassPermitted(left.getClass())
            || !securityPolicy.isClassPermitted(right.getClass()))) {
      throw new TemplateSecurityException(
          "Relational comparison on unpermitted class is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    if (VtlNumericOperations.isNumeric(left) && VtlNumericOperations.isNumeric(right)) {
      if (VtlNumericOperations.isNonFinite(left) || VtlNumericOperations.isNonFinite(right)) {
        return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
      }
      BigDecimal l = VtlNumericOperations.toBigDecimal(left);
      BigDecimal r = VtlNumericOperations.toBigDecimal(right);
      return l.compareTo(r);
    }

    if (VtlNumericOperations.isNumeric(left) && right instanceof CharSequence cs) {
      if (VtlNumericOperations.isNonFinite(left)) {
        try {
          return Double.compare(
              ((Number) left).doubleValue(), Double.parseDouble(cs.toString().trim()));
        } catch (NumberFormatException ignored) {
        }
      }
      try {
        BigDecimal r = new BigDecimal(cs.toString().trim());
        return VtlNumericOperations.toBigDecimal(left).compareTo(r);
      } catch (NumberFormatException ignored) {
      }
    } else if (left instanceof CharSequence cs && VtlNumericOperations.isNumeric(right)) {
      if (VtlNumericOperations.isNonFinite(right)) {
        try {
          return Double.compare(
              Double.parseDouble(cs.toString().trim()), ((Number) right).doubleValue());
        } catch (NumberFormatException ignored) {
        }
      }
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
