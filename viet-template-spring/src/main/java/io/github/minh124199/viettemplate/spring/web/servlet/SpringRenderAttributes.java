package io.github.minh124199.viettemplate.spring.web.servlet;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Stable, namespaced attribute keys used to pass trusted Spring MVC integration metadata into
 * {@link RenderRequest#attributes()}.
 *
 * <h2>Security &amp; Isolation Guarantees</h2>
 *
 * <p>Values stored under these keys are trusted framework/integration metadata. They are carried
 * exclusively inside {@link RenderRequest#attributes()} and must <em>never</em> be automatically
 * exposed as template variables in {@link RenderContext}. Template authors must not be granted
 * direct access to raw servlet infrastructure (e.g. {@code $request}, {@code $response}, or {@code
 * $session}) by default.
 *
 * <p>Integration extensions and {@link
 * io.github.minh124199.viettemplate.api.RenderContextContributor} implementations (such as Spring
 * Security integration) retrieve these attributes to inspect request state and construct narrow,
 * immutable, safe presentation facades.
 */
public final class SpringRenderAttributes {

  /**
   * Attribute key identifying the active {@link HttpServletRequest}.
   *
   * <p>Value type: {@link HttpServletRequest}.
   */
  public static final String SERVLET_REQUEST = "viet-template.spring.servlet.request";

  private SpringRenderAttributes() {}
}
