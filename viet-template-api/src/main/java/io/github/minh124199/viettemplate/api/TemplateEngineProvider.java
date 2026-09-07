package io.github.minh124199.viettemplate.api;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Service provider interface used by {@link TemplateEngine#builder()} to discover and instantiate
 * engine implementations.
 */
public interface TemplateEngineProvider {

  TemplateEngine.Builder createBuilder();

  static TemplateEngineProvider load() {
    ServiceLoader<TemplateEngineProvider> loader = ServiceLoader.load(TemplateEngineProvider.class);
    Iterator<TemplateEngineProvider> it = loader.iterator();
    if (it.hasNext()) {
      return it.next();
    }
    // Fallback: Attempt direct reflection load of the reference VTL engine provider
    try {
      Class<?> cls =
          Class.forName("io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineProvider");
      return (TemplateEngineProvider) cls.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(
          "No TemplateEngineProvider found on classpath. Ensure viet-template-vtl-interpreter is"
              + " included.",
          e);
    }
  }
}
