package io.github.minh124199.viettemplate.vtl.interpreter;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

/** Centralized truthiness evaluator implementing Apache Velocity 2.4.x compatible rules. */
public final class VtlTruthiness {

  private VtlTruthiness() {}

  public static boolean isTruthy(EvaluationValue value, boolean emptyCheck) {
    return isTruthy(value, emptyCheck, VtlSecurityPolicy.standard());
  }

  public static boolean isTruthy(
      EvaluationValue value, boolean emptyCheck, VtlSecurityPolicy securityPolicy) {
    if (value == null || value.isUndefined() || value.isNull()) {
      return false;
    }
    return isTruthyObject(value.value(), emptyCheck, securityPolicy);
  }

  public static boolean isTruthyObject(Object obj, boolean emptyCheck) {
    return isTruthyObject(obj, emptyCheck, VtlSecurityPolicy.standard());
  }

  public static boolean isTruthyObject(
      Object obj, boolean emptyCheck, VtlSecurityPolicy securityPolicy) {
    if (obj == null) {
      return false;
    }

    if (obj instanceof Boolean b) {
      return b;
    }

    VtlSecurityPolicy policy =
        securityPolicy != null ? securityPolicy : VtlSecurityPolicy.standard();

    // Custom getAsBoolean() method is always evaluated first (regardless of emptyCheck)
    if (policy.isClassPermitted(obj.getClass())) {
      try {
        Method getAsBoolean = findZeroArgMethod(obj.getClass(), "getAsBoolean");
        if (getAsBoolean != null
            && (getAsBoolean.getReturnType() == boolean.class
                || getAsBoolean.getReturnType() == Boolean.class)
            && policy.isMethodPermitted(obj.getClass(), getAsBoolean)) {
          Object res = getAsBoolean.invoke(obj);
          if (res instanceof Boolean b) {
            return b;
          }
        }
      } catch (Exception ignored) {
        // Fall through
      }
    }

    if (!emptyCheck) {
      return true;
    }

    return !asEmpty(obj, policy);
  }

  public static boolean asEmpty(Object obj) {
    return asEmpty(obj, VtlSecurityPolicy.standard());
  }

  public static boolean asEmpty(Object obj, VtlSecurityPolicy securityPolicy) {
    if (obj == null) {
      return true;
    }

    if (obj.getClass().isArray()) {
      return Array.getLength(obj) == 0;
    }

    // Fast-path standard interfaces before reflection
    if (obj instanceof CharSequence cs) {
      return cs.isEmpty();
    }
    if (obj instanceof Collection<?> col) {
      return col.isEmpty();
    }
    if (obj instanceof Map<?, ?> map) {
      return map.isEmpty();
    }

    VtlSecurityPolicy policy =
        securityPolicy != null ? securityPolicy : VtlSecurityPolicy.standard();
    boolean classPermitted = policy.isClassPermitted(obj.getClass());

    // Check custom isEmpty()
    if (classPermitted) {
      try {
        Method isEmpty = findZeroArgMethod(obj.getClass(), "isEmpty");
        if (isEmpty != null
            && (isEmpty.getReturnType() == boolean.class
                || isEmpty.getReturnType() == Boolean.class)
            && policy.isMethodPermitted(obj.getClass(), isEmpty)) {
          Object res = isEmpty.invoke(obj);
          if (res instanceof Boolean b) {
            return b;
          }
        }
      } catch (Exception ignored) {
        // Fall through
      }
    }

    // Check custom length() returning Number
    if (classPermitted) {
      try {
        Method length = findZeroArgMethod(obj.getClass(), "length");
        if (length != null
            && Number.class.isAssignableFrom(boxType(length.getReturnType()))
            && policy.isMethodPermitted(obj.getClass(), length)) {
          Object res = length.invoke(obj);
          if (res instanceof Number n) {
            return isZero(n);
          }
        }
      } catch (Exception ignored) {
        // Fall through
      }
    }

    // Check custom size() returning Number
    if (classPermitted) {
      try {
        Method size = findZeroArgMethod(obj.getClass(), "size");
        if (size != null
            && Number.class.isAssignableFrom(boxType(size.getReturnType()))
            && policy.isMethodPermitted(obj.getClass(), size)) {
          Object res = size.invoke(obj);
          if (res instanceof Number n) {
            return isZero(n);
          }
        }
      } catch (Exception ignored) {
        // Fall through
      }
    }

    // Check Number
    if (obj instanceof Number n) {
      return isZero(n);
    }

    // Check custom getAsString()
    if (classPermitted) {
      try {
        Method getAsString = findZeroArgMethod(obj.getClass(), "getAsString");
        if (getAsString != null
            && String.class.isAssignableFrom(getAsString.getReturnType())
            && policy.isMethodPermitted(obj.getClass(), getAsString)) {
          Object res = getAsString.invoke(obj);
          return res == null || ((String) res).isEmpty();
        }
      } catch (Exception ignored) {
        // Fall through
      }
    }

    // Check custom getAsNumber()
    if (classPermitted) {
      try {
        Method getAsNumber = findZeroArgMethod(obj.getClass(), "getAsNumber");
        if (getAsNumber != null
            && Number.class.isAssignableFrom(getAsNumber.getReturnType())
            && policy.isMethodPermitted(obj.getClass(), getAsNumber)) {
          Object res = getAsNumber.invoke(obj);
          return res == null || isZero((Number) res);
        }
      } catch (Exception ignored) {
        // Fall through
      }
    }

    return false;
  }

  public static boolean isZero(Number n) {
    if (n == null) {
      return true;
    }
    if (n instanceof BigInteger bi) {
      return bi.signum() == 0;
    }
    if (n instanceof BigDecimal bd) {
      return bd.compareTo(BigDecimal.ZERO) == 0;
    }
    if (n instanceof Float f) {
      return f == 0.0f;
    }
    if (n instanceof Double d) {
      return d == 0.0d;
    }
    return n.longValue() == 0L;
  }

  private static Method findZeroArgMethod(Class<?> clazz, String name) {
    try {
      Method m = clazz.getMethod(name);
      return m.getParameterCount() == 0 ? m : null;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Class<?> boxType(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == float.class) return Float.class;
    if (type == short.class) return Short.class;
    if (type == byte.class) return Byte.class;
    if (type == boolean.class) return Boolean.class;
    if (type == char.class) return Character.class;
    return type;
  }
}
