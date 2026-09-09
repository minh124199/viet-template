package io.github.minh124199.viettemplate.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Framework-neutral classifier identifying sensitive container, framework, or runtime objects that
 * should not be directly exposed to template execution.
 *
 * <p>Identifies types by qualified class and interface names without introducing compile-time or
 * runtime dependencies on optional frameworks (such as Spring Framework or Jakarta Servlet).
 */
public final class SensitiveObjectClassifier {

  private static final Set<String> DEFAULT_SENSITIVE_TYPE_NAMES =
      Set.of(
          // Spring container & context
          "org.springframework.context.ApplicationContext",
          "org.springframework.beans.factory.BeanFactory",
          "org.springframework.beans.factory.ListableBeanFactory",
          "org.springframework.beans.factory.HierarchicalBeanFactory",
          "org.springframework.core.env.Environment",
          "org.springframework.core.env.PropertyResolver",
          "org.springframework.security.core.context.SecurityContext",
          // Servlet request, response, and session
          "jakarta.servlet.ServletRequest",
          "jakarta.servlet.ServletResponse",
          "jakarta.servlet.http.HttpServletRequest",
          "jakarta.servlet.http.HttpServletResponse",
          "jakarta.servlet.http.HttpSession",
          "jakarta.servlet.ServletContext",
          "javax.servlet.ServletRequest",
          "javax.servlet.ServletResponse",
          "javax.servlet.http.HttpServletRequest",
          "javax.servlet.http.HttpServletResponse",
          "javax.servlet.http.HttpSession",
          "javax.servlet.ServletContext",
          // Security contexts
          "java.security.AccessControlContext",
          "javax.security.auth.Subject");

  private static final SensitiveObjectClassifier DEFAULT =
      new SensitiveObjectClassifier(DEFAULT_SENSITIVE_TYPE_NAMES);

  private final Set<String> sensitiveTypeNames;

  public SensitiveObjectClassifier(Set<String> sensitiveTypeNames) {
    Objects.requireNonNull(sensitiveTypeNames, "sensitiveTypeNames must not be null");
    this.sensitiveTypeNames = Collections.unmodifiableSet(new HashSet<>(sensitiveTypeNames));
  }

  public static SensitiveObjectClassifier standard() {
    return DEFAULT;
  }

  /**
   * Checks whether the given fully qualified class or interface name is in the default sensitive
   * type registry.
   */
  public static boolean isSensitiveClassName(String className) {
    return DEFAULT.sensitiveTypeNames.contains(className);
  }

  /**
   * Checks whether the given class (or any of its superclasses or implemented interfaces) matches a
   * sensitive framework type name.
   *
   * @param clazz class to inspect
   * @return {@code true} if sensitive, {@code false} otherwise
   */
  public boolean isSensitive(Class<?> clazz) {
    if (clazz == null) {
      return false;
    }
    return checkHierarchy(clazz, new HashSet<>());
  }

  /**
   * Checks whether the runtime class of the specified target object is classified as sensitive.
   *
   * @param target object to inspect
   * @return {@code true} if sensitive, {@code false} otherwise
   */
  public boolean isSensitive(Object target) {
    return target != null && isSensitive(target.getClass());
  }

  public Set<String> sensitiveTypeNames() {
    return sensitiveTypeNames;
  }

  private boolean checkHierarchy(Class<?> clazz, Set<Class<?>> visited) {
    if (clazz == null || !visited.add(clazz)) {
      return false;
    }

    if (sensitiveTypeNames.contains(clazz.getName())) {
      return true;
    }

    for (Class<?> intf : clazz.getInterfaces()) {
      if (checkHierarchy(intf, visited)) {
        return true;
      }
    }

    return checkHierarchy(clazz.getSuperclass(), visited);
  }
}
