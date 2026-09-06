package io.github.minh124199.viettemplate.vtl.interpreter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Security policy governing reflection, property access, and method invocations in the interpreter.
 */
public interface VtlSecurityPolicy {

  boolean isClassPermitted(Class<?> clazz);

  boolean isMethodPermitted(Class<?> receiverClass, Method method);

  boolean isFieldPermitted(Class<?> receiverClass, Field field);

  static VtlSecurityPolicy standard() {
    return StandardSecurityPolicy.INSTANCE;
  }
}

final class StandardSecurityPolicy implements VtlSecurityPolicy {
  static final StandardSecurityPolicy INSTANCE = new StandardSecurityPolicy();

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

  private StandardSecurityPolicy() {}

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
    if (DENIED_METHOD_NAMES.contains(method.getName())) {
      return false;
    }
    return true;
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
}
