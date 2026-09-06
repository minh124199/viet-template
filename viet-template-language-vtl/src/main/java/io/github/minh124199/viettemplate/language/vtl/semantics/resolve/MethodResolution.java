package io.github.minh124199.viettemplate.language.vtl.semantics.resolve;

import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

/** Result of resolving a method invocation on a receiver type. */
public record MethodResolution(
    Kind kind,
    VType returnType,
    Optional<Method> targetMethod,
    Optional<String> diagnosticMessage,
    Optional<String> typoSuggestion) {

  public enum Kind {
    RESOLVED,
    DENIED_BY_POLICY,
    METHOD_NOT_FOUND,
    DYNAMIC
  }

  public MethodResolution {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(returnType, "returnType must not be null");
    Objects.requireNonNull(targetMethod, "targetMethod must not be null");
    Objects.requireNonNull(diagnosticMessage, "diagnosticMessage must not be null");
    Objects.requireNonNull(typoSuggestion, "typoSuggestion must not be null");
  }

  public static MethodResolution resolved(VType returnType, Method targetMethod) {
    return new MethodResolution(
        Kind.RESOLVED, returnType, Optional.of(targetMethod), Optional.empty(), Optional.empty());
  }

  public static MethodResolution dynamic(VType returnType) {
    return new MethodResolution(
        Kind.DYNAMIC, returnType, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static MethodResolution denied(String reason) {
    return new MethodResolution(
        Kind.DENIED_BY_POLICY,
        VTypes.ERROR,
        Optional.empty(),
        Optional.of(reason),
        Optional.empty());
  }

  public static MethodResolution notFound(VType errorType, Optional<String> suggestion) {
    return new MethodResolution(
        Kind.METHOD_NOT_FOUND, errorType, Optional.empty(), Optional.empty(), suggestion);
  }

  public boolean isResolved() {
    return kind == Kind.RESOLVED;
  }
}
