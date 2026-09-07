package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.util.List;
import java.util.Set;

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

  /**
   * Renders a request through the full engine lifecycle (including context contributors and
   * optional layout rendering).
   *
   * @param request render request
   * @param output destination template output
   * @throws IOException on I/O write failures
   */
  void render(RenderRequest request, TemplateOutput output) throws IOException;

  /**
   * Renders a screen template through the full engine lifecycle (including context contributors and
   * optional layout rendering).
   *
   * @param screenId normalized screen template identifier
   * @param context user model context
   * @param output destination template output
   * @throws IOException on I/O write failures
   */
  default void render(TemplateId screenId, RenderContext context, TemplateOutput output)
      throws IOException {
    render(new RenderRequest(screenId, context, java.util.Map.of()), output);
  }

  /**
   * Convenience method to render a screen template by name string.
   *
   * @param screenName screen template name or path
   * @param context user model context
   * @param output destination template output
   * @throws IOException on I/O write failures
   */
  default void render(String screenName, RenderContext context, TemplateOutput output)
      throws IOException {
    render(TemplateId.normalize(screenName), context, output);
  }

  /**
   * Prepares an executable {@link LayoutRenderPlan} for the specified screen template and context.
   *
   * @param screenId screen template identifier
   * @param context current evaluation context
   * @return prepared layout render plan
   */
  LayoutRenderPlan prepareLayoutPlan(TemplateId screenId, RenderContext context);

  /** Returns the template dependency graph tracking dependency relationships in this engine. */
  TemplateDependencyGraph dependencyGraph();

  /**
   * Invalidates the specified template and all its transitive dependents from the compilation
   * cache.
   *
   * @param id template identifier
   * @return set of all invalidated template identifiers
   */
  Set<TemplateId> invalidateWithDependents(TemplateId id);

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

    Builder globalMacroLibraries(List<TemplateId> libraries);

    Builder globalMacroPrecedence(GlobalMacroPrecedence precedence);

    Builder addContextContributor(RenderContextContributor contributor);

    Builder contextContributors(List<RenderContextContributor> contributors);

    Builder contextCollisionPolicy(ContextCollisionPolicy policy);

    Builder layoutConfiguration(LayoutConfiguration configuration);

    TemplateEngine build();
  }
}
