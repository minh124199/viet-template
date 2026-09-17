package io.github.minh124199.viettemplate.spring.security.compat;

import io.github.minh124199.viettemplate.api.ContributorContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.spring.security.CsrfView;
import io.github.minh124199.viettemplate.spring.security.CsrfViewFactory;
import io.github.minh124199.viettemplate.spring.security.SecurityView;
import io.github.minh124199.viettemplate.spring.security.SecurityViewFactory;
import io.github.minh124199.viettemplate.spring.security.SpringSecurityRenderContextContributor;
import io.github.minh124199.viettemplate.spring.web.servlet.SpringRenderAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/**
 * Standalone verification runner that validates runtime binary compatibility of
 * compiled viet-template-spring-security classes against specific Spring Security classpaths.
 */
public final class SecurityCompatibilityVerifier {

  public static void main(String[] args) throws Exception {
    String expectedVersion = args.length > 0 ? args[0] : "unknown";
    System.out.println("Starting compatibility verification for Spring Security: " + expectedVersion);

    // 1. Verify class linkage
    verifyClassLinkage();

    // 2. Verify SecurityView and SecurityViewFactory
    verifySecurityViewFactory();

    // 3. Verify CsrfView and CsrfViewFactory
    verifyCsrfViewFactory();

    // 4. Verify SpringSecurityRenderContextContributor end-to-end
    verifyContributor();

    System.out.println("[PASS] Spring Security " + expectedVersion + " binary compatibility verified successfully.");
  }

  private static void verifyClassLinkage() throws ClassNotFoundException {
    String[] requiredClasses = {
      "io.github.minh124199.viettemplate.spring.security.SecurityView",
      "io.github.minh124199.viettemplate.spring.security.DefaultSecurityView",
      "io.github.minh124199.viettemplate.spring.security.SecurityViewFactory",
      "io.github.minh124199.viettemplate.spring.security.DefaultSecurityViewFactory",
      "io.github.minh124199.viettemplate.spring.security.CsrfView",
      "io.github.minh124199.viettemplate.spring.security.DefaultCsrfView",
      "io.github.minh124199.viettemplate.spring.security.CsrfViewFactory",
      "io.github.minh124199.viettemplate.spring.security.DefaultCsrfViewFactory",
      "io.github.minh124199.viettemplate.spring.security.SpringSecurityRenderContextContributor"
    };

    for (String cls : requiredClasses) {
      Class<?> c = Class.forName(cls);
      if (c == null) {
        throw new IllegalStateException("Failed to load class: " + cls);
      }
    }
  }

  private static void verifySecurityViewFactory() {
    SecurityViewFactory factory = SecurityViewFactory.defaultFactory();

    // Null authentication -> Anonymous view
    SecurityView anonView = factory.create(null, null);
    check(!anonView.isAuthenticated(), "anonView should not be authenticated");
    check(anonView.isAnonymous(), "anonView should be anonymous");
    check("".equals(anonView.getName()), "anonView name should be empty");
    check(anonView.getAuthorities().isEmpty(), "anonView authorities should be empty");
    check(!anonView.hasAuthority("ROLE_USER"), "anonView should not have authority");
    check(!anonView.hasAnyAuthority("ROLE_USER", "ROLE_ADMIN"), "anonView hasAnyAuthority should be false");

    // Authenticated user
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "test-user", "cred", AuthorityUtils.createAuthorityList("ROLE_USER", "SCOPE_read"));
    SecurityView userView = factory.create(auth, null);
    check(userView.isAuthenticated(), "userView should be authenticated");
    check(!userView.isAnonymous(), "userView should not be anonymous");
    check("test-user".equals(userView.getName()), "userView name should be test-user");
    check(userView.getAuthorities().equals(Set.of("ROLE_USER", "SCOPE_read")), "userView authorities mismatch");
    check(userView.hasAuthority("ROLE_USER"), "userView should have ROLE_USER");
    check(userView.hasAuthority("SCOPE_read"), "userView should have SCOPE_read");
    check(!userView.hasAuthority("ROLE_ADMIN"), "userView should not have ROLE_ADMIN");
    check(userView.hasAnyAuthority("ROLE_ADMIN", "ROLE_USER"), "userView hasAnyAuthority should be true");

    // Spring Security AnonymousAuthenticationToken
    Authentication anonAuth =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    SecurityView springAnonView = factory.create(anonAuth, null);
    check(!springAnonView.isAuthenticated(), "springAnonView should not be authenticated");
    check(springAnonView.isAnonymous(), "springAnonView should be anonymous");
  }

  private static void verifyCsrfViewFactory() {
    CsrfViewFactory factory = CsrfViewFactory.defaultFactory();

    // Null request -> null view
    check(factory.create(null) == null, "null request should return null CsrfView");

    // Request with CSRF token
    Map<String, Object> reqAttrs = new HashMap<>();
    HttpServletRequest request = createMockRequest(reqAttrs);

    CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-value-12345");
    reqAttrs.put(CsrfToken.class.getName(), token);

    CsrfView csrfView = factory.create(request);
    check(csrfView != null, "CsrfView should not be null when token present");
    check("token-value-12345".equals(csrfView.getToken()), "csrf token mismatch");
    check("_csrf".equals(csrfView.getParameterName()), "csrf parameterName mismatch");
    check("X-CSRF-TOKEN".equals(csrfView.getHeaderName()), "csrf headerName mismatch");
  }

  private static void verifyContributor() {
    SpringSecurityRenderContextContributor contributor = new SpringSecurityRenderContextContributor();
    check("security".equals(contributor.getSecurityVariableName()), "security var name mismatch");
    check("csrf".equals(contributor.getCsrfVariableName()), "csrf var name mismatch");

    Map<String, Object> reqAttrs = new HashMap<>();
    CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "contributor-csrf-token");
    reqAttrs.put(CsrfToken.class.getName(), token);

    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "admin-user", "n/a", AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
    reqAttrs.put("userPrincipal", auth);

    HttpServletRequest request = createMockRequest(reqAttrs);

    Map<String, Object> renderAttributes = Map.of(SpringRenderAttributes.SERVLET_REQUEST, request);
    RenderRequest renderRequest =
        RenderRequest.of(TemplateId.of("compat-test.vtl"), RenderContext.empty(), renderAttributes);

    SimpleContributorContext context = new SimpleContributorContext();
    contributor.contribute(context, renderRequest);

    check(context.contains("security"), "context should contain security");
    check(context.contains("csrf"), "context should contain csrf");

    SecurityView secView = (SecurityView) context.get("security");
    check(secView.isAuthenticated(), "secView must be authenticated");
    check("admin-user".equals(secView.getName()), "secView name mismatch");
    check(secView.hasAuthority("ROLE_ADMIN"), "secView authority mismatch");

    CsrfView csrfView = (CsrfView) context.get("csrf");
    check("contributor-csrf-token".equals(csrfView.getToken()), "csrfView token mismatch");
    check("_csrf".equals(csrfView.getParameterName()), "csrfView parameterName mismatch");
    check("X-XSRF-TOKEN".equals(csrfView.getHeaderName()), "csrfView headerName mismatch");
  }

  private static HttpServletRequest createMockRequest(Map<String, Object> attributes) {
    return (HttpServletRequest)
        Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[] {HttpServletRequest.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getAttribute".equals(name) && args != null && args.length == 1) {
                return attributes.get(args[0]);
              }
              if ("getUserPrincipal".equals(name)) {
                return attributes.get("userPrincipal");
              }
              if ("getSession".equals(name)) {
                return null;
              }
              return null;
            });
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError("Verification check failed: " + message);
    }
  }

  private static final class SimpleContributorContext implements ContributorContext {
    private final Map<String, Object> storage = new HashMap<>();

    @Override
    public ContributorContext put(String key, Object value) {
      storage.put(key, value);
      return this;
    }

    @Override
    public ContributorContext putAll(Map<String, ?> entries) {
      storage.putAll(entries);
      return this;
    }

    @Override
    public boolean contains(String key) {
      return storage.containsKey(key);
    }

    @Override
    public Object get(String key) {
      return storage.get(key);
    }
  }
}
