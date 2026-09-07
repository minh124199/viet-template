package io.github.minh124199.viettemplate.runtime.linker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core dynamic linker that resolves {@link MemberKey} requests against receiver class shapes using
 * high-performance {@link MethodHandle} targets while enforcing {@link LinkerAccessPolicy}.
 */
public final class DynamicLinker {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

  private final LinkerAccessPolicy defaultPolicy;

  public DynamicLinker() {
    this(LinkerAccessPolicy.standard());
  }

  public DynamicLinker(LinkerAccessPolicy defaultPolicy) {
    this.defaultPolicy = Objects.requireNonNull(defaultPolicy, "defaultPolicy must not be null");
  }

  /**
   * Links a dynamic member access request using this linker's default access policy.
   *
   * @param receiverClass the runtime class of the receiver
   * @param memberKey the member operation, name, and arity
   * @return resolved {@link AccessLink}
   */
  public AccessLink link(Class<?> receiverClass, MemberKey memberKey) {
    return link(receiverClass, memberKey, defaultPolicy);
  }

  /**
   * Links a dynamic member access request for a given receiver class.
   *
   * @param receiverClass the runtime class of the receiver
   * @param memberKey the member operation, name, and arity
   * @param policy the active access policy
   * @return resolved {@link AccessLink}
   */
  public AccessLink link(Class<?> receiverClass, MemberKey memberKey, LinkerAccessPolicy policy) {
    Objects.requireNonNull(memberKey, "memberKey must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    if (receiverClass == null) {
      return AccessLink.missing(null, memberKey.name());
    }

    if (!policy.isClassPermitted(receiverClass)) {
      return AccessLink.denied(
          receiverClass,
          memberKey.name(),
          "class " + receiverClass.getName() + " is denied by policy");
    }

    return switch (memberKey.operation()) {
      case PROPERTY_GET -> linkPropertyGet(receiverClass, memberKey.name(), policy);
      case PROPERTY_SET -> linkPropertySet(receiverClass, memberKey.name(), policy);
      case METHOD_CALL ->
          linkMethodCall(receiverClass, memberKey.name(), memberKey.arity(), policy);
      case INDEX_GET -> linkIndexGet(receiverClass, policy);
      case INDEX_SET -> linkIndexSet(receiverClass, policy);
    };
  }

  private AccessLink linkPropertyGet(
      Class<?> receiverClass, String propertyName, LinkerAccessPolicy policy) {
    String capitalized = capitalize(propertyName);

    // 1. getname()
    Method m = findPublicZeroArgMethod(receiverClass, "get" + propertyName.toLowerCase());
    if (m != null) {
      return checkMethodAndCreateLink(receiverClass, m, propertyName, policy);
    }

    // 2. getName()
    m = findPublicZeroArgMethod(receiverClass, "get" + capitalized);
    if (m != null) {
      return checkMethodAndCreateLink(receiverClass, m, propertyName, policy);
    }

    // 3. Map.get(propertyName)
    if (Map.class.isAssignableFrom(receiverClass)) {
      try {
        MethodHandle getMh =
            LOOKUP.findVirtual(Map.class, "get", MethodType.methodType(Object.class, Object.class));
        MethodHandle bound =
            MethodHandles.insertArguments(getMh, 1, propertyName)
                .asType(MethodType.methodType(Object.class, Object.class));
        return AccessLink.ok(receiverClass, bound, propertyName);
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    // 4. isName()
    m = findPublicZeroArgMethod(receiverClass, "is" + capitalized);
    if (m != null && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
      return checkMethodAndCreateLink(receiverClass, m, propertyName, policy);
    }

    // 5. isname()
    m = findPublicZeroArgMethod(receiverClass, "is" + propertyName.toLowerCase());
    if (m != null && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
      return checkMethodAndCreateLink(receiverClass, m, propertyName, policy);
    }

    // 6. Record accessor / zero-arg method matching name()
    m = findPublicZeroArgMethod(receiverClass, propertyName);
    if (m != null) {
      return checkMethodAndCreateLink(receiverClass, m, propertyName, policy);
    }

    // 7. Public field
    Field f = findPublicField(receiverClass, propertyName);
    if (f != null) {
      if (!policy.isFieldPermitted(receiverClass, f)) {
        return AccessLink.denied(
            receiverClass, propertyName, "field " + f.getName() + " is denied by policy");
      }
      try {
        MethodHandle mh =
            LOOKUP.unreflectGetter(f).asType(MethodType.methodType(Object.class, Object.class));
        return AccessLink.ok(receiverClass, mh, propertyName);
      } catch (IllegalAccessException e) {
        // fall through
      }
    }

    // 8. Pseudo-properties: size / length
    if ("size".equals(propertyName)) {
      if (Collection.class.isAssignableFrom(receiverClass)) {
        try {
          MethodHandle mh =
              LOOKUP
                  .findVirtual(Collection.class, "size", MethodType.methodType(int.class))
                  .asType(MethodType.methodType(Object.class, Object.class));
          return AccessLink.ok(receiverClass, mh, propertyName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          // fall through
        }
      } else if (Map.class.isAssignableFrom(receiverClass)) {
        try {
          MethodHandle mh =
              LOOKUP
                  .findVirtual(Map.class, "size", MethodType.methodType(int.class))
                  .asType(MethodType.methodType(Object.class, Object.class));
          return AccessLink.ok(receiverClass, mh, propertyName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          // fall through
        }
      } else if (receiverClass.isArray()) {
        try {
          MethodHandle mh =
              LOOKUP
                  .findStatic(
                      Array.class, "getLength", MethodType.methodType(int.class, Object.class))
                  .asType(MethodType.methodType(Object.class, Object.class));
          return AccessLink.ok(receiverClass, mh, propertyName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          // fall through
        }
      }
    } else if ("length".equals(propertyName)) {
      if (receiverClass.isArray()) {
        try {
          MethodHandle mh =
              LOOKUP
                  .findStatic(
                      Array.class, "getLength", MethodType.methodType(int.class, Object.class))
                  .asType(MethodType.methodType(Object.class, Object.class));
          return AccessLink.ok(receiverClass, mh, propertyName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          // fall through
        }
      } else if (CharSequence.class.isAssignableFrom(receiverClass)) {
        try {
          MethodHandle mh =
              LOOKUP
                  .findVirtual(CharSequence.class, "length", MethodType.methodType(int.class))
                  .asType(MethodType.methodType(Object.class, Object.class));
          return AccessLink.ok(receiverClass, mh, propertyName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
          // fall through
        }
      }
    }

    return AccessLink.missing(receiverClass, propertyName);
  }

  private AccessLink linkPropertySet(
      Class<?> receiverClass, String propertyName, LinkerAccessPolicy policy) {
    String capitalized = capitalize(propertyName);

    // 1. Setter method setName(val) or setname(val)
    Method setter = findPublicOneArgMethod(receiverClass, "set" + capitalized);
    if (setter == null) {
      setter = findPublicOneArgMethod(receiverClass, "set" + propertyName.toLowerCase());
    }
    if (setter != null) {
      if (!policy.isMethodPermitted(receiverClass, setter)) {
        return AccessLink.denied(
            receiverClass, propertyName, "setter " + setter.getName() + " is denied by policy");
      }
      Method accessible = findAccessibleMethod(setter);
      Method toUnreflect = accessible != null ? accessible : setter;
      try {
        MethodHandle mh = LOOKUP.unreflect(toUnreflect);
        return AccessLink.ok(receiverClass, mh, propertyName);
      } catch (IllegalAccessException e) {
        // fall through
      }
    }

    // 2. Map.put(propertyName, val)
    if (Map.class.isAssignableFrom(receiverClass)) {
      try {
        MethodHandle putMh =
            LOOKUP.findVirtual(
                Map.class, "put", MethodType.methodType(Object.class, Object.class, Object.class));
        MethodHandle bound = MethodHandles.insertArguments(putMh, 1, propertyName);
        return AccessLink.ok(receiverClass, bound, propertyName);
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    // 3. Public field
    Field f = findPublicField(receiverClass, propertyName);
    if (f != null && !Modifier.isFinal(f.getModifiers())) {
      if (!policy.isFieldPermitted(receiverClass, f)) {
        return AccessLink.denied(
            receiverClass, propertyName, "field " + f.getName() + " is denied by policy");
      }
      try {
        MethodHandle mh = LOOKUP.unreflectSetter(f);
        return AccessLink.ok(receiverClass, mh, propertyName);
      } catch (IllegalAccessException e) {
        // fall through
      }
    }

    return AccessLink.missing(receiverClass, propertyName);
  }

  private AccessLink linkMethodCall(
      Class<?> receiverClass, String methodName, int arity, LinkerAccessPolicy policy) {
    Method targetMethod = null;
    for (Method m : receiverClass.getMethods()) {
      if (m.getName().equals(methodName) && m.getParameterCount() == arity) {
        targetMethod = m;
        break;
      }
    }

    if (targetMethod == null) {
      return AccessLink.missing(receiverClass, methodName);
    }

    if (!policy.isMethodPermitted(receiverClass, targetMethod)) {
      return AccessLink.denied(
          receiverClass, methodName, "method " + methodName + " is denied by policy");
    }

    Method accessible = findAccessibleMethod(targetMethod);
    Method toUnreflect = accessible != null ? accessible : targetMethod;
    try {
      MethodHandle mh = LOOKUP.unreflect(toUnreflect);
      return AccessLink.ok(receiverClass, mh, methodName);
    } catch (IllegalAccessException e) {
      return AccessLink.denied(receiverClass, methodName, "inaccessible method: " + e.getMessage());
    }
  }

  private AccessLink linkIndexGet(Class<?> receiverClass, LinkerAccessPolicy policy) {
    if (List.class.isAssignableFrom(receiverClass)) {
      try {
        MethodHandle mh =
            LOOKUP.findVirtual(List.class, "get", MethodType.methodType(Object.class, int.class));
        return AccessLink.ok(receiverClass, mh, "[]");
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    if (Map.class.isAssignableFrom(receiverClass)) {
      try {
        MethodHandle mh =
            LOOKUP.findVirtual(Map.class, "get", MethodType.methodType(Object.class, Object.class));
        return AccessLink.ok(receiverClass, mh, "[]");
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    if (receiverClass.isArray()) {
      try {
        MethodHandle mh =
            LOOKUP.findStatic(
                Array.class, "get", MethodType.methodType(Object.class, Object.class, int.class));
        return AccessLink.ok(receiverClass, mh, "[]");
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    return AccessLink.missing(receiverClass, "[]");
  }

  private AccessLink linkIndexSet(Class<?> receiverClass, LinkerAccessPolicy policy) {
    if (List.class.isAssignableFrom(receiverClass)) {
      try {
        MethodHandle mh =
            LOOKUP.findVirtual(
                List.class, "set", MethodType.methodType(Object.class, int.class, Object.class));
        return AccessLink.ok(receiverClass, mh, "[]");
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    if (Map.class.isAssignableFrom(receiverClass)) {
      try {
        MethodHandle mh =
            LOOKUP.findVirtual(
                Map.class, "put", MethodType.methodType(Object.class, Object.class, Object.class));
        return AccessLink.ok(receiverClass, mh, "[]");
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    if (receiverClass.isArray()) {
      try {
        MethodHandle mh =
            LOOKUP.findStatic(
                Array.class,
                "set",
                MethodType.methodType(void.class, Object.class, int.class, Object.class));
        return AccessLink.ok(receiverClass, mh, "[]");
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // fall through
      }
    }

    return AccessLink.missing(receiverClass, "[]");
  }

  private AccessLink checkMethodAndCreateLink(
      Class<?> receiverClass, Method method, String propertyName, LinkerAccessPolicy policy) {
    if (!policy.isMethodPermitted(receiverClass, method)) {
      return AccessLink.denied(
          receiverClass, propertyName, "method " + method.getName() + " is denied by policy");
    }
    Method accessible = findAccessibleMethod(method);
    Method toUnreflect = accessible != null ? accessible : method;
    try {
      MethodHandle mh =
          LOOKUP.unreflect(toUnreflect).asType(MethodType.methodType(Object.class, Object.class));
      return AccessLink.ok(receiverClass, mh, propertyName);
    } catch (IllegalAccessException e) {
      return AccessLink.denied(
          receiverClass, propertyName, "inaccessible method " + method.getName());
    }
  }

  private static Method findAccessibleMethod(Method m) {
    if (m == null) {
      return null;
    }
    Class<?> declaringClass = m.getDeclaringClass();
    if (Modifier.isPublic(declaringClass.getModifiers())) {
      return m;
    }
    for (Class<?> intf : declaringClass.getInterfaces()) {
      if (Modifier.isPublic(intf.getModifiers())) {
        try {
          Method intfMethod = intf.getMethod(m.getName(), m.getParameterTypes());
          Method acc = findAccessibleMethod(intfMethod);
          if (acc != null) {
            return acc;
          }
        } catch (NoSuchMethodException ignored) {
        }
      }
    }
    Class<?> superClass = declaringClass.getSuperclass();
    while (superClass != null) {
      if (Modifier.isPublic(superClass.getModifiers())) {
        try {
          return superClass.getMethod(m.getName(), m.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
        }
      }
      for (Class<?> intf : superClass.getInterfaces()) {
        if (Modifier.isPublic(intf.getModifiers())) {
          try {
            Method intfMethod = intf.getMethod(m.getName(), m.getParameterTypes());
            Method acc = findAccessibleMethod(intfMethod);
            if (acc != null) {
              return acc;
            }
          } catch (NoSuchMethodException ignored) {
          }
        }
      }
      superClass = superClass.getSuperclass();
    }
    return null;
  }

  private static Method findPublicZeroArgMethod(Class<?> clazz, String name) {
    for (Method m : clazz.getMethods()) {
      if (m.getName().equals(name) && m.getParameterCount() == 0) {
        return m;
      }
    }
    return null;
  }

  private static Method findPublicOneArgMethod(Class<?> clazz, String name) {
    for (Method m : clazz.getMethods()) {
      if (m.getName().equals(name) && m.getParameterCount() == 1) {
        return m;
      }
    }
    return null;
  }

  private static Field findPublicField(Class<?> clazz, String name) {
    try {
      Field f = clazz.getField(name);
      return Modifier.isPublic(f.getModifiers()) ? f : null;
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }
}
