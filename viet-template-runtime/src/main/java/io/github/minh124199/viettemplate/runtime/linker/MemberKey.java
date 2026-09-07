package io.github.minh124199.viettemplate.runtime.linker;

import java.util.Objects;

/** Key identifying a dynamic member access by its operation, member name, and arity. */
public record MemberKey(MemberOperation operation, String name, int arity) {

  public MemberKey {
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(name, "name must not be null");
    if (arity < 0) {
      throw new IllegalArgumentException("arity must not be negative: " + arity);
    }
  }

  /** Creates a property read key with arity 0. */
  public static MemberKey propertyGet(String propertyName) {
    return new MemberKey(MemberOperation.PROPERTY_GET, propertyName, 0);
  }

  /** Creates a property write key with arity 1. */
  public static MemberKey propertySet(String propertyName) {
    return new MemberKey(MemberOperation.PROPERTY_SET, propertyName, 1);
  }

  /** Creates a method invocation key with the specified arity. */
  public static MemberKey methodCall(String methodName, int arity) {
    return new MemberKey(MemberOperation.METHOD_CALL, methodName, arity);
  }

  /** Creates an indexed read key with arity 1. */
  public static MemberKey indexGet() {
    return new MemberKey(MemberOperation.INDEX_GET, "[]", 1);
  }

  /** Creates an indexed write key with arity 2. */
  public static MemberKey indexSet() {
    return new MemberKey(MemberOperation.INDEX_SET, "[]", 2);
  }
}
