package io.github.minh124199.viettemplate.api;

/** Primary entrypoint contract for loading and managing templates. */
public interface TemplateEngine {

  /**
   * Retrieves and returns a loaded or compiled {@link Template} by its identifier.
   *
   * @param id normalized template identifier
   * @return the ready-to-render {@link Template}
   * @throws TemplateResourceException if the template cannot be located in the repository
   * @throws TemplateSecurityException if runtime compilation is rejected or access is unauthorized
   * @throws TemplateCompilationException if compilation fails
   */
  Template get(TemplateId id);

  /**
   * Convenience method to retrieve a template by name string.
   *
   * @param name template name or path
   * @return the ready-to-render {@link Template}
   */
  default Template get(String name) {
    return get(TemplateId.normalize(name));
  }

  /** Returns the underlying {@link TemplateRepository} associated with this engine. */
  TemplateRepository repository();

  /**
   * Returns whether runtime compilation is strictly rejected (e.g. in hardened production mode).
   */
  boolean rejectRuntimeCompilation();

  /** Creates a new fluent {@link Builder} to configure and instantiate a {@link TemplateEngine}. */
  static Builder builder() {
    return TemplateEngineProvider.load().createBuilder();
  }

  /** Fluent builder interface for configuring a {@link TemplateEngine}. */
  interface Builder {
    Builder repository(TemplateRepository repository);

    Builder rejectRuntimeCompilation(boolean reject);

    Builder maxCacheEntries(int maxEntries);

    Builder negativeCacheTtlMillis(long ttlMillis);

    Builder hotReload(boolean enabled);

    Builder watchDebounceMillis(long millis);

    TemplateEngine build();
  }
}
