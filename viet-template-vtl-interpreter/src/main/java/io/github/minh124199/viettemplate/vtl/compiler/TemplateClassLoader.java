package io.github.minh124199.viettemplate.vtl.compiler;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Dedicated generation-level ClassLoader for dynamically defined compiled template classes.
 *
 * <p>Enables safe ClassLoader isolation, distinct security domains, and full garbage collection
 * upon template engine or generation invalidation without leaking application ClassLoaders.
 */
public final class TemplateClassLoader extends ClassLoader {

  private final ConcurrentMap<String, Class<?>> definedClasses = new ConcurrentHashMap<>();

  public TemplateClassLoader(ClassLoader parent) {
    super(parent != null ? parent : TemplateClassLoader.class.getClassLoader());
  }

  public Class<? extends CompiledTemplate> defineTemplateClass(
      String className, byte[] classBytes) {
    Objects.requireNonNull(className, "className must not be null");
    Objects.requireNonNull(classBytes, "classBytes must not be null");

    Class<?> clazz =
        definedClasses.computeIfAbsent(
            className, name -> defineClass(name, classBytes, 0, classBytes.length));

    return clazz.asSubclass(CompiledTemplate.class);
  }

  public Class<?> defineRawClass(String className, byte[] classBytes) {
    Objects.requireNonNull(className, "className must not be null");
    Objects.requireNonNull(classBytes, "classBytes must not be null");

    return definedClasses.computeIfAbsent(
        className, name -> defineClass(name, classBytes, 0, classBytes.length));
  }

  public int definedCount() {
    return definedClasses.size();
  }
}
