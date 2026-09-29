package io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves method calls against receiver types with overload scoring and security validation. */
public final class MethodResolver {

  private MethodResolver() {}

  public static MethodResolution resolveMethod(
      VType receiverType, String methodName, List<VType> argumentTypes) {
    return resolveMethod(receiverType, methodName, argumentTypes, MemberAccessPolicy.standard());
  }

  public static MethodResolution resolveMethod(
      VType receiverType, String methodName, List<VType> argumentTypes, MemberAccessPolicy policy) {
    Objects.requireNonNull(receiverType, "receiverType must not be null");
    Objects.requireNonNull(methodName, "methodName must not be null");
    Objects.requireNonNull(argumentTypes, "argumentTypes must not be null");
    MemberAccessPolicy effectivePolicy = policy != null ? policy : MemberAccessPolicy.standard();

    if (receiverType instanceof VType.DynamicType) {
      return MethodResolution.dynamic(VTypes.DYNAMIC);
    }
    if (receiverType instanceof VType.ErrorType) {
      return MethodResolution.notFound(VTypes.ERROR, Optional.empty());
    }

    if (receiverType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> clazz = ct.javaClass().get();
      return resolveClassMethod(clazz, methodName, argumentTypes, effectivePolicy);
    }

    return MethodResolution.notFound(VTypes.ERROR, Optional.empty());
  }

  private static MethodResolution resolveClassMethod(
      Class<?> clazz, String methodName, List<VType> argumentTypes, MemberAccessPolicy policy) {
    if (argumentTypes.stream().anyMatch(t -> t instanceof VType.ErrorType)) {
      return MethodResolution.notFound(VTypes.ERROR, Optional.empty());
    }

    // 1. Security Check
    if (!policy.isClassPermitted(clazz)) {
      return MethodResolution.denied(
          "Access to class " + clazz.getName() + " is denied by security policy");
    }
    if (!policy.isMethodPermitted(clazz, methodName, argumentTypes.size())) {
      return MethodResolution.denied("Method " + methodName + " is denied by security policy");
    }

    // 2. Overload Matching & Scoring
    List<MethodScore> matches = new ArrayList<>();
    Set<String> candidateNames = new LinkedHashSet<>();
    List<Method> candidates = new ArrayList<>();
    Set<Integer> candidateArities = new LinkedHashSet<>();
    int permittedMatchingArityMethods = 0;

    for (Method method : clazz.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      if (method.isBridge() || method.isSynthetic()) {
        continue;
      }
      if (!policy.isMethodPermitted(clazz, method)
          || !policy.isClassPermitted(method.getReturnType())) {
        continue;
      }
      candidateNames.add(method.getName());
      if (method.getName().equals(methodName)) {
        candidateArities.add(method.getParameterCount());
        if (method.getParameterCount() == argumentTypes.size()) {
          permittedMatchingArityMethods++;
          if (!method.isVarArgs()) {
            candidates.add(method);
          }
        }
      }
    }

    if (!candidateNames.contains(methodName)) {
      Optional<String> suggestion =
          LevenshteinDistance.findClosestMatch(methodName, candidateNames);
      return MethodResolution.notFound(VTypes.ERROR, suggestion);
    }

    if (permittedMatchingArityMethods == 0) {
      List<Integer> arities = candidateArities.stream().sorted().toList();
      String msg =
          "Method '"
              + methodName
              + "' on type "
              + clazz.getName()
              + " expects "
              + arities
              + " argument(s), but was called with "
              + argumentTypes.size();
      return MethodResolution.arityMismatch(msg, arities);
    }

    boolean hasDynamicArg = argumentTypes.stream().anyMatch(t -> t instanceof VType.DynamicType);
    if (hasDynamicArg && candidates.size() > 1) {
      return MethodResolution.dynamic(VTypes.DYNAMIC);
    }

    for (Method method : candidates) {
      int score = scoreMethodArguments(method, argumentTypes);
      if (score >= 0) {
        matches.add(new MethodScore(method, score));
      }
    }

    if (!matches.isEmpty()) {
      matches.sort((a, b) -> Integer.compare(a.score, b.score));
      if (matches.size() > 1 && matches.get(0).score == matches.get(1).score) {
        // Ambiguous overload match: fall back to dynamic resolution
        return MethodResolution.dynamic(VTypes.DYNAMIC);
      }

      Method bestMethod = matches.get(0).method;
      Optional<Method> publicMethod = findPublicMethod(bestMethod, clazz);
      if (publicMethod.isEmpty()) {
        return MethodResolution.dynamic(VTypes.DYNAMIC);
      }
      Method targetMethod = publicMethod.get();
      VType returnType =
          targetMethod.getReturnType() == void.class
              ? VTypes.DYNAMIC
              : VTypes.fromJavaType(targetMethod.getGenericReturnType(), Nullability.UNKNOWN);

      boolean hasCharWidening = false;
      Class<?>[] ptypes = targetMethod.getParameterTypes();
      for (int i = 0; i < argumentTypes.size(); i++) {
        VType at = argumentTypes.get(i);
        Class<?> pt = ptypes[i];
        if (pt.isPrimitive() && pt != char.class) {
          if (at instanceof VType.PrimitiveType prim
              && prim.kind().primitiveClass() == char.class) {
            hasCharWidening = true;
            break;
          }
          if (at instanceof VType.ClassType ct
              && ct.javaClass().isPresent()
              && ct.javaClass().get() == Character.class) {
            hasCharWidening = true;
            break;
          }
        }
      }
      if (hasCharWidening) {
        return MethodResolution.dynamic(returnType);
      }

      int totalCandidates = Math.max(candidates.size(), permittedMatchingArityMethods);
      return MethodResolution.resolved(returnType, targetMethod, totalCandidates);
    }

    if (!candidates.isEmpty()) {
      int incompatibleArgIndex = -1;
      for (int i = 0; i < argumentTypes.size(); i++) {
        final int argIdx = i;
        VType argType = argumentTypes.get(argIdx);
        boolean allFail =
            candidates.stream()
                .allMatch(m -> scoreArgument(m.getParameterTypes()[argIdx], argType) < 0);
        if (allFail) {
          incompatibleArgIndex = argIdx;
          break;
        }
      }
      String msg =
          "Method '"
              + methodName
              + "' on type "
              + clazz.getName()
              + " cannot be called with argument types "
              + argumentTypes.stream().map(VType::typeName).toList();
      return MethodResolution.incompatibleArguments(msg, incompatibleArgIndex);
    }

    return MethodResolution.dynamic(VTypes.DYNAMIC);
  }

  public static Optional<Method> findPublicMethod(Method method, Class<?> targetClass) {
    if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
      return Optional.of(method);
    }
    if (Modifier.isPublic(targetClass.getModifiers())) {
      try {
        Method m = targetClass.getMethod(method.getName(), method.getParameterTypes());
        if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
          return Optional.of(m);
        }
      } catch (NoSuchMethodException ignored) {
      }
    }
    for (Class<?> iface : targetClass.getInterfaces()) {
      Optional<Method> m = findPublicMethod(method, iface);
      if (m.isPresent()) {
        return m;
      }
    }
    Class<?> superclass = targetClass.getSuperclass();
    if (superclass != null && superclass != Object.class) {
      Optional<Method> m = findPublicMethod(method, superclass);
      if (m.isPresent()) {
        return m;
      }
    }
    return Optional.empty();
  }

  private static int scoreMethodArguments(Method method, List<VType> argumentTypes) {
    Class<?>[] paramTypes = method.getParameterTypes();
    if (paramTypes.length != argumentTypes.size()) {
      return -1;
    }

    int totalScore = 0;
    for (int i = 0; i < paramTypes.length; i++) {
      Class<?> paramType = paramTypes[i];
      VType argType = argumentTypes.get(i);

      int argScore = scoreArgument(paramType, argType);
      if (argScore < 0) {
        return -1;
      }
      totalScore += argScore;
    }
    return totalScore;
  }

  private static int scoreArgument(Class<?> paramType, VType argType) {
    if (argType instanceof VType.DynamicType) {
      return 100;
    }
    if (argType instanceof VType.ErrorType) {
      return -1;
    }

    if (argType instanceof VType.NullType) {
      if (paramType.isPrimitive()) {
        return -1;
      }
      return 10;
    }

    if (paramType.isPrimitive()) {
      if (argType instanceof VType.PrimitiveType pt) {
        Class<?> primArg = pt.kind().primitiveClass();
        if (primArg == paramType) {
          return 0;
        }
        if (isPermittedPrimitiveWidening(primArg, paramType)) {
          return 2 + wideningDistance(primArg, paramType);
        }
        return -1;
      }
      if (argType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
        Class<?> argClass = ct.javaClass().get();
        if (boxType(paramType) == argClass) {
          return 1;
        }
        Class<?> unboxed = unboxType(argClass);
        if (unboxed != null && isPermittedPrimitiveWidening(unboxed, paramType)) {
          return 3 + wideningDistance(unboxed, paramType);
        }
        return -1;
      }
      return -1;
    }

    if (argType instanceof VType.PrimitiveType pt) {
      Class<?> boxed = pt.kind().boxedClass();
      if (paramType == boxed) {
        return 1;
      }
      if (paramType.isAssignableFrom(boxed)) {
        return 2 + Math.min(getInheritanceDistance(paramType, boxed), 20);
      }
      if (paramType == Object.class) {
        return 15;
      }
      return -1;
    }

    if (argType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> argClass = ct.javaClass().get();
      if (paramType == argClass) {
        return 0;
      }
      if (paramType.isAssignableFrom(argClass)) {
        return 2 + Math.min(getInheritanceDistance(paramType, argClass), 20);
      }
      if (paramType == Object.class) {
        return 15;
      }
      return -1;
    }

    if (argType instanceof VType.ArrayType && paramType.isArray()) {
      return 2;
    }

    if (paramType == Object.class) {
      return 15;
    }

    return -1;
  }

  private static boolean isPermittedPrimitiveWidening(Class<?> from, Class<?> to) {
    if (from == byte.class) {
      return to == short.class
          || to == int.class
          || to == long.class
          || to == float.class
          || to == double.class;
    }
    if (from == short.class) {
      return to == int.class || to == long.class || to == float.class || to == double.class;
    }
    if (from == char.class) {
      return to == int.class || to == long.class || to == float.class || to == double.class;
    }
    if (from == int.class) {
      return to == long.class || to == float.class || to == double.class;
    }
    if (from == long.class) {
      return to == float.class || to == double.class;
    }
    if (from == float.class) {
      return to == double.class;
    }
    return false;
  }

  private static int wideningDistance(Class<?> from, Class<?> to) {
    if (from == byte.class) {
      if (to == short.class) return 1;
      if (to == int.class) return 2;
      if (to == long.class) return 3;
      if (to == float.class) return 4;
      if (to == double.class) return 5;
    }
    if (from == short.class) {
      if (to == int.class) return 1;
      if (to == long.class) return 2;
      if (to == float.class) return 3;
      if (to == double.class) return 4;
    }
    if (from == char.class) {
      if (to == int.class) return 1;
      if (to == long.class) return 2;
      if (to == float.class) return 3;
      if (to == double.class) return 4;
    }
    if (from == int.class) {
      if (to == long.class) return 1;
      if (to == float.class) return 2;
      if (to == double.class) return 3;
    }
    if (from == long.class) {
      if (to == float.class) return 1;
      if (to == double.class) return 2;
    }
    if (from == float.class) {
      if (to == double.class) return 1;
    }
    return 10;
  }

  private static Class<?> unboxType(Class<?> type) {
    if (type == Integer.class) return int.class;
    if (type == Long.class) return long.class;
    if (type == Double.class) return double.class;
    if (type == Float.class) return float.class;
    if (type == Short.class) return short.class;
    if (type == Byte.class) return byte.class;
    if (type == Boolean.class) return boolean.class;
    if (type == Character.class) return char.class;
    return null;
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

  private record MethodScore(Method method, int score) {}
}
