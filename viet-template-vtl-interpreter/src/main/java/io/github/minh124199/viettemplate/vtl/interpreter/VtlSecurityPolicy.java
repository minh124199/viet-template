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

  private static final java.util.Set<String> CORE_DENIED_CLASSES =
      java.util.Set.of(
          Class.class.getName(),
          ClassLoader.class.getName(),
          Module.class.getName(),
          Runtime.class.getName(),
          ProcessBuilder.class.getName(),
          Process.class.getName(),
          Thread.class.getName(),
          ThreadGroup.class.getName(),
          System.class.getName(),
          Method.class.getName(),
          Field.class.getName(),
          java.lang.reflect.Constructor.class.getName(),
          java.lang.reflect.Member.class.getName(),
          java.lang.invoke.MethodHandle.class.getName(),
          java.lang.invoke.MethodHandles.class.getName(),
          java.lang.invoke.MethodHandles.Lookup.class.getName(),
          java.security.ProtectionDomain.class.getName(),
          "java.security.AccessController",
          java.security.Security.class.getName(),
          java.util.concurrent.Executor.class.getName(),
          java.util.concurrent.ExecutorService.class.getName(),
          java.util.concurrent.ThreadPoolExecutor.class.getName(),
          java.util.concurrent.ScheduledExecutorService.class.getName(),
          java.util.concurrent.ForkJoinPool.class.getName(),
          java.util.concurrent.CompletableFuture.class.getName());

  private static final java.util.Set<String> CORE_DENIED_PREFIXES =
      java.util.Set.of(
          "java.lang.reflect.",
          "java.lang.invoke.",
          "java.security.",
          "sun.",
          "jdk.internal.",
          "jdk.nashorn.",
          "com.sun.",
          "org.graalvm.");

  private static final java.util.Set<String> CORE_DENIED_METHODS =
      java.util.Set.of(
          "getClass",
          "getClassLoader",
          "getModule",
          "getProtectionDomain",
          "getDeclaredMethods",
          "getDeclaredFields",
          "getDeclaredConstructors",
          "getMethods",
          "getFields",
          "getConstructors",
          "getDeclaredMethod",
          "getDeclaredField",
          "getDeclaredConstructor",
          "getMethod",
          "getField",
          "getConstructor",
          "newInstance",
          "invoke",
          "setAccessible",
          "trySetAccessible",
          "forName",
          "loadClass",
          "findClass",
          "defineClass",
          "lookup",
          "exit",
          "halt",
          "load",
          "loadLibrary",
          "wait",
          "notify",
          "notifyAll",
          "shutdown",
          "shutdownNow",
          "interrupt",
          "suspend",
          "resume");

  private static boolean isCoreDenied(Class<?> clazz) {
    if (clazz == null) {
      return true;
    }
    String name = clazz.getName();
    if (CORE_DENIED_CLASSES.contains(name)) {
      return true;
    }
    for (String prefix : CORE_DENIED_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

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
    if (clazz == null || isCoreDenied(clazz)) {
      return false;
    }
    return policy.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    if (receiverClass == null || isCoreDenied(receiverClass)) {
      return false;
    }
    if (method != null) {
      if (isCoreDenied(method.getDeclaringClass())
          || isCoreDenied(method.getReturnType())
          || CORE_DENIED_METHODS.contains(method.getName())) {
        return false;
      }
    }
    return policy.isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    if (receiverClass == null || isCoreDenied(receiverClass)) {
      return false;
    }
    if (field != null
        && (isCoreDenied(field.getDeclaringClass()) || isCoreDenied(field.getType()))) {
      return false;
    }
    return policy.isFieldPermitted(receiverClass, field);
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    if (receiverClass == null || isCoreDenied(receiverClass)) {
      return false;
    }
    if (propertyName != null
        && propertyName.equalsIgnoreCase("class")
        && !java.util.Map.class.isAssignableFrom(receiverClass)) {
      return false;
    }
    return policy.isPropertyPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    if (receiverClass == null || isCoreDenied(receiverClass)) {
      return false;
    }
    return policy.isPropertyMutationPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    if (receiverClass == null || isCoreDenied(receiverClass)) {
      return false;
    }
    return policy.isIndexMutationPermitted(receiverClass);
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    if (!isPropertyPermitted(receiverClass, propertyName)) {
      return false;
    }
    return isMethodPermitted(receiverClass, method);
  }

  @Override
  public LinkerAccessPolicy toLinkerAccessPolicy() {
    return new LinkerAccessPolicy() {
      @Override
      public String policyId() {
        return MemberAccessPolicyVtlAdapter.this.policyFingerprint();
      }

      @Override
      public boolean isSafeProfile() {
        return MemberAccessPolicyVtlAdapter.this.isSafeProfile();
      }

      @Override
      public boolean isClassPermitted(Class<?> clazz) {
        return MemberAccessPolicyVtlAdapter.this.isClassPermitted(clazz);
      }

      @Override
      public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
        return MemberAccessPolicyVtlAdapter.this.isMethodPermitted(receiverClass, method);
      }

      @Override
      public boolean isFieldPermitted(Class<?> receiverClass, Field field) {
        return MemberAccessPolicyVtlAdapter.this.isFieldPermitted(receiverClass, field);
      }

      @Override
      public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
        return MemberAccessPolicyVtlAdapter.this.isPropertyPermitted(receiverClass, propertyName);
      }

      @Override
      public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
        return MemberAccessPolicyVtlAdapter.this.isPropertyMutationPermitted(
            receiverClass, propertyName);
      }

      @Override
      public boolean isIndexMutationPermitted(Class<?> receiverClass) {
        return MemberAccessPolicyVtlAdapter.this.isIndexMutationPermitted(receiverClass);
      }

      @Override
      public boolean isPropertyMethodPermitted(
          Class<?> receiverClass, Method method, String propertyName) {
        return MemberAccessPolicyVtlAdapter.this.isPropertyMethodPermitted(
            receiverClass, method, propertyName);
      }
    };
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
