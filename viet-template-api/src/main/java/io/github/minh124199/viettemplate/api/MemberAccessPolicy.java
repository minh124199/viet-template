package io.github.minh124199.viettemplate.api;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * High-level, immutable access policy governing class, property, field, and method resolution
 * across all template execution tiers.
 *
 * <p>Supports both defense-in-depth deny rules and explicit allowlist-based safe profiles. Deny
 * rules always take precedence over allow rules.
 */
public interface MemberAccessPolicy extends Serializable {

  /** Checks whether the given class is permitted for template access or linkage. */
  boolean isClassPermitted(Class<?> clazz);

  /** Checks whether invocation of a method on the receiver class is permitted. */
  boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity);

  /** Checks whether invocation of the method on the receiver class is permitted. */
  default boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    return method != null
        && isClassPermitted(method.getDeclaringClass())
        && isMethodPermitted(receiverClass, method.getName(), method.getParameterCount());
  }

  /** Checks whether access to a property on the receiver class is permitted. */
  boolean isPropertyPermitted(Class<?> receiverClass, String propertyName);

  /** Checks whether direct field access on the receiver class is permitted. */
  boolean isFieldPermitted(Class<?> receiverClass, String fieldName);

  /** Checks whether direct field access on the receiver class is permitted. */
  default boolean isFieldPermitted(Class<?> receiverClass, Field field) {
    return field != null
        && isClassPermitted(field.getDeclaringClass())
        && isFieldPermitted(receiverClass, field.getName());
  }

  /** Returns the stable, configuration-sensitive cryptographic fingerprint for this policy. */
  SecurityPolicyFingerprint fingerprint();

  /** Returns the string representation of the policy fingerprint. */
  default String policyFingerprint() {
    return fingerprint().value();
  }

  /** Returns the standard defense-in-depth security policy. */
  static MemberAccessPolicy standard() {
    return DefaultMemberAccessPolicy.STANDARD;
  }

  /** Returns the strict allowlist-based safe security policy. */
  static MemberAccessPolicy safe() {
    return DefaultMemberAccessPolicy.SAFE;
  }

  /** Returns a policy that denies all member and class accesses. */
  static MemberAccessPolicy denyAll() {
    return DefaultMemberAccessPolicy.DENY_ALL;
  }

  /** Creates a new builder for customizing member access policies. */
  static Builder builder() {
    return new Builder();
  }

  /** Builder for constructing immutable {@link MemberAccessPolicy} instances. */
  final class Builder {
    private boolean safeProfile = false;
    private final Set<String> deniedClasses = new HashSet<>();
    private final Set<String> deniedPackagePrefixes = new HashSet<>();
    private final Set<String> deniedMethodNames = new HashSet<>();

    private final Set<String> allowedClasses = new HashSet<>();
    private final Map<String, Set<String>> allowedMethods = new HashMap<>();
    private final Map<String, Set<String>> allowedProperties = new HashMap<>();
    private final Set<String> allowedHelperClasses = new HashSet<>();
    private SensitiveObjectClassifier sensitiveClassifier = SensitiveObjectClassifier.standard();

    public Builder() {
      // Seed with standard defense-in-depth deny rules
      deniedClasses.addAll(DefaultMemberAccessPolicy.CORE_DENIED_CLASS_NAMES);
      deniedPackagePrefixes.addAll(DefaultMemberAccessPolicy.CORE_DENIED_PACKAGE_PREFIXES);
      deniedMethodNames.addAll(DefaultMemberAccessPolicy.CORE_DENIED_METHOD_NAMES);
    }

    public Builder safeProfile(boolean safeProfile) {
      this.safeProfile = safeProfile;
      return this;
    }

    public Builder allowClass(Class<?> clazz) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      allowedClasses.add(clazz.getName());
      return this;
    }

    public Builder allowClasses(Class<?>... classes) {
      if (classes != null) {
        for (Class<?> c : classes) {
          allowClass(c);
        }
      }
      return this;
    }

    public Builder allowMethod(Class<?> clazz, String methodName) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      Objects.requireNonNull(methodName, "methodName must not be null");
      allowedClasses.add(clazz.getName());
      allowedMethods.computeIfAbsent(clazz.getName(), k -> new HashSet<>()).add(methodName);
      return this;
    }

    public Builder allowProperty(Class<?> clazz, String propertyName) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      Objects.requireNonNull(propertyName, "propertyName must not be null");
      allowedClasses.add(clazz.getName());
      allowedProperties.computeIfAbsent(clazz.getName(), k -> new HashSet<>()).add(propertyName);
      return this;
    }

    public Builder allowHelper(Class<?> helperClass) {
      Objects.requireNonNull(helperClass, "helperClass must not be null");
      allowedHelperClasses.add(helperClass.getName());
      allowedClasses.add(helperClass.getName());
      return this;
    }

    public Builder allowHelperMethod(Class<?> helperClass, String methodName) {
      allowHelper(helperClass);
      allowMethod(helperClass, methodName);
      return this;
    }

    public Builder denyClass(Class<?> clazz) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      deniedClasses.add(clazz.getName());
      return this;
    }

    public Builder denyPackage(String packagePrefix) {
      Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
      deniedPackagePrefixes.add(packagePrefix);
      return this;
    }

    public Builder denyMethod(String methodName) {
      Objects.requireNonNull(methodName, "methodName must not be null");
      deniedMethodNames.add(methodName);
      return this;
    }

    public Builder sensitiveClassifier(SensitiveObjectClassifier classifier) {
      this.sensitiveClassifier =
          Objects.requireNonNull(classifier, "sensitiveClassifier must not be null");
      return this;
    }

    public MemberAccessPolicy build() {
      return new DefaultMemberAccessPolicy(
          safeProfile,
          deniedClasses,
          deniedPackagePrefixes,
          deniedMethodNames,
          allowedClasses,
          allowedMethods,
          allowedProperties,
          allowedHelperClasses,
          sensitiveClassifier);
    }
  }
}

@SuppressWarnings("serial")
final class DefaultMemberAccessPolicy implements MemberAccessPolicy {

  @Serial private static final long serialVersionUID = 1L;

  static final Set<String> CORE_DENIED_PACKAGE_PREFIXES =
      Set.of(
          "java.lang.reflect.",
          "java.lang.invoke.",
          "java.security.",
          "sun.",
          "jdk.internal.",
          "jdk.nashorn.",
          "com.sun.",
          "org.graalvm.");

  static final Set<String> CORE_DENIED_CLASS_NAMES =
      Set.of(
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
          Constructor.class.getName(),
          Member.class.getName(),
          MethodHandle.class.getName(),
          MethodHandles.class.getName(),
          MethodHandles.Lookup.class.getName(),
          ProtectionDomain.class.getName(),
          "java.security.AccessController",
          Security.class.getName(),
          java.util.concurrent.Executor.class.getName(),
          java.util.concurrent.ExecutorService.class.getName(),
          java.util.concurrent.ThreadPoolExecutor.class.getName(),
          java.util.concurrent.ScheduledExecutorService.class.getName(),
          java.util.concurrent.ForkJoinPool.class.getName(),
          java.util.concurrent.CompletableFuture.class.getName());

  static final Set<String> CORE_DENIED_METHOD_NAMES =
      Set.of(
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

  static final MemberAccessPolicy STANDARD =
      new DefaultMemberAccessPolicy(
          false,
          CORE_DENIED_CLASS_NAMES,
          CORE_DENIED_PACKAGE_PREFIXES,
          CORE_DENIED_METHOD_NAMES,
          Set.of(),
          Map.of(),
          Map.of(),
          Set.of(),
          SensitiveObjectClassifier.standard());

  static final MemberAccessPolicy SAFE =
      new DefaultMemberAccessPolicy(
          true,
          CORE_DENIED_CLASS_NAMES,
          CORE_DENIED_PACKAGE_PREFIXES,
          CORE_DENIED_METHOD_NAMES,
          Set.of(),
          Map.of(),
          Map.of(),
          Set.of(),
          SensitiveObjectClassifier.standard());

  static final MemberAccessPolicy DENY_ALL = DenyAllMemberAccessPolicy.INSTANCE;

  private final boolean safeProfile;
  private final Set<String> deniedClasses;
  private final Set<String> deniedPackagePrefixes;
  private final Set<String> deniedMethodNames;

  private final Set<String> allowedClasses;
  private final Map<String, Set<String>> allowedMethods;
  private final Map<String, Set<String>> allowedProperties;
  private final Set<String> allowedHelperClasses;
  private final SensitiveObjectClassifier sensitiveClassifier;
  private final SecurityPolicyFingerprint fingerprint;

  DefaultMemberAccessPolicy(
      boolean safeProfile,
      Set<String> deniedClasses,
      Set<String> deniedPackagePrefixes,
      Set<String> deniedMethodNames,
      Set<String> allowedClasses,
      Map<String, Set<String>> allowedMethods,
      Map<String, Set<String>> allowedProperties,
      Set<String> allowedHelperClasses,
      SensitiveObjectClassifier sensitiveClassifier) {
    this.safeProfile = safeProfile;
    this.deniedClasses = Collections.unmodifiableSet(new HashSet<>(deniedClasses));
    this.deniedPackagePrefixes = Collections.unmodifiableSet(new HashSet<>(deniedPackagePrefixes));
    this.deniedMethodNames = Collections.unmodifiableSet(new HashSet<>(deniedMethodNames));
    this.allowedClasses = Collections.unmodifiableSet(new HashSet<>(allowedClasses));
    this.allowedMethods = deepUnmodifiableMap(allowedMethods);
    this.allowedProperties = deepUnmodifiableMap(allowedProperties);
    this.allowedHelperClasses = Collections.unmodifiableSet(new HashSet<>(allowedHelperClasses));
    this.sensitiveClassifier =
        sensitiveClassifier != null ? sensitiveClassifier : SensitiveObjectClassifier.standard();
    this.fingerprint = computeFingerprint();
  }

  private static Map<String, Set<String>> deepUnmodifiableMap(Map<String, Set<String>> map) {
    Map<String, Set<String>> copy = new HashMap<>();
    if (map != null) {
      for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
        copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
      }
    }
    return Collections.unmodifiableMap(copy);
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    String name = clazz.getName();

    // 1. Check explicit denylist
    if (deniedClasses.contains(name)) {
      return false;
    }

    // 2. Check package prefixes
    for (String prefix : deniedPackagePrefixes) {
      if (name.startsWith(prefix)) {
        return false;
      }
    }

    // 3. Check sensitive framework classifier
    if (sensitiveClassifier.isSensitive(clazz)) {
      return false;
    }

    // 4. Safe allowlist profile mode
    if (safeProfile) {
      return isAllowlistedClass(clazz);
    }

    return true;
  }

  private boolean isAllowlistedClass(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    // Standard JDK safe types are always allowed in safe profile
    if (isStandardJdkSafeType(clazz)) {
      return true;
    }
    String name = clazz.getName();
    if (allowedClasses.contains(name) || allowedHelperClasses.contains(name)) {
      return true;
    }
    // Check interfaces and superclasses
    for (Class<?> intf : clazz.getInterfaces()) {
      if (isAllowlistedClass(intf)) {
        return true;
      }
    }
    Class<?> superClazz = clazz.getSuperclass();
    if (superClazz != null && superClazz != Object.class) {
      return isAllowlistedClass(superClazz);
    }
    return false;
  }

  private static boolean isStandardJdkSafeType(Class<?> clazz) {
    if (CharSequence.class.isAssignableFrom(clazz)
        || Number.class.isAssignableFrom(clazz)
        || clazz == Boolean.class
        || clazz == boolean.class
        || clazz == Character.class
        || clazz == char.class
        || Collection.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz)
        || Optional.class.isAssignableFrom(clazz)
        || Enum.class.isAssignableFrom(clazz)) {
      return true;
    }
    if (clazz.isArray()) {
      return isStandardJdkSafeType(clazz.getComponentType());
    }
    return false;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
    if (methodName == null || !isClassPermitted(receiverClass)) {
      return false;
    }

    // Denied method names apply globally
    if (deniedMethodNames.contains(methodName)) {
      return false;
    }

    if (safeProfile) {
      // In safe profile, standard JDK safe methods (e.g. Map.get, Collection.size, String.length)
      if (isStandardSafeMethod(receiverClass, methodName)) {
        return true;
      }

      String receiverName = receiverClass.getName();
      Set<String> methods = allowedMethods.get(receiverName);
      if (methods != null) {
        return methods.contains(methodName);
      }
      // If the class is allowlisted without specific method restrictions, allow declared public
      // non-Object methods
      if (allowedClasses.contains(receiverName) || allowedHelperClasses.contains(receiverName)) {
        return !isDangerousObjectMethod(methodName);
      }
      return false;
    }

    return true;
  }

  private static boolean isDangerousObjectMethod(String methodName) {
    return CORE_DENIED_METHOD_NAMES.contains(methodName);
  }

  private static boolean isStandardSafeMethod(Class<?> receiverClass, String methodName) {
    if (Map.class.isAssignableFrom(receiverClass)) {
      return Set.of("get", "size", "isEmpty", "containsKey", "keySet", "values", "entrySet")
          .contains(methodName);
    }
    if (Collection.class.isAssignableFrom(receiverClass)) {
      return Set.of("size", "isEmpty", "contains", "iterator").contains(methodName);
    }
    if (CharSequence.class.isAssignableFrom(receiverClass)) {
      return Set.of(
              "length",
              "charAt",
              "subSequence",
              "toString",
              "substring",
              "contains",
              "indexOf",
              "startsWith",
              "endsWith",
              "trim",
              "toLowerCase",
              "toUpperCase",
              "replace",
              "split")
          .contains(methodName);
    }
    return false;
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    if (propertyName == null || !isClassPermitted(receiverClass)) {
      return false;
    }

    // Property name 'class' cannot bypass getClass() denial on non-Map receivers
    if (propertyName.equalsIgnoreCase("class") && !Map.class.isAssignableFrom(receiverClass)) {
      return false;
    }

    if (safeProfile) {
      if (Map.class.isAssignableFrom(receiverClass)) {
        return true;
      }
      String receiverName = receiverClass.getName();
      Set<String> props = allowedProperties.get(receiverName);
      if (props != null) {
        return props.contains(propertyName);
      }
      // If class is allowlisted without property restrictions, allow public property resolution
      if (allowedClasses.contains(receiverName) || allowedHelperClasses.contains(receiverName)) {
        return true;
      }
      return false;
    }

    return true;
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
    if (fieldName == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    if (safeProfile) {
      String receiverName = receiverClass.getName();
      return allowedClasses.contains(receiverName);
    }
    return true;
  }

  @Override
  public SecurityPolicyFingerprint fingerprint() {
    return fingerprint;
  }

  private SecurityPolicyFingerprint computeFingerprint() {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update((byte) (safeProfile ? 1 : 0));
      md.update((byte) ';');

      updateSorted(md, deniedClasses);
      md.update((byte) ';');
      updateSorted(md, deniedPackagePrefixes);
      md.update((byte) ';');
      updateSorted(md, deniedMethodNames);
      md.update((byte) ';');
      updateSorted(md, allowedClasses);
      md.update((byte) ';');
      updateSorted(md, allowedHelperClasses);
      md.update((byte) ';');

      TreeSet<String> sortedMethodKeys = new TreeSet<>(allowedMethods.keySet());
      for (String k : sortedMethodKeys) {
        md.update(k.getBytes(StandardCharsets.UTF_8));
        md.update((byte) ':');
        updateSorted(md, allowedMethods.get(k));
        md.update((byte) ',');
      }
      md.update((byte) ';');

      TreeSet<String> sortedPropKeys = new TreeSet<>(allowedProperties.keySet());
      for (String k : sortedPropKeys) {
        md.update(k.getBytes(StandardCharsets.UTF_8));
        md.update((byte) ':');
        updateSorted(md, allowedProperties.get(k));
        md.update((byte) ',');
      }
      md.update((byte) ';');

      updateSorted(md, sensitiveClassifier.sensitiveTypeNames());

      return SecurityPolicyFingerprint.of(HexFormat.of().formatHex(md.digest()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void updateSorted(MessageDigest md, Collection<String> items) {
    TreeSet<String> sorted = new TreeSet<>(items);
    for (String s : sorted) {
      md.update(s.getBytes(StandardCharsets.UTF_8));
      md.update((byte) '|');
    }
  }
}

@SuppressWarnings("serial")
final class DenyAllMemberAccessPolicy implements MemberAccessPolicy {

  @Serial private static final long serialVersionUID = 1L;

  static final DenyAllMemberAccessPolicy INSTANCE = new DenyAllMemberAccessPolicy();

  private static final SecurityPolicyFingerprint FINGERPRINT =
      SecurityPolicyFingerprint.of(
          "0000000000000000000000000000000000000000000000000000000000000000");

  private DenyAllMemberAccessPolicy() {}

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    return false;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
    return false;
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    return false;
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
    return false;
  }

  @Override
  public SecurityPolicyFingerprint fingerprint() {
    return FINGERPRINT;
  }
}
