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
    if (value == null || value.isUndefined() || value.isNull()) {
      return false;
    }
    return isTruthyObject(value.value(), emptyCheck);
  }

  public static boolean isTruthyObject(Object obj, boolean emptyCheck) {
    if (obj == null) {
      return false;
    }

    if (obj instanceof Boolean b) {
      return b;
    }

    // Custom getAsBoolean() method is always evaluated first (regardless of emptyCheck)
    try {
      Method getAsBoolean = findZeroArgMethod(obj.getClass(), "getAsBoolean");
      if (getAsBoolean != null
          && (getAsBoolean.getReturnType() == boolean.class
              || getAsBoolean.getReturnType() == Boolean.class)) {
        Object res = getAsBoolean.invoke(obj);
        if (res instanceof Boolean b) {
          return b;
        }
      }
    } catch (Exception ignored) {
      // Fall through
    }

    if (!emptyCheck) {
      return true;
    }

    return !asEmpty(obj);
  }

  public static boolean asEmpty(Object obj) {
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

    // Check custom isEmpty()
    try {
      Method isEmpty = findZeroArgMethod(obj.getClass(), "isEmpty");
      if (isEmpty != null
          && (isEmpty.getReturnType() == boolean.class
              || isEmpty.getReturnType() == Boolean.class)) {
        Object res = isEmpty.invoke(obj);
        if (res instanceof Boolean b) {
          return b;
        }
      }
    } catch (Exception ignored) {
      // Fall through
    }

    // Check custom length() returning Number
    try {
      Method length = findZeroArgMethod(obj.getClass(), "length");
      if (length != null && Number.class.isAssignableFrom(boxType(length.getReturnType()))) {
        Object res = length.invoke(obj);
        if (res instanceof Number n) {
          return isZero(n);
        }
      }
    } catch (Exception ignored) {
      // Fall through
    }

    // Check custom size() returning Number
    try {
      Method size = findZeroArgMethod(obj.getClass(), "size");
      if (size != null && Number.class.isAssignableFrom(boxType(size.getReturnType()))) {
        Object res = size.invoke(obj);
        if (res instanceof Number n) {
          return isZero(n);
        }
      }
    } catch (Exception ignored) {
      // Fall through
    }

    // Check Number
    if (obj instanceof Number n) {
      return isZero(n);
    }

    // Check custom getAsString()
    try {
      Method getAsString = findZeroArgMethod(obj.getClass(), "getAsString");
      if (getAsString != null && String.class.isAssignableFrom(getAsString.getReturnType())) {
        Object res = getAsString.invoke(obj);
        return res == null || ((String) res).isEmpty();
      }
    } catch (Exception ignored) {
      // Fall through
    }

    // Check custom getAsNumber()
    try {
      Method getAsNumber = findZeroArgMethod(obj.getClass(), "getAsNumber");
      if (getAsNumber != null && Number.class.isAssignableFrom(getAsNumber.getReturnType())) {
        Object res = getAsNumber.invoke(obj);
        return res == null || isZero((Number) res);
      }
    } catch (Exception ignored) {
      // Fall through
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
