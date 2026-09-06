package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Correctness-first reflection-based implementation of {@link ReferenceAccess}. */
public final class DefaultReferenceAccess implements ReferenceAccess {

  private final VtlSecurityPolicy securityPolicy;

  public DefaultReferenceAccess(VtlSecurityPolicy securityPolicy) {
    this.securityPolicy = Objects.requireNonNull(securityPolicy, "securityPolicy must not be null");
  }

  @Override
  public EvaluationValue getProperty(
      Object target, String propertyName, SourceSpan span, TemplateId id) {
    if (target == null) {
      return EvaluationValue.definedNull();
    }

    Class<?> clazz = target.getClass();
    if (!securityPolicy.isClassPermitted(clazz)) {
      throw new TemplateSecurityException(
          "Access to class " + clazz.getName() + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    String capitalized = capitalize(propertyName);

    // 1. getname()
    Method m = findPublicZeroArgMethod(clazz, "get" + propertyName.toLowerCase());
    if (m != null) {
      return invokeGetter(target, m, span, id);
    }

    // 2. getName()
    m = findPublicZeroArgMethod(clazz, "get" + capitalized);
    if (m != null) {
      return invokeGetter(target, m, span, id);
    }

    // 3. Map.get("name")
    if (target instanceof Map<?, ?> map) {
      if (map.containsKey(propertyName)) {
        return EvaluationValue.of(map.get(propertyName));
      }
    }

    // 4. isName()
    m = findPublicZeroArgMethod(clazz, "is" + capitalized);
    if (m != null && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
      return invokeGetter(target, m, span, id);
    }

    // 5. isname()
    m = findPublicZeroArgMethod(clazz, "is" + propertyName.toLowerCase());
    if (m != null && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
      return invokeGetter(target, m, span, id);
    }

    // 6. Record accessor / zero-arg method matching name()
    m = findPublicZeroArgMethod(clazz, propertyName);
    if (m != null) {
      return invokeGetter(target, m, span, id);
    }

    // 7. Public field
    Field f = findPublicField(clazz, propertyName);
    if (f != null) {
      if (!securityPolicy.isFieldPermitted(clazz, f)) {
        throw new TemplateSecurityException(
            "Access to field "
                + f.getName()
                + " on "
                + clazz.getName()
                + " is denied by security policy",
            id,
            span,
            InterpreterDiagnosticCodes.SECURITY_VIOLATION);
      }
      try {
        return EvaluationValue.of(f.get(target));
      } catch (IllegalAccessException e) {
        throw new TemplateRenderException(
            "Cannot access field: " + propertyName,
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR,
            e);
      }
    }

    return EvaluationValue.undefined();
  }

  @Override
  public EvaluationValue invokeMethod(
      Object target,
      String methodName,
      List<EvaluationValue> arguments,
      SourceSpan span,
      TemplateId id) {
    if (target == null) {
      throw new TemplateRenderException(
          "Cannot invoke method '" + methodName + "' on null target",
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    Class<?> clazz = target.getClass();
    if (!securityPolicy.isClassPermitted(clazz)) {
      throw new TemplateSecurityException(
          "Access to class " + clazz.getName() + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    List<Method> candidates = new ArrayList<>();
    for (Method m : clazz.getMethods()) {
      if (m.getName().equals(methodName)
          && Modifier.isPublic(m.getModifiers())
          && m.getParameterCount() == arguments.size()) {
        candidates.add(m);
      }
    }

    if (candidates.isEmpty()) {
      return EvaluationValue.undefined();
    }

    Method bestMethod = null;
    int bestScore = Integer.MAX_VALUE;
    boolean ambiguous = false;

    for (Method candidate : candidates) {
      int score = scoreCandidate(candidate, arguments);
      if (score >= 0) {
        if (score < bestScore) {
          bestScore = score;
          bestMethod = candidate;
          ambiguous = false;
        } else if (score == bestScore) {
          ambiguous = true;
        }
      }
    }

    if (ambiguous) {
      throw new TemplateRenderException(
          "Ambiguous method invocation: multiple methods matching '"
              + methodName
              + "' with arguments on "
              + clazz.getName(),
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    if (bestMethod == null) {
      return EvaluationValue.undefined();
    }

    if (!securityPolicy.isMethodPermitted(clazz, bestMethod)) {
      throw new TemplateSecurityException(
          "Invocation of method "
              + bestMethod.getName()
              + " on "
              + clazz.getName()
              + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }

    Object[] convertedArgs = new Object[arguments.size()];
    Class<?>[] paramTypes = bestMethod.getParameterTypes();
    for (int i = 0; i < arguments.size(); i++) {
      convertedArgs[i] = convertArgument(arguments.get(i), paramTypes[i], span, id);
    }

    try {
      Object result = bestMethod.invoke(target, convertedArgs);
      return EvaluationValue.of(result);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof ControlSignal cs) {
        throw cs;
      }
      throw new TemplateRenderException(
          "Method '" + methodName + "' threw an exception: " + cause.getMessage(),
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR,
          cause);
    } catch (IllegalAccessException e) {
      throw new TemplateRenderException(
          "Cannot access method: " + methodName,
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR,
          e);
    }
  }

  @Override
  public EvaluationValue getIndex(
      Object target, EvaluationValue index, SourceSpan span, TemplateId id) {
    if (target == null) {
      return EvaluationValue.definedNull();
    }

    if (target instanceof List<?> list) {
      int idx = toIntegerIndex(index, span, id);
      if (idx >= 0 && idx < list.size()) {
        return EvaluationValue.of(list.get(idx));
      }
      return EvaluationValue.undefined();
    }

    if (target.getClass().isArray()) {
      int idx = toIntegerIndex(index, span, id);
      int length = Array.getLength(target);
      if (idx >= 0 && idx < length) {
        return EvaluationValue.of(Array.get(target, idx));
      }
      return EvaluationValue.undefined();
    }

    if (target instanceof Map<?, ?> map) {
      Object key = index.asObjectOrNull();
      if (map.containsKey(key)) {
        return EvaluationValue.of(map.get(key));
      }
      return EvaluationValue.undefined();
    }

    // Fallback: try invoking get(index)
    return invokeMethod(target, "get", List.of(index), span, id);
  }

  @Override
  public void setProperty(
      Object target, String propertyName, EvaluationValue value, SourceSpan span, TemplateId id) {
    if (target == null) {
      throw new TemplateRenderException(
          "Cannot write property '" + propertyName + "' to null target",
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    if (target instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> rawMap = (Map<Object, Object>) map;
      rawMap.put(propertyName, value.asObjectOrNull());
      return;
    }

    Class<?> clazz = target.getClass();
    String setterName = "set" + capitalize(propertyName);
    Method setter = null;
    for (Method m : clazz.getMethods()) {
      if ((m.getName().equals(setterName) || m.getName().equalsIgnoreCase("set" + propertyName))
          && Modifier.isPublic(m.getModifiers())
          && m.getParameterCount() == 1) {
        setter = m;
        break;
      }
    }

    if (setter != null) {
      if (!securityPolicy.isMethodPermitted(clazz, setter)) {
        throw new TemplateSecurityException(
            "Invocation of setter " + setter.getName() + " on " + clazz.getName() + " is denied",
            id,
            span,
            InterpreterDiagnosticCodes.SECURITY_VIOLATION);
      }
      try {
        Object arg = convertArgument(value, setter.getParameterTypes()[0], span, id);
        setter.invoke(target, arg);
        return;
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        throw new TemplateRenderException(
            "Setter '" + setterName + "' failed: " + cause.getMessage(),
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR,
            cause);
      } catch (IllegalAccessException e) {
        throw new TemplateRenderException(
            "Cannot access setter: " + setterName,
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR,
            e);
      }
    }

    Field field = findPublicField(clazz, propertyName);
    if (field != null) {
      if (!securityPolicy.isFieldPermitted(clazz, field)) {
        throw new TemplateSecurityException(
            "Access to field " + field.getName() + " is denied",
            id,
            span,
            InterpreterDiagnosticCodes.SECURITY_VIOLATION);
      }
      try {
        field.set(target, convertArgument(value, field.getType(), span, id));
        return;
      } catch (IllegalAccessException e) {
        throw new TemplateRenderException(
            "Cannot set field: " + propertyName,
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR,
            e);
      }
    }

    throw new TemplateRenderException(
        "No writable property or setter found for '" + propertyName + "' on " + clazz.getName(),
        id,
        span,
        InterpreterDiagnosticCodes.SYNTAX_ERROR);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setIndex(
      Object target, EvaluationValue index, EvaluationValue value, SourceSpan span, TemplateId id) {
    if (target == null) {
      throw new TemplateRenderException(
          "Cannot set index on null target", id, span, InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }

    if (target instanceof List<?> list) {
      int idx = toIntegerIndex(index, span, id);
      if (idx < 0 || idx >= list.size()) {
        throw new TemplateRenderException(
            "Index out of bounds: " + idx + " (list size: " + list.size() + ")",
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      ((List<Object>) list).set(idx, value.asObjectOrNull());
      return;
    }

    if (target.getClass().isArray()) {
      int idx = toIntegerIndex(index, span, id);
      int length = Array.getLength(target);
      if (idx < 0 || idx >= length) {
        throw new TemplateRenderException(
            "Array index out of bounds: " + idx + " (array length: " + length + ")",
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      Array.set(
          target, idx, convertArgument(value, target.getClass().getComponentType(), span, id));
      return;
    }

    if (target instanceof Map<?, ?> map) {
      ((Map<Object, Object>) map).put(index.asObjectOrNull(), value.asObjectOrNull());
      return;
    }

    throw new TemplateRenderException(
        "Target does not support index assignment: " + target.getClass().getName(),
        id,
        span,
        InterpreterDiagnosticCodes.SYNTAX_ERROR);
  }

  private EvaluationValue invokeGetter(Object target, Method m, SourceSpan span, TemplateId id) {
    if (!securityPolicy.isMethodPermitted(target.getClass(), m)) {
      throw new TemplateSecurityException(
          "Invocation of getter "
              + m.getName()
              + " on "
              + target.getClass().getName()
              + " is denied by security policy",
          id,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }
    try {
      return EvaluationValue.of(m.invoke(target));
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof ControlSignal cs) {
        throw cs;
      }
      throw new TemplateRenderException(
          "Getter '" + m.getName() + "' threw an exception: " + cause.getMessage(),
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR,
          cause);
    } catch (IllegalAccessException e) {
      throw new TemplateRenderException(
          "Cannot access getter: " + m.getName(),
          id,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR,
          e);
    }
  }

  private int scoreCandidate(Method method, List<EvaluationValue> args) {
    Class<?>[] params = method.getParameterTypes();
    int score = 0;
    for (int i = 0; i < params.length; i++) {
      Class<?> paramType = params[i];
      EvaluationValue arg = args.get(i);
      if (arg.isNull() || arg.isUndefined()) {
        if (paramType.isPrimitive()) {
          return -1; // cannot pass null to primitive
        }
        score += 10;
      } else {
        Object val = arg.value();
        Class<?> valClass = val.getClass();
        if (paramType == valClass) {
          score += 0;
        } else if (boxType(paramType) == valClass) {
          score += 1;
        } else if (paramType.isAssignableFrom(valClass)) {
          score += 2 + Math.min(getInheritanceDistance(paramType, valClass), 20);
        } else if (isNumericType(paramType) && isNumericType(valClass)) {
          score += 50;
        } else if ((paramType == String.class || paramType == CharSequence.class)
            && !(val instanceof Map<?, ?>)) {
          score += 80;
        } else {
          return -1; // incompatible
        }
      }
    }
    return score;
  }

  private static int getInheritanceDistance(Class<?> target, Class<?> sub) {
    if (target == sub) {
      return 0;
    }
    if (target.isInterface()) {
      return getInterfaceDistance(target, sub);
    }
    int distance = 0;
    Class<?> curr = sub;
    while (curr != null && curr != target) {
      distance++;
      curr = curr.getSuperclass();
    }
    return (curr == target) ? distance : 20;
  }

  private static int getInterfaceDistance(Class<?> targetInterface, Class<?> cls) {
    if (cls == null) {
      return 50;
    }
    for (Class<?> iface : cls.getInterfaces()) {
      if (iface == targetInterface) {
        return 1;
      }
      int d = getInterfaceDistance(targetInterface, iface);
      if (d < 50) {
        return d + 1;
      }
    }
    return getInterfaceDistance(targetInterface, cls.getSuperclass()) + 1;
  }

  private Object convertArgument(
      EvaluationValue arg, Class<?> targetType, SourceSpan span, TemplateId id) {
    if (arg.isNull() || arg.isUndefined()) {
      if (targetType.isPrimitive()) {
        throw new TemplateRenderException(
            "Cannot pass null to primitive parameter " + targetType.getName(),
            id,
            span,
            InterpreterDiagnosticCodes.SYNTAX_ERROR);
      }
      return null;
    }

    Object val = arg.value();
    if (targetType.isInstance(val)) {
      return val;
    }

    Class<?> boxedTarget = boxType(targetType);
    if (boxedTarget.isInstance(val)) {
      return val;
    }

    if (val instanceof Number num) {
      if (boxedTarget == Integer.class) return num.intValue();
      if (boxedTarget == Long.class) return num.longValue();
      if (boxedTarget == Double.class) return num.doubleValue();
      if (boxedTarget == Float.class) return num.floatValue();
      if (boxedTarget == Short.class) return num.shortValue();
      if (boxedTarget == Byte.class) return num.byteValue();
      if (boxedTarget == BigDecimal.class) return VtlNumericOperations.toBigDecimal(num);
      if (boxedTarget == BigInteger.class) return VtlNumericOperations.toBigInteger(num);
    }

    if (targetType == String.class || targetType == CharSequence.class) {
      return String.valueOf(val);
    }

    return val;
  }

  private int toIntegerIndex(EvaluationValue index, SourceSpan span, TemplateId id) {
    if (index.isDefined() && index.value() instanceof Number n) {
      return n.intValue();
    }
    throw new TemplateRenderException(
        "Index must evaluate to a number, but was: " + index,
        id,
        span,
        InterpreterDiagnosticCodes.SYNTAX_ERROR);
  }

  private static Method findPublicZeroArgMethod(Class<?> clazz, String name) {
    try {
      Method m = clazz.getMethod(name);
      return (m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers())) ? m : null;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static Field findPublicField(Class<?> clazz, String name) {
    try {
      Field f = clazz.getField(name);
      return Modifier.isPublic(f.getModifiers()) ? f : null;
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static boolean isNumericType(Class<?> clazz) {
    return Number.class.isAssignableFrom(boxType(clazz));
  }

  private static Class<?> boxType(Class<?> type) {
    if (!type.isPrimitive()) return type;
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
