package io.github.minh124199.viettemplate.language.vtl.internal.semantics.resolve;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of resolving a method invocation on a receiver type. */
public record MethodResolution(
    Kind kind,
    VType returnType,
    Optional<Method> targetMethod,
    Optional<String> diagnosticMessage,
    Optional<String> typoSuggestion,
    int candidateCount,
    List<Integer> candidateArities,
    int incompatibleArgIndex) {

  public enum Kind {
    RESOLVED,
    DENIED_BY_POLICY,
    METHOD_NOT_FOUND,
    ARITY_MISMATCH,
    INCOMPATIBLE_ARGUMENTS,
    DYNAMIC
  }

  public MethodResolution {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(returnType, "returnType must not be null");
    Objects.requireNonNull(targetMethod, "targetMethod must not be null");
    Objects.requireNonNull(diagnosticMessage, "diagnosticMessage must not be null");
    Objects.requireNonNull(typoSuggestion, "typoSuggestion must not be null");
    candidateArities = candidateArities != null ? List.copyOf(candidateArities) : List.of();
  }

  public MethodResolution(
      Kind kind,
      VType returnType,
      Optional<Method> targetMethod,
      Optional<String> diagnosticMessage,
      Optional<String> typoSuggestion,
      int candidateCount) {
    this(
        kind,
        returnType,
        targetMethod,
        diagnosticMessage,
        typoSuggestion,
        candidateCount,
        List.of(),
        -1);
  }

  public MethodResolution(
      Kind kind,
      VType returnType,
      Optional<Method> targetMethod,
      Optional<String> diagnosticMessage,
      Optional<String> typoSuggestion) {
    this(
        kind,
        returnType,
        targetMethod,
        diagnosticMessage,
        typoSuggestion,
        kind == Kind.RESOLVED ? 1 : 0,
        List.of(),
        -1);
  }

  public static MethodResolution resolved(VType returnType, Method targetMethod) {
    return resolved(returnType, targetMethod, 1);
  }

  public static MethodResolution resolved(
      VType returnType, Method targetMethod, int candidateCount) {
    return new MethodResolution(
        Kind.RESOLVED,
        returnType,
        Optional.of(targetMethod),
        Optional.empty(),
        Optional.empty(),
        candidateCount,
        List.of(),
        -1);
  }

  public static MethodResolution dynamic(VType returnType) {
    return new MethodResolution(
        Kind.DYNAMIC,
        returnType,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        0,
        List.of(),
        -1);
  }

  public static MethodResolution denied(String reason) {
    return new MethodResolution(
        Kind.DENIED_BY_POLICY,
        VTypes.ERROR,
        Optional.empty(),
        Optional.of(reason),
        Optional.empty(),
        0,
        List.of(),
        -1);
  }

  public static MethodResolution notFound(VType errorType, Optional<String> suggestion) {
    return new MethodResolution(
        Kind.METHOD_NOT_FOUND,
        errorType,
        Optional.empty(),
        Optional.empty(),
        suggestion,
        0,
        List.of(),
        -1);
  }

  public static MethodResolution arityMismatch(String message, List<Integer> candidateArities) {
    return new MethodResolution(
        Kind.ARITY_MISMATCH,
        VTypes.ERROR,
        Optional.empty(),
        Optional.of(message),
        Optional.empty(),
        candidateArities.size(),
        candidateArities,
        -1);
  }

  public static MethodResolution incompatibleArguments(String message, int incompatibleArgIndex) {
    return new MethodResolution(
        Kind.INCOMPATIBLE_ARGUMENTS,
        VTypes.ERROR,
        Optional.empty(),
        Optional.of(message),
        Optional.empty(),
        0,
        List.of(),
        incompatibleArgIndex);
  }

  public boolean isResolved() {
    return kind == Kind.RESOLVED;
  }

  /**
   * Returns {@code true} if this resolution represents a specialization-stable method call. Under
   * Viet Template semantics, a method call is specialization-stable if and only if exactly one
   * permitted candidate exists for the target name and arity on the receiver class, guaranteeing
   * that direct bytecode invocation and dynamic linker resolution select the exact same target
   * method under all runtime subtypes and values.
   */
  public boolean isSpecializationStable() {
    return isResolved() && targetMethod.isPresent() && candidateCount == 1;
  }
}
