package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
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

  private static final Set<String> DENIED_CLASS_PREFIXES =
      Set.of("java.lang.reflect.", "java.lang.invoke.", "java.security.", "sun.", "jdk.internal.");

  private static final Set<Class<?>> DENIED_CLASSES =
      Set.of(
          Class.class,
          ClassLoader.class,
          Module.class,
          Runtime.class,
          ProcessBuilder.class,
          Process.class,
          Thread.class,
          ThreadGroup.class,
          System.class);

  private static final Set<String> DENIED_METHOD_NAMES =
      Set.of("getClass", "wait", "notify", "notifyAll");

  private MethodResolver() {}

  public static MethodResolution resolveMethod(
      VType receiverType, String methodName, List<VType> argumentTypes) {
    Objects.requireNonNull(receiverType, "receiverType must not be null");
    Objects.requireNonNull(methodName, "methodName must not be null");
    Objects.requireNonNull(argumentTypes, "argumentTypes must not be null");

    if (receiverType instanceof VType.DynamicType) {
      return MethodResolution.dynamic(VTypes.DYNAMIC);
    }
    if (receiverType instanceof VType.ErrorType) {
      return MethodResolution.notFound(VTypes.ERROR, Optional.empty());
    }

    if (receiverType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> clazz = ct.javaClass().get();
      return resolveClassMethod(clazz, methodName, argumentTypes);
    }

    return MethodResolution.notFound(VTypes.ERROR, Optional.empty());
  }

  private static MethodResolution resolveClassMethod(
      Class<?> clazz, String methodName, List<VType> argumentTypes) {
    // 1. Security Check
    if (isClassDenied(clazz)) {
      return MethodResolution.denied(
          "Access to class " + clazz.getName() + " is denied by security policy");
    }
    if (DENIED_METHOD_NAMES.contains(methodName)) {
      return MethodResolution.denied("Method " + methodName + " is denied by security policy");
    }

    // 2. Overload Matching & Scoring
    List<MethodScore> matches = new ArrayList<>();
    Set<String> candidateNames = new LinkedHashSet<>();

    for (Method method : clazz.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      candidateNames.add(method.getName());
      if (!method.getName().equals(methodName)) {
        continue;
      }
      if (isMethodDenied(method)) {
        continue;
      }

      int score = scoreMethodArguments(method, argumentTypes);
      if (score >= 0) {
        matches.add(new MethodScore(method, score));
      }
    }

    if (!matches.isEmpty()) {
      matches.sort((a, b) -> Integer.compare(b.score, a.score));
      Method bestMethod = matches.get(0).method;
      VType returnType =
          bestMethod.getReturnType() == void.class
              ? VTypes.DYNAMIC
              : VTypes.fromJavaType(bestMethod.getGenericReturnType(), Nullability.NULLABLE);
      return MethodResolution.resolved(returnType, bestMethod);
    }

    // Typo suggestion
    Optional<String> suggestion = LevenshteinDistance.findClosestMatch(methodName, candidateNames);
    return MethodResolution.notFound(VTypes.ERROR, suggestion);
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
    if (argType instanceof VType.DynamicType || argType instanceof VType.ErrorType) {
      return 1;
    }

    if (paramType.isPrimitive()) {
      if (argType instanceof VType.PrimitiveType pt) {
        if (pt.kind().primitiveClass() == paramType) {
          return 10;
        }
        if (pt.isAssignableTo(new VType.PrimitiveType(PrimitiveKind.fromClass(paramType)))) {
          return 7;
        }
      }
      if (argType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
        if (PrimitiveKind.isPrimitiveOrBoxed(ct.javaClass().get())) {
          return 5;
        }
      }
      return -1;
    }

    if (argType instanceof VType.NullType) {
      return 8;
    }

    if (argType instanceof VType.PrimitiveType pt) {
      if (paramType.isAssignableFrom(pt.kind().boxedClass())) {
        return 6;
      }
      if (paramType == Object.class || paramType == Number.class) {
        return 4;
      }
      return -1;
    }

    if (argType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> argClass = ct.javaClass().get();
      if (paramType == argClass) {
        return 10;
      }
      if (paramType.isAssignableFrom(argClass)) {
        return 8;
      }
      return -1;
    }

    return 2;
  }

  private static boolean isClassDenied(Class<?> clazz) {
    if (clazz == null) {
      return true;
    }
    if (DENIED_CLASSES.contains(clazz)) {
      return true;
    }
    String name = clazz.getName();
    for (String prefix : DENIED_CLASS_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isMethodDenied(Method method) {
    if (DENIED_METHOD_NAMES.contains(method.getName())) {
      return true;
    }
    return isClassDenied(method.getDeclaringClass());
  }

  private record MethodScore(Method method, int score) {}
}
