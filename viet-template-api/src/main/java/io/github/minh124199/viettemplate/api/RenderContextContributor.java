package io.github.minh124199.viettemplate.api;

/**
 * SPI for enriching a {@link RenderContext} prior to template rendering.
 *
 * <p>Used by application frameworks (such as Spring MVC view adapters or Velocity Tools
 * integrations) to contribute request-local helpers, toolbox utilities, and request attributes.
 */
@FunctionalInterface
public interface RenderContextContributor {

  /**
   * Contributes variables, helpers, or attributes to the render context.
   *
   * @param context contributor context accumulator
   * @param request current render request metadata
   */
  void contribute(ContributorContext context, RenderRequest request);
}
