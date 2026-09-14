package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Primary entrypoint contract for loading, compiling, and rendering templates.
 *
 * <p><strong>Thread Safety &amp; Lifecycle:</strong> {@code TemplateEngine} instances are
 * long-lived, thread-safe, and designed for concurrent multi-threaded sharing across application
 * runtimes. All template retrieval, compilation caching, and rendering operations can be safely
 * executed concurrently from multiple threads.
 *
 * <p>Implements {@link AutoCloseable} to allow graceful release of background resources (such as
 * filesystem watchers or thread pools) upon application shutdown.
 */
public interface TemplateEngine extends AutoCloseable {

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
   * Convenience method to render a screen template by name string directly to a {@link String}.
   *
   * @param screenName screen template name or path
   * @param context user model context
   * @return rendered template output string
   * @throws IOException on write failures
   */
  default String render(String screenName, RenderContext context) throws IOException {
    return render(TemplateId.normalize(screenName), context);
  }

  /**
   * Convenience method to render a screen template by identifier directly to a {@link String}.
   *
   * @param screenId normalized screen template identifier
   * @param context user model context
   * @return rendered template output string
   * @throws IOException on write failures
   */
  default String render(TemplateId screenId, RenderContext context) throws IOException {
    StringBuilder sb = new StringBuilder();
    render(
        screenId,
        context,
        new TemplateOutput() {
          @Override
          public void write(CharSequence v) {
            if (v != null) {
              sb.append(v);
            }
          }

          @Override
          public void write(CharSequence v, int s, int e) {
            if (v != null) {
              Objects.checkFromToIndex(s, e, v.length());
              sb.append(v, s, e);
            }
          }

          @Override
          public void write(char v) {
            sb.append(v);
          }

          @Override
          public void writeUtf8(byte[] b) {
            sb.append(new String(b, StandardCharsets.UTF_8));
          }

          @Override
          public void writeUtf8(byte[] b, int off, int len) {
            sb.append(new String(b, off, len, StandardCharsets.UTF_8));
          }

          @Override
          public void writeInt(int v) {
            sb.append(v);
          }

          @Override
          public void writeLong(long v) {
            sb.append(v);
          }

          @Override
          public void writeDouble(double v) {
            sb.append(v);
          }

          @Override
          public void writeBoolean(boolean v) {
            sb.append(v);
          }
        });
    return sb.toString();
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

  /**
   * Invalidates the specified template from the compilation cache.
   *
   * <p>The default implementation delegates to {@link #invalidateWithDependents(TemplateId)}.
   *
   * @param id template identifier
   */
  default void invalidate(TemplateId id) {
    invalidateWithDependents(id);
  }

  /** Invalidates all templates and compilation cache entries in this engine. */
  default void invalidateAll() {}

  /** Closes this template engine, releasing any underlying background resources. */
  @Override
  default void close() {}

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

    Builder memberAccessPolicy(MemberAccessPolicy policy);

    TemplateEngine build();
  }
}
