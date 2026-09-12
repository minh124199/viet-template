package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Security policy governing reflection, property access, and method invocations in the interpreter.
 */
public interface VtlSecurityPolicy {

  boolean isClassPermitted(Class<?> clazz);

  boolean isMethodPermitted(Class<?> receiverClass, Method method);

  boolean isFieldPermitted(Class<?> receiverClass, Field field);

  default boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return isClassPermitted(receiverClass);
  }

  default boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return isClassPermitted(receiverClass);
  }

  default boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return isClassPermitted(receiverClass);
  }

  default boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return isMethodPermitted(receiverClass, method);
  }

  default String policyFingerprint() {
    return "standard";
  }

  default boolean isSafeProfile() {
    return false;
  }

  default LinkerAccessPolicy toLinkerAccessPolicy() {
    return LinkerAccessPolicy.standard();
  }

  static VtlSecurityPolicy standard() {
    return StandardSecurityPolicy.INSTANCE;
  }

  static VtlSecurityPolicy safe() {
    return SafeSecurityPolicy.INSTANCE;
  }

  static VtlSecurityPolicy of(MemberAccessPolicy policy) {
    Objects.requireNonNull(policy, "policy must not be null");
    return new MemberAccessPolicyVtlAdapter(policy);
  }

  /**
   * Enforces safe profile policy invariants. If the supplied policy is null or standard, {@link
   * #safe()} is returned. If a custom policy is supplied, it is composed with {@link #safe()} such
   * that it can only further restrict, never weaken, safe profile guarantees.
   */
  static VtlSecurityPolicy enforceSafe(VtlSecurityPolicy policy) {
    if (policy == null || policy == StandardSecurityPolicy.INSTANCE) {
      return SafeSecurityPolicy.INSTANCE;
    }
    if (policy instanceof SafeSecurityPolicy) {
      return policy;
    }
    if (policy instanceof MemberAccessPolicyVtlAdapter adapter) {
      if (adapter.policy() == MemberAccessPolicy.safe()) {
        return SafeSecurityPolicy.INSTANCE;
      }
      MemberAccessPolicy safeMap = adapter.policy().toSafeProfile();
      return new MemberAccessPolicyVtlAdapter(safeMap);
    }
    return new NarrowingSafeSecurityPolicy(SafeSecurityPolicy.INSTANCE, policy);
  }
}

final class StandardSecurityPolicy implements VtlSecurityPolicy {
  static final StandardSecurityPolicy INSTANCE = new StandardSecurityPolicy();
  private final MemberAccessPolicy delegate = MemberAccessPolicy.standard();

  private StandardSecurityPolicy() {}

  @Override
  public String policyFingerprint() {
    return delegate.policyFingerprint();
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return delegate.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return delegate.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return delegate.isFieldPermitted(receiverClass, field);
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return delegate.isPropertyPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return delegate.isPropertyMutationPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return delegate.isIndexMutationPermitted(receiverClass);
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return delegate.isPropertyMethodPermitted(receiverClass, method, propertyName);
  }

  @Override
  public LinkerAccessPolicy toLinkerAccessPolicy() {
    return LinkerAccessPolicy.of(delegate);
  }
}

final class SafeSecurityPolicy implements VtlSecurityPolicy {
  static final SafeSecurityPolicy INSTANCE = new SafeSecurityPolicy();
  private final MemberAccessPolicy delegate = MemberAccessPolicy.safe();

  private SafeSecurityPolicy() {}

  @Override
  public String policyFingerprint() {
    return delegate.policyFingerprint();
  }

  @Override
  public boolean isSafeProfile() {
    return true;
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return delegate.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return delegate.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return delegate.isFieldPermitted(receiverClass, field);
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return delegate.isPropertyPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return delegate.isPropertyMutationPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return delegate.isIndexMutationPermitted(receiverClass);
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return delegate.isPropertyMethodPermitted(receiverClass, method, propertyName);
  }

  @Override
  public LinkerAccessPolicy toLinkerAccessPolicy() {
    return LinkerAccessPolicy.of(delegate);
  }
}

final class MemberAccessPolicyVtlAdapter implements VtlSecurityPolicy {
  private final MemberAccessPolicy policy;

  MemberAccessPolicyVtlAdapter(MemberAccessPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
  }

  public MemberAccessPolicy policy() {
    return policy;
  }

  @Override
  public String policyFingerprint() {
    return policy.policyFingerprint();
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

  @Override
  public LinkerAccessPolicy toLinkerAccessPolicy() {
    return LinkerAccessPolicy.of(policy);
  }
}

final class NarrowingSafeSecurityPolicy implements VtlSecurityPolicy {
  private final VtlSecurityPolicy safe;
  private final VtlSecurityPolicy custom;
  private final String fingerprint;

  NarrowingSafeSecurityPolicy(VtlSecurityPolicy safe, VtlSecurityPolicy custom) {
    this.safe = Objects.requireNonNull(safe, "safe must not be null");
    this.custom = Objects.requireNonNull(custom, "custom must not be null");
    this.fingerprint =
        computeCombinedFingerprint(safe.policyFingerprint(), custom.policyFingerprint());
  }

  private static String computeCombinedFingerprint(String f1, String f2) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(f1.getBytes(StandardCharsets.UTF_8));
      md.update((byte) ':');
      md.update(f2.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  @Override
  public String policyFingerprint() {
    return fingerprint;
  }

  @Override
  public boolean isSafeProfile() {
    return true;
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return safe.isClassPermitted(clazz) && custom.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return safe.isMethodPermitted(receiverClass, method)
        && custom.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return safe.isFieldPermitted(receiverClass, field)
        && custom.isFieldPermitted(receiverClass, field);
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return safe.isPropertyPermitted(receiverClass, propertyName)
        && custom.isPropertyPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return safe.isPropertyMutationPermitted(receiverClass, propertyName)
        && custom.isPropertyMutationPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return safe.isIndexMutationPermitted(receiverClass)
        && custom.isIndexMutationPermitted(receiverClass);
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return safe.isPropertyMethodPermitted(receiverClass, method, propertyName)
        && custom.isPropertyMethodPermitted(receiverClass, method, propertyName);
  }

  @Override
  public LinkerAccessPolicy toLinkerAccessPolicy() {
    return new LinkerAccessPolicy() {
      @Override
      public String policyId() {
        return fingerprint;
      }

      @Override
      public boolean isSafeProfile() {
        return true;
      }

      @Override
      public boolean isClassPermitted(Class<?> clazz) {
        return NarrowingSafeSecurityPolicy.this.isClassPermitted(clazz);
      }

      @Override
      public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
        return NarrowingSafeSecurityPolicy.this.isMethodPermitted(receiverClass, method);
      }

      @Override
      public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
        return NarrowingSafeSecurityPolicy.this.isFieldPermitted(receiverClass, field);
      }

      @Override
      public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
        return NarrowingSafeSecurityPolicy.this.isPropertyPermitted(receiverClass, propertyName);
      }

      @Override
      public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
        return NarrowingSafeSecurityPolicy.this.isPropertyMutationPermitted(
            receiverClass, propertyName);
      }

      @Override
      public boolean isIndexMutationPermitted(Class<?> receiverClass) {
        return NarrowingSafeSecurityPolicy.this.isIndexMutationPermitted(receiverClass);
      }

      @Override
      public boolean isPropertyMethodPermitted(
          Class<?> receiverClass, Method method, String propertyName) {
        return NarrowingSafeSecurityPolicy.this.isPropertyMethodPermitted(
            receiverClass, method, propertyName);
      }
    };
  }
}
