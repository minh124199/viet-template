package io.github.minh124199.viettemplate.spring.security;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.spring.web.servlet.SpringRenderAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

/**
 * {@link RenderContextContributor} that normalizes Spring Security authentication and CSRF state
 * into safe, immutable presentation facades for template rendering.
 *
 * <h2>Variables Contributed</h2>
 *
 * <ul>
 *   <li>{@code $security}: An immutable {@link SecurityView} representing authentication status,
 *       principal name, and granted authorities. Always present during rendering.
 *   <li>{@code $csrf}: An immutable {@link CsrfView} representing the CSRF token, parameter name,
 *       and header name. Contributed only when a valid CSRF token is available on the request.
 * </ul>
 *
 * <h2>Thread Safety &amp; Statelessness</h2>
 *
 * <p>Instances of this class are long-lived, thread-safe, and request-stateless singletons. All
 * request-specific metadata is extracted on the call stack inside {@link
 * #contribute(ContributorContext, RenderRequest)}, ensuring zero cross-request contamination.
 */
public final class SpringSecurityRenderContextContributor implements RenderContextContributor {

  public static final String DEFAULT_SECURITY_VARIABLE_NAME = "security";
  public static final String DEFAULT_CSRF_VARIABLE_NAME = "csrf";

  /**
   * Optional integration attribute key used to pass an explicit {@link Authentication} in {@link
   * RenderRequest#attributes()}.
   */
  public static final String AUTHENTICATION_ATTRIBUTE =
      "viet-template.spring.security.authentication";

  /**
   * Optional integration attribute key used to pass an explicit {@link SecurityContext} in {@link
   * RenderRequest#attributes()}.
   */
  public static final String SECURITY_CONTEXT_ATTRIBUTE =
      "viet-template.spring.security.security-context";

  private final SecurityViewFactory securityViewFactory;
  private final CsrfViewFactory csrfViewFactory;
  private final SecurityContextHolderStrategy securityContextHolderStrategy;
  private final String securityVariableName;
  private final String csrfVariableName;

  public SpringSecurityRenderContextContributor() {
    this(null, null, null, null, null);
  }

  public SpringSecurityRenderContextContributor(
      SecurityViewFactory securityViewFactory, CsrfViewFactory csrfViewFactory) {
    this(securityViewFactory, csrfViewFactory, null, null, null);
  }

  public SpringSecurityRenderContextContributor(
      SecurityViewFactory securityViewFactory,
      CsrfViewFactory csrfViewFactory,
      AuthenticationTrustResolver trustResolver) {
    this(
        securityViewFactory != null
            ? securityViewFactory
            : new DefaultSecurityViewFactory(trustResolver),
        csrfViewFactory != null ? csrfViewFactory : new DefaultCsrfViewFactory(),
        null,
        null,
        null);
  }

  public SpringSecurityRenderContextContributor(
      SecurityViewFactory securityViewFactory,
      CsrfViewFactory csrfViewFactory,
      SecurityContextHolderStrategy securityContextHolderStrategy,
      String securityVariableName,
      String csrfVariableName) {
    this.securityViewFactory =
        securityViewFactory != null ? securityViewFactory : new DefaultSecurityViewFactory();
    this.csrfViewFactory = csrfViewFactory != null ? csrfViewFactory : new DefaultCsrfViewFactory();
    this.securityContextHolderStrategy =
        securityContextHolderStrategy != null
            ? securityContextHolderStrategy
            : SecurityContextHolder.getContextHolderStrategy();
    this.securityVariableName =
        securityVariableName != null ? securityVariableName : DEFAULT_SECURITY_VARIABLE_NAME;
    this.csrfVariableName =
        csrfVariableName != null ? csrfVariableName : DEFAULT_CSRF_VARIABLE_NAME;
  }

  @Override
  public void contribute(ContributorContext context, RenderRequest request) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(request, "request must not be null");

    HttpServletRequest servletRequest = null;
    Object reqObj = request.attributes().get(SpringRenderAttributes.SERVLET_REQUEST);
    if (reqObj instanceof HttpServletRequest r) {
      servletRequest = r;
    }

    Authentication authentication = resolveAuthentication(request, servletRequest);
    SecurityView securityView = this.securityViewFactory.create(authentication, servletRequest);
    context.put(this.securityVariableName, securityView);

    if (servletRequest != null) {
      CsrfView csrfView = this.csrfViewFactory.create(servletRequest);
      if (csrfView != null) {
        context.put(this.csrfVariableName, csrfView);
      }
    }
  }

  private Authentication resolveAuthentication(
      RenderRequest request, HttpServletRequest servletRequest) {
    // 1. Explicit request attributes take first priority
    Object authAttr = request.attributes().get(AUTHENTICATION_ATTRIBUTE);
    if (authAttr instanceof Authentication a) {
      return a;
    }

    Object secCtxAttr = request.attributes().get(SECURITY_CONTEXT_ATTRIBUTE);
    if (secCtxAttr instanceof SecurityContext sc && sc.getAuthentication() != null) {
      return sc.getAuthentication();
    }

    // 2. Strategy-managed SecurityContext (thread-local or configured holder strategy)
    SecurityContext holderContext = this.securityContextHolderStrategy.getContext();
    if (holderContext != null && holderContext.getAuthentication() != null) {
      return holderContext.getAuthentication();
    }

    // 3. Fall back to HttpServletRequest attributes populated by Spring Security filters
    if (servletRequest != null) {
      if (servletRequest.getUserPrincipal() instanceof Authentication a) {
        return a;
      }
      Object reqAttr =
          servletRequest.getAttribute(
              RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME);
      if (reqAttr instanceof SecurityContext sc && sc.getAuthentication() != null) {
        return sc.getAuthentication();
      }
      Object sessionAttr =
          servletRequest.getAttribute(
              HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      if (sessionAttr instanceof SecurityContext sc && sc.getAuthentication() != null) {
        return sc.getAuthentication();
      }
    }

    return null;
  }

  public SecurityViewFactory getSecurityViewFactory() {
    return this.securityViewFactory;
  }

  public CsrfViewFactory getCsrfViewFactory() {
    return this.csrfViewFactory;
  }

  public String getSecurityVariableName() {
    return this.securityVariableName;
  }

  public String getCsrfVariableName() {
    return this.csrfVariableName;
  }
}
