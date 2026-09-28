package io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
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

    for (Method method : clazz.getMethods()) {
      if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      if (!policy.isMethodPermitted(clazz, method)
          || !policy.isClassPermitted(method.getReturnType())) {
        continue;
      }
      candidateNames.add(method.getName());
      if (!method.getName().equals(methodName)) {
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

    if (argType instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> argClass = ct.javaClass().get();
      if (paramType.isAssignableFrom(argClass)) {
        if (paramType == argClass) {
          return 10;
        }
        return 6;
      }
    }

    if (argType instanceof VType.ArrayType at && paramType.isArray()) {
      return 7;
    }

    return -1;
  }

  private record MethodScore(Method method, int score) {}
}
