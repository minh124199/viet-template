package io.github.minh124199.viettemplate.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Default implementation of {@link SecurityViewFactory}. */
final class DefaultSecurityViewFactory implements SecurityViewFactory {

  private final AuthenticationTrustResolver trustResolver;

  DefaultSecurityViewFactory() {
    this(new AuthenticationTrustResolverImpl());
  }

  DefaultSecurityViewFactory(AuthenticationTrustResolver trustResolver) {
    this.trustResolver =
        trustResolver != null ? trustResolver : new AuthenticationTrustResolverImpl();
  }

  @Override
  public SecurityView create(Authentication authentication, HttpServletRequest request) {
    if (authentication == null) {
      return DefaultSecurityView.ANONYMOUS;
    }
    if (this.trustResolver.isAnonymous(authentication)) {
      return DefaultSecurityView.ANONYMOUS;
    }
    if (!authentication.isAuthenticated()) {
      return DefaultSecurityView.ANONYMOUS;
    }

    String name = authentication.getName() != null ? authentication.getName() : "";
    Set<String> authorities = new LinkedHashSet<>();
    if (authentication.getAuthorities() != null) {
      for (GrantedAuthority ga : authentication.getAuthorities()) {
        if (ga != null && ga.getAuthority() != null) {
          authorities.add(ga.getAuthority());
        }
      }
    }

    return new DefaultSecurityView(true, false, name, authorities);
  }
}
