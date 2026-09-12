package io.github.minh124199.viettemplate.runtime.linker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Access policy governing dynamic linkage of classes, methods, and fields.
 *
 * <p>Access policies serve as inputs to linkage. Each policy provides a {@link #policyId()} so that
 * cached linkage decisions are strictly isolated and unapproved member targets are never accessible
 * through cache reuse across differing security contexts.
 */
public interface LinkerAccessPolicy {

  /**
   * Returns a stable identifier for this policy used to partition inline caches.
   *
   * @return the policy identifier
   */
  String policyId();

  /** Checks whether the given class is permitted for dynamic linkage. */
  boolean isClassPermitted(Class<?> clazz);

  /** Checks whether invocation of the given method on a receiver class is permitted. */
  boolean isMethodPermitted(Class<?> receiverClass, Method method);

  /** Checks whether invocation of the given method is permitted on its declaring class. */
  default boolean isMethodPermitted(Method method) {
    return method != null && isMethodPermitted(method.getDeclaringClass(), method);
  }

  /** Checks whether access to the given field on a receiver class is permitted. */
  boolean isFieldPermitted(Class<?> receiverClass, Field field);

  /** Checks whether access to the given field is permitted on its declaring class. */
  default boolean isFieldPermitted(Field field) {
    return field != null && isFieldPermitted(field.getDeclaringClass(), field);
  }

  /** Checks whether access to a property on the receiver class is permitted. */
  default boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return isClassPermitted(receiverClass);
  }

  /** Checks whether property mutation on the receiver class is permitted. */
  default boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return isClassPermitted(receiverClass);
  }

  /** Checks whether index mutation on the receiver class is permitted. */
  default boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return isClassPermitted(receiverClass);
  }

  /**
   * Checks whether invoking the given method as a property reader (getter, record accessor, or
   * zero-arg property match) is permitted.
   */
  default boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return isMethodPermitted(receiverClass, method);
  }

  /** Returns whether this policy enforces the strict safe-allowlist sandbox profile. */
  default boolean isSafeProfile() {
    return false;
  }

  /** Returns the default standard security policy. */
  static LinkerAccessPolicy standard() {
    return StandardLinkerAccessPolicy.INSTANCE;
  }

  /** Returns a policy that denies all accesses. */
  static LinkerAccessPolicy denyAll() {
    return DenyAllLinkerAccessPolicy.INSTANCE;
  }

  /**
   * Wraps a high-level {@link io.github.minh124199.viettemplate.api.MemberAccessPolicy} as a {@link
   * LinkerAccessPolicy}.
   */
  static LinkerAccessPolicy of(io.github.minh124199.viettemplate.api.MemberAccessPolicy policy) {
    java.util.Objects.requireNonNull(policy, "policy must not be null");
    return new MemberAccessPolicyLinkerAdapter(policy);
  }
}

final class StandardLinkerAccessPolicy implements LinkerAccessPolicy {

  static final StandardLinkerAccessPolicy INSTANCE = new StandardLinkerAccessPolicy();

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

  private StandardLinkerAccessPolicy() {}

  @Override
  public String policyId() {
    return "standard";
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    if (DENIED_CLASSES.contains(clazz)) {
      return false;
    }
    String name = clazz.getName();
    for (String prefix : DENIED_CLASS_PREFIXES) {
      if (name.startsWith(prefix)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    if (method == null
        || !isClassPermitted(receiverClass)
        || !isClassPermitted(method.getDeclaringClass())) {
      return false;
    }
    return !DENIED_METHOD_NAMES.contains(method.getName());
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    if (field == null
        || !isClassPermitted(receiverClass)
        || !isClassPermitted(field.getDeclaringClass())) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    if (propertyName == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    if (propertyName.equalsIgnoreCase("class")
        && !java.util.Map.class.isAssignableFrom(receiverClass)) {
      return false;
    }
    return true;
  }
}

final class DenyAllLinkerAccessPolicy implements LinkerAccessPolicy {

  static final DenyAllLinkerAccessPolicy INSTANCE = new DenyAllLinkerAccessPolicy();

  private DenyAllLinkerAccessPolicy() {}

  @Override
  public String policyId() {
    return "deny-all";
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return false;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return false;
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return false;
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return false;
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return false;
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return false;
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return false;
  }
}

final class MemberAccessPolicyLinkerAdapter implements LinkerAccessPolicy {
  private final io.github.minh124199.viettemplate.api.MemberAccessPolicy policy;
  private final String policyId;

  MemberAccessPolicyLinkerAdapter(io.github.minh124199.viettemplate.api.MemberAccessPolicy policy) {
    this.policy = java.util.Objects.requireNonNull(policy, "policy must not be null");
    this.policyId = policy.policyFingerprint();
  }

  @Override
  public String policyId() {
    return policyId;
  }

  @Override
  public boolean isSafeProfile() {
    return policy.isSafeProfile();
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return policy.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return policy.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return policy.isFieldPermitted(receiverClass, field);
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return policy.isPropertyPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return policy.isPropertyMutationPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return policy.isIndexMutationPermitted(receiverClass);
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return policy.isPropertyMethodPermitted(receiverClass, method, propertyName);
  }
}
