package io.github.minh124199.viettemplate.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * SPI factory for constructing a {@link SecurityView} from Spring Security request state.
 *
 * <p>Applications can register custom factory beans in the Spring ApplicationContext to customize
 * security view generation without replacing the contributor pipeline.
 */
@FunctionalInterface
public interface SecurityViewFactory {

  /**
   * Creates an immutable {@link SecurityView} for the given authentication and request.
   *
   * @param authentication current authentication, or {@code null}
   * @param request current HTTP servlet request, or {@code null}
   * @return immutable security view facade
   */
  SecurityView create(Authentication authentication, HttpServletRequest request);

  /** Returns a default factory instance using standard Spring Security conventions. */
  static SecurityViewFactory defaultFactory() {
    return new DefaultSecurityViewFactory();
  }

  /**
   * Returns a default factory instance using the provided {@link
   * org.springframework.security.authentication.AuthenticationTrustResolver}.
   */
  static SecurityViewFactory defaultFactory(
      org.springframework.security.authentication.AuthenticationTrustResolver trustResolver) {
    return new DefaultSecurityViewFactory(trustResolver);
  }
}
