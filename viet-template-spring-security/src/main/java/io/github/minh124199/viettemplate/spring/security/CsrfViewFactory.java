package io.github.minh124199.viettemplate.spring.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SPI factory for extracting and constructing a {@link CsrfView} from an HTTP servlet request.
 *
 * <p>Applications can register custom factory beans in the Spring ApplicationContext to customize
 * CSRF token exposure.
 */
@FunctionalInterface
public interface CsrfViewFactory {

  /**
   * Extracts CSRF token metadata from the request and constructs a {@link CsrfView}.
   *
   * @param request current HTTP servlet request
   * @return csrf view facade, or {@code null} if CSRF is disabled or no token is available
   */
  CsrfView create(HttpServletRequest request);

  /** Returns a default factory instance using standard Spring Security CSRF conventions. */
  static CsrfViewFactory defaultFactory() {
    return new DefaultCsrfViewFactory();
  }
}
