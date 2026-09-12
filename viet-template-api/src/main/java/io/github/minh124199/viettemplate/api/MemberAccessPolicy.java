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

  /**
   * Checks whether property mutation (#set on a property or setter) on the receiver class is
   * permitted.
   */
  default boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    return true;
  }

  /**
   * Checks whether index mutation (#set on an index or element) on the receiver class is permitted.
   */
  default boolean isIndexMutationPermitted(Class<?> receiverClass) {
    return true;
  }

  /**
   * Checks whether invoking the given method as a property reader (getter, record accessor, or
   * zero-arg property match) is permitted.
   */
  default boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    return isMethodPermitted(receiverClass, method);
  }

  /** Returns the stable, configuration-sensitive cryptographic fingerprint for this policy. */
  SecurityPolicyFingerprint fingerprint();

  /** Returns the string representation of the policy fingerprint. */
  default String policyFingerprint() {
    return fingerprint().value();
  }

  /** Returns whether this policy enforces the strict safe-allowlist sandbox profile. */
  default boolean isSafeProfile() {
    return false;
  }

  /** Returns an equivalent policy that unconditionally enforces the safe sandbox profile. */
  default MemberAccessPolicy toSafeProfile() {
    return new MandatorySafeMemberAccessPolicy(this);
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

    private final Map<String, Set<String>> allowedPropertyMutations = new HashMap<>();
    private final Set<String> allowedIndexMutations = new HashSet<>();
    private boolean allowAllPropertyMutations = false;
    private boolean allowAllIndexMutations = false;

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

    public Builder allowPropertyMutation(Class<?> clazz, String propertyName) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      Objects.requireNonNull(propertyName, "propertyName must not be null");
      allowedPropertyMutations
          .computeIfAbsent(clazz.getName(), k -> new HashSet<>())
          .add(propertyName);
      return this;
    }

    public Builder allowIndexMutation(Class<?> clazz) {
      Objects.requireNonNull(clazz, "clazz must not be null");
      allowedIndexMutations.add(clazz.getName());
      return this;
    }

    public Builder allowAllPropertyMutations(boolean allow) {
      this.allowAllPropertyMutations = allow;
      return this;
    }

    public Builder allowAllIndexMutations(boolean allow) {
      this.allowAllIndexMutations = allow;
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
          sensitiveClassifier,
          allowedPropertyMutations,
          allowedIndexMutations,
          allowAllPropertyMutations,
          allowAllIndexMutations);
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

  private final Map<String, Set<String>> allowedPropertyMutations;
  private final Set<String> allowedIndexMutations;
  private final boolean allowAllPropertyMutations;
  private final boolean allowAllIndexMutations;
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
      SensitiveObjectClassifier sensitiveClassifier,
      Map<String, Set<String>> allowedPropertyMutations,
      Set<String> allowedIndexMutations,
      boolean allowAllPropertyMutations,
      boolean allowAllIndexMutations) {
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
    this.allowedPropertyMutations = deepUnmodifiableMap(allowedPropertyMutations);
    this.allowedIndexMutations = Collections.unmodifiableSet(new HashSet<>(allowedIndexMutations));
    this.allowAllPropertyMutations = allowAllPropertyMutations;
    this.allowAllIndexMutations = allowAllIndexMutations;
    this.fingerprint = computeFingerprint();
  }

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
    this(
        safeProfile,
        deniedClasses,
        deniedPackagePrefixes,
        deniedMethodNames,
        allowedClasses,
        allowedMethods,
        allowedProperties,
        allowedHelperClasses,
        sensitiveClassifier,
        Map.of(),
        Set.of(),
        false,
        false);
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
  public boolean isSafeProfile() {
    return safeProfile;
  }

  @Override
  public MemberAccessPolicy toSafeProfile() {
    if (safeProfile && !allowAllPropertyMutations && !allowAllIndexMutations) {
      return this;
    }
    return new DefaultMemberAccessPolicy(
        true,
        deniedClasses,
        deniedPackagePrefixes,
        deniedMethodNames,
        allowedClasses,
        allowedMethods,
        allowedProperties,
        allowedHelperClasses,
        sensitiveClassifier,
        allowedPropertyMutations,
        allowedIndexMutations,
        false,
        false);
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

  boolean isDirectlyApproved(Class<?> clazz) {
    if (clazz == null || clazz == Object.class) {
      return false;
    }
    if (clazz.isAnnotationPresent(TemplateData.class) || clazz.isRecord()) {
      return true;
    }
    for (Method m : clazz.getDeclaredMethods()) {
      if (m.isAnnotationPresent(TemplateCallable.class)) {
        return true;
      }
    }
    String name = clazz.getName();
    return allowedClasses.contains(name) || allowedHelperClasses.contains(name);
  }

  boolean hasApprovedAncestor(Class<?> clazz) {
    if (clazz == null || clazz == Object.class) {
      return false;
    }
    if (isDirectlyApproved(clazz)) {
      return true;
    }
    for (Class<?> intf : clazz.getInterfaces()) {
      if (hasApprovedAncestor(intf)) {
        return true;
      }
    }
    Class<?> superClazz = clazz.getSuperclass();
    if (superClazz != null && superClazz != Object.class) {
      return hasApprovedAncestor(superClazz);
    }
    return false;
  }

  private boolean isAllowlistedClass(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    // Standard safe types are always allowed in safe profile
    if (isStandardSafeType(clazz)) {
      return true;
    }
    return hasApprovedAncestor(clazz);
  }

  boolean isApprovedAncestorMethod(Class<?> receiverClass, Method method) {
    if (receiverClass == null || method == null) {
      return false;
    }
    String name = method.getName();
    Class<?>[] params = method.getParameterTypes();

    Class<?> curr = receiverClass.getSuperclass();
    while (curr != null && curr != Object.class) {
      if (isDirectlyApproved(curr)) {
        try {
          Method m = curr.getDeclaredMethod(name, params);
          if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
            return true;
          }
        } catch (NoSuchMethodException ignored) {
        }
      }
      if (searchApprovedInterfacesMethod(curr, name, params)) {
        return true;
      }
      curr = curr.getSuperclass();
    }

    return searchApprovedInterfacesMethod(receiverClass, name, params);
  }

  boolean searchApprovedInterfacesMethod(Class<?> clazz, String name, Class<?>... params) {
    if (clazz == null) {
      return false;
    }
    for (Class<?> intf : clazz.getInterfaces()) {
      if (isDirectlyApproved(intf)) {
        try {
          Method m = intf.getDeclaredMethod(name, params);
          if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
            return true;
          }
        } catch (NoSuchMethodException ignored) {
        }
      }
      if (searchApprovedInterfacesMethod(intf, name, params)) {
        return true;
      }
    }
    return false;
  }

  static boolean isStandardSafeType(Class<?> clazz) {
    if (CharSequence.class.isAssignableFrom(clazz)
        || Number.class.isAssignableFrom(clazz)
        || clazz == Boolean.class
        || clazz == boolean.class
        || clazz == Character.class
        || clazz == char.class
        || Collection.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz)
        || Optional.class.isAssignableFrom(clazz)
        || Enum.class.isAssignableFrom(clazz)
        || java.util.Iterator.class.isAssignableFrom(clazz)
        || clazz.getName().endsWith("ForeachMetadata")) {
      return true;
    }
    if (clazz.isArray()) {
      return isStandardSafeType(clazz.getComponentType());
    }
    return false;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    if (method == null
        || !isClassPermitted(receiverClass)
        || !isClassPermitted(method.getDeclaringClass())) {
      return false;
    }
    String methodName = method.getName();
    if (deniedMethodNames.contains(methodName)) {
      return false;
    }
    if (safeProfile) {
      if (isStandardSafeMethod(receiverClass, methodName)) {
        return true;
      }
      String receiverName = receiverClass.getName();
      Set<String> methods = allowedMethods.get(receiverName);
      if (methods != null && methods.contains(methodName)) {
        return true;
      }
      if (allowedHelperClasses.contains(receiverName)) {
        return !isDangerousObjectMethod(methodName);
      }
      if (hasTemplateCallable(method, receiverClass)) {
        return true;
      }
      if (isRecordComponentAccessor(receiverClass, method, methodName)) {
        return true;
      }
      if (!receiverClass.isRecord()
          && isLegitimateGetter(method, propertyNameFromGetter(methodName))) {
        if (isDirectlyApproved(receiverClass) || isApprovedAncestorMethod(receiverClass, method)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
    if (methodName == null || !isClassPermitted(receiverClass)) {
      return false;
    }

    if (deniedMethodNames.contains(methodName)) {
      return false;
    }

    if (safeProfile) {
      if (isStandardSafeMethod(receiverClass, methodName)) {
        return true;
      }
      String receiverName = receiverClass.getName();
      Set<String> methods = allowedMethods.get(receiverName);
      if (methods != null && methods.contains(methodName)) {
        return true;
      }
      if (allowedHelperClasses.contains(receiverName)) {
        return !isDangerousObjectMethod(methodName);
      }
      for (Method m : receiverClass.getMethods()) {
        if (m.getName().equals(methodName) && m.getParameterCount() == arity) {
          if (isMethodPermitted(receiverClass, m)) {
            return true;
          }
        }
      }
      return false;
    }

    return true;
  }

  static boolean isDangerousObjectMethod(String methodName) {
    return CORE_DENIED_METHOD_NAMES.contains(methodName);
  }

  private static final Set<String> SAFE_MAP_METHODS =
      Set.of(
          "get",
          "size",
          "isEmpty",
          "containsKey",
          "containsValue",
          "keySet",
          "values",
          "entrySet",
          "getOrDefault");

  private static final Set<String> SAFE_COLLECTION_METHODS =
      Set.of("size", "isEmpty", "contains", "containsAll", "iterator", "toArray", "get");

  private static final Set<String> SAFE_CHAR_SEQUENCE_METHODS =
      Set.of(
          "length",
          "charAt",
          "subSequence",
          "toString",
          "substring",
          "contains",
          "indexOf",
          "lastIndexOf",
          "startsWith",
          "endsWith",
          "trim",
          "toLowerCase",
          "toUpperCase",
          "replace",
          "replaceAll",
          "split",
          "isEmpty",
          "repeat",
          "strip",
          "stripLeading",
          "stripTrailing",
          "isBlank",
          "matches",
          "equals",
          "hashCode",
          "compareTo");

  private static final Set<String> SAFE_OBJECT_METHODS = Set.of("toString", "equals", "hashCode");

  private static final Set<String> SAFE_ITERATOR_METHODS = Set.of("hasNext", "next");

  private static final Set<String> SAFE_OPTIONAL_METHODS =
      Set.of(
          "isPresent",
          "isEmpty",
          "get",
          "orElse",
          "orElseNull",
          "orElseThrow",
          "toString",
          "equals",
          "hashCode");

  private static final Set<String> SAFE_ENUM_METHODS =
      Set.of("name", "ordinal", "toString", "equals", "hashCode", "compareTo");

  private static final Set<String> SAFE_FOREACH_METHODS =
      Set.of(
          "index",
          "getIndex",
          "count",
          "getCount",
          "first",
          "isFirst",
          "last",
          "isLast",
          "hasNext",
          "getHasNext",
          "parent",
          "getParent",
          "topmost",
          "getTopmost",
          "stop",
          "toString");

  static boolean isStandardSafeMethod(Class<?> receiverClass, String methodName) {
    if (SAFE_OBJECT_METHODS.contains(methodName)) {
      return true;
    }
    if (Map.class.isAssignableFrom(receiverClass)) {
      return SAFE_MAP_METHODS.contains(methodName);
    }
    if (Collection.class.isAssignableFrom(receiverClass)) {
      return SAFE_COLLECTION_METHODS.contains(methodName);
    }
    if (CharSequence.class.isAssignableFrom(receiverClass)) {
      return SAFE_CHAR_SEQUENCE_METHODS.contains(methodName);
    }
    if (Number.class.isAssignableFrom(receiverClass)) {
      return SAFE_OBJECT_METHODS.contains(methodName)
          || Set.of(
                  "intValue",
                  "longValue",
                  "floatValue",
                  "doubleValue",
                  "byteValue",
                  "shortValue",
                  "compareTo")
              .contains(methodName);
    }
    if (receiverClass == Boolean.class || receiverClass == boolean.class) {
      return SAFE_OBJECT_METHODS.contains(methodName)
          || Set.of("booleanValue", "compareTo").contains(methodName);
    }
    if (receiverClass == Character.class || receiverClass == char.class) {
      return SAFE_OBJECT_METHODS.contains(methodName)
          || Set.of("charValue", "compareTo").contains(methodName);
    }
    if (Optional.class.isAssignableFrom(receiverClass)) {
      return SAFE_OPTIONAL_METHODS.contains(methodName);
    }
    if (Enum.class.isAssignableFrom(receiverClass)) {
      return SAFE_ENUM_METHODS.contains(methodName);
    }
    if (java.util.Iterator.class.isAssignableFrom(receiverClass)) {
      return SAFE_ITERATOR_METHODS.contains(methodName);
    }
    if (receiverClass.getName().endsWith("ForeachMetadata")) {
      return SAFE_FOREACH_METHODS.contains(methodName);
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
      if (props != null && props.contains(propertyName)) {
        return true;
      }
      if (allowedClasses.contains(receiverName) || allowedHelperClasses.contains(receiverName)) {
        return true;
      }
      if (isStandardSafeType(receiverClass)) {
        return true;
      }
      if (receiverClass.isRecord()) {
        for (java.lang.reflect.RecordComponent rc : receiverClass.getRecordComponents()) {
          if (rc.getName().equals(propertyName)) {
            return true;
          }
        }
        return false;
      }
      if (receiverClass.isAnnotationPresent(TemplateData.class)) {
        return true;
      }
      return isApprovedAncestorProperty(receiverClass, propertyName);
    }

    return true;
  }

  boolean isApprovedAncestorProperty(Class<?> receiverClass, String propertyName) {
    if (receiverClass == null || propertyName == null) {
      return false;
    }
    String capitalized = capitalize(propertyName);
    String getName = "get" + capitalized;
    String isName = "is" + capitalized;

    Class<?> curr = receiverClass.getSuperclass();
    while (curr != null && curr != Object.class) {
      if (isDirectlyApproved(curr)) {
        Set<String> superProps = allowedProperties.get(curr.getName());
        if (superProps != null && superProps.contains(propertyName)) {
          return true;
        }
        if (curr.isRecord()) {
          for (java.lang.reflect.RecordComponent rc : curr.getRecordComponents()) {
            if (rc.getName().equals(propertyName)) {
              return true;
            }
          }
        } else {
          for (Method m : curr.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
                && m.getParameterCount() == 0
                && (m.getName().equals(getName)
                    || m.getName().equalsIgnoreCase(getName)
                    || m.getName().equals(isName)
                    || m.getName().equalsIgnoreCase(isName)
                    || m.getName().equals(propertyName))) {
              return true;
            }
          }
        }
      }
      if (searchApprovedInterfacesProperty(curr, propertyName, getName, isName)) {
        return true;
      }
      curr = curr.getSuperclass();
    }

    return searchApprovedInterfacesProperty(receiverClass, propertyName, getName, isName);
  }

  boolean searchApprovedInterfacesProperty(
      Class<?> clazz, String propertyName, String getName, String isName) {
    if (clazz == null) {
      return false;
    }
    for (Class<?> intf : clazz.getInterfaces()) {
      if (isDirectlyApproved(intf)) {
        Set<String> intfProps = allowedProperties.get(intf.getName());
        if (intfProps != null && intfProps.contains(propertyName)) {
          return true;
        }
        for (Method m : intf.getDeclaredMethods()) {
          if (java.lang.reflect.Modifier.isPublic(m.getModifiers())
              && m.getParameterCount() == 0
              && (m.getName().equals(getName)
                  || m.getName().equalsIgnoreCase(getName)
                  || m.getName().equals(isName)
                  || m.getName().equalsIgnoreCase(isName)
                  || m.getName().equals(propertyName))) {
            return true;
          }
        }
      }
      if (searchApprovedInterfacesProperty(intf, propertyName, getName, isName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    if (method == null
        || !isClassPermitted(receiverClass)
        || !isClassPermitted(method.getDeclaringClass())) {
      return false;
    }
    String methodName = method.getName();
    if (deniedMethodNames.contains(methodName)) {
      return false;
    }
    if (!isPropertyPermitted(receiverClass, propertyName)) {
      return false;
    }
    if (!safeProfile) {
      return isMethodPermitted(receiverClass, method);
    }
    if (hasTemplateCallable(method, receiverClass)) {
      return true;
    }
    String receiverName = receiverClass.getName();
    Set<String> methods = allowedMethods.get(receiverName);
    if (methods != null && methods.contains(methodName)) {
      return true;
    }
    if (allowedHelperClasses.contains(receiverName) && !isDangerousObjectMethod(methodName)) {
      return true;
    }
    if (isStandardSafeMethod(receiverClass, methodName)) {
      return true;
    }
    if (isRecordComponentAccessor(receiverClass, method, propertyName)) {
      return true;
    }
    if (!receiverClass.isRecord() && isLegitimateGetter(method, propertyName)) {
      if (isDirectlyApproved(receiverClass) || isApprovedAncestorMethod(receiverClass, method)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
    if (receiverClass == null || propertyName == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    if (!safeProfile) {
      return true;
    }
    if (allowAllPropertyMutations) {
      return true;
    }
    Set<String> props = allowedPropertyMutations.get(receiverClass.getName());
    if (props != null && props.contains(propertyName)) {
      return true;
    }
    for (Class<?> intf : receiverClass.getInterfaces()) {
      Set<String> intfProps = allowedPropertyMutations.get(intf.getName());
      if (intfProps != null && intfProps.contains(propertyName)) {
        return true;
      }
    }
    Class<?> sup = receiverClass.getSuperclass();
    while (sup != null && sup != Object.class) {
      Set<String> supProps = allowedPropertyMutations.get(sup.getName());
      if (supProps != null && supProps.contains(propertyName)) {
        return true;
      }
      sup = sup.getSuperclass();
    }
    return false;
  }

  @Override
  public boolean isIndexMutationPermitted(Class<?> receiverClass) {
    if (receiverClass == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    if (!safeProfile) {
      return true;
    }
    if (allowAllIndexMutations) {
      return true;
    }
    if (allowedIndexMutations.contains(receiverClass.getName())) {
      return true;
    }
    for (Class<?> intf : receiverClass.getInterfaces()) {
      if (allowedIndexMutations.contains(intf.getName())) {
        return true;
      }
    }
    Class<?> sup = receiverClass.getSuperclass();
    while (sup != null && sup != Object.class) {
      if (allowedIndexMutations.contains(sup.getName())) {
        return true;
      }
      sup = sup.getSuperclass();
    }
    return false;
  }

  static boolean hasTemplateCallable(Method method, Class<?> receiverClass) {
    if (method == null) {
      return false;
    }
    if (method.isAnnotationPresent(TemplateCallable.class)) {
      return true;
    }
    Class<?>[] paramTypes = method.getParameterTypes();
    String name = method.getName();

    if (searchTemplateCallableHierarchy(receiverClass, name, paramTypes)) {
      return true;
    }
    if (searchTemplateCallableHierarchy(method.getDeclaringClass(), name, paramTypes)) {
      return true;
    }

    if (method.isBridge() || method.isSynthetic()) {
      for (Method m : method.getDeclaringClass().getDeclaredMethods()) {
        if (!m.isBridge()
            && !m.isSynthetic()
            && m.getName().equals(name)
            && m.getParameterCount() == paramTypes.length) {
          if (hasTemplateCallable(m, receiverClass)) {
            return true;
          }
        }
      }
    } else {
      for (Method m : method.getDeclaringClass().getDeclaredMethods()) {
        if (m.isBridge()
            && m.getName().equals(name)
            && m.getParameterCount() == paramTypes.length) {
          if (searchTemplateCallableHierarchy(receiverClass, name, m.getParameterTypes())
              || searchTemplateCallableHierarchy(
                  method.getDeclaringClass(), name, m.getParameterTypes())) {
            return true;
          }
        }
      }
    }

    return false;
  }

  static boolean searchTemplateCallableHierarchy(
      Class<?> clazz, String name, Class<?>... paramTypes) {
    if (clazz == null || clazz == Object.class) {
      return false;
    }
    try {
      Method m = clazz.getDeclaredMethod(name, paramTypes);
      if (m.isAnnotationPresent(TemplateCallable.class)) {
        return true;
      }
    } catch (NoSuchMethodException ignored) {
    }
    for (Class<?> intf : clazz.getInterfaces()) {
      if (searchTemplateCallableHierarchy(intf, name, paramTypes)) {
        return true;
      }
    }
    return searchTemplateCallableHierarchy(clazz.getSuperclass(), name, paramTypes);
  }

  static boolean isRecordComponentAccessor(
      Class<?> receiverClass, Method method, String propertyName) {
    if (receiverClass.isRecord()) {
      for (java.lang.reflect.RecordComponent rc : receiverClass.getRecordComponents()) {
        if (rc.getName().equals(propertyName) && rc.getName().equals(method.getName())) {
          return true;
        }
      }
    }
    Class<?> declaring = method.getDeclaringClass();
    if (declaring != receiverClass && declaring.isRecord()) {
      for (java.lang.reflect.RecordComponent rc : declaring.getRecordComponents()) {
        if (rc.getName().equals(propertyName) && rc.getName().equals(method.getName())) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean isLegitimateGetter(Method method, String propertyName) {
    if (method.getParameterCount() != 0) {
      return false;
    }
    Class<?> returnType = method.getReturnType();
    if (returnType == void.class || returnType == Void.class) {
      return false;
    }
    String name = method.getName();
    String capitalized = capitalize(propertyName);
    if (name.equals("get" + capitalized) || name.equalsIgnoreCase("get" + propertyName)) {
      return true;
    }
    if ((name.equals("is" + capitalized) || name.equalsIgnoreCase("is" + propertyName))
        && (returnType == boolean.class || returnType == Boolean.class)) {
      return true;
    }
    return false;
  }

  static String propertyNameFromGetter(String methodName) {
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    }
    if (methodName.startsWith("is") && methodName.length() > 2) {
      return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
    }
    return methodName;
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
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
      md.update((byte) (allowAllPropertyMutations ? 1 : 0));
      md.update((byte) ';');
      md.update((byte) (allowAllIndexMutations ? 1 : 0));
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
      updateSorted(md, allowedIndexMutations);
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

      TreeSet<String> sortedPropMutKeys = new TreeSet<>(allowedPropertyMutations.keySet());
      for (String k : sortedPropMutKeys) {
        md.update(k.getBytes(StandardCharsets.UTF_8));
        md.update((byte) ':');
        updateSorted(md, allowedPropertyMutations.get(k));
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

  @Override
  public SecurityPolicyFingerprint fingerprint() {
    return FINGERPRINT;
  }
}

@SuppressWarnings("serial")
final class MandatorySafeMemberAccessPolicy implements MemberAccessPolicy {

  @Serial private static final long serialVersionUID = 1L;

  private final MemberAccessPolicy delegate;
  private final SecurityPolicyFingerprint fingerprint;

  MandatorySafeMemberAccessPolicy(MemberAccessPolicy delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.fingerprint = computeFingerprint();
  }

  @Override
  public boolean isSafeProfile() {
    return true;
  }

  @Override
  public MemberAccessPolicy toSafeProfile() {
    return this;
  }

  @Override
  public boolean isClassPermitted(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    String name = clazz.getName();
    if (DefaultMemberAccessPolicy.CORE_DENIED_CLASS_NAMES.contains(name)) {
      return false;
    }
    for (String prefix : DefaultMemberAccessPolicy.CORE_DENIED_PACKAGE_PREFIXES) {
      if (name.startsWith(prefix)) {
        return false;
      }
    }
    if (SensitiveObjectClassifier.standard().isSensitive(clazz)) {
      return false;
    }
    return delegate.isClassPermitted(clazz);
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
    if (method == null
        || !isClassPermitted(receiverClass)
        || !isClassPermitted(method.getDeclaringClass())) {
      return false;
    }
    String methodName = method.getName();
    if (DefaultMemberAccessPolicy.CORE_DENIED_METHOD_NAMES.contains(methodName)) {
      return false;
    }
    if (!delegate.isMethodPermitted(receiverClass, method)) {
      return false;
    }
    if (DefaultMemberAccessPolicy.isStandardSafeMethod(receiverClass, methodName)) {
      return true;
    }
    if (DefaultMemberAccessPolicy.hasTemplateCallable(method, receiverClass)) {
      return true;
    }
    if (DefaultMemberAccessPolicy.isRecordComponentAccessor(receiverClass, method, methodName)) {
      return true;
    }
    if (!receiverClass.isRecord()
        && DefaultMemberAccessPolicy.isLegitimateGetter(
            method, DefaultMemberAccessPolicy.propertyNameFromGetter(methodName))) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
    if (methodName == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    if (DefaultMemberAccessPolicy.CORE_DENIED_METHOD_NAMES.contains(methodName)) {
      return false;
    }
    if (!delegate.isMethodPermitted(receiverClass, methodName, arity)) {
      return false;
    }
    if (DefaultMemberAccessPolicy.isStandardSafeMethod(receiverClass, methodName)) {
      return true;
    }
    for (Method m : receiverClass.getMethods()) {
      if (m.getName().equals(methodName) && m.getParameterCount() == arity) {
        if (isMethodPermitted(receiverClass, m)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
    if (propertyName == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    if (propertyName.equalsIgnoreCase("class") && !Map.class.isAssignableFrom(receiverClass)) {
      return false;
    }
    return delegate.isPropertyPermitted(receiverClass, propertyName);
  }

  @Override
  public boolean isPropertyMethodPermitted(
      Class<?> receiverClass, Method method, String propertyName) {
    if (method == null
        || !isClassPermitted(receiverClass)
        || !isClassPermitted(method.getDeclaringClass())) {
      return false;
    }
    if (DefaultMemberAccessPolicy.CORE_DENIED_METHOD_NAMES.contains(method.getName())) {
      return false;
    }
    if (!isPropertyPermitted(receiverClass, propertyName)) {
      return false;
    }
    return isMethodPermitted(receiverClass, method);
  }

  @Override
  public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
    if (fieldName == null || !isClassPermitted(receiverClass)) {
      return false;
    }
    return delegate.isFieldPermitted(receiverClass, fieldName);
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
  public SecurityPolicyFingerprint fingerprint() {
    return fingerprint;
  }

  private SecurityPolicyFingerprint computeFingerprint() {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update("mandatory-safe:".getBytes(StandardCharsets.UTF_8));
      md.update(delegate.policyFingerprint().getBytes(StandardCharsets.UTF_8));
      return SecurityPolicyFingerprint.of(HexFormat.of().formatHex(md.digest()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
