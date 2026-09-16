package io.github.minh124199.viettemplate.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.ContextCollisionException;
import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.spring.web.servlet.SpringRenderAttributes;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SpringSecurityRenderContextContributorTest {

  private InMemoryTemplateRepository repository;
  private SpringSecurityRenderContextContributor contributor;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    this.repository = InMemoryTemplateRepository.create();
    this.contributor = new SpringSecurityRenderContextContributor();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private TemplateEngine createEngine(ContextCollisionPolicy collisionPolicy) {
    return TemplateEngine.builder()
        .repository(this.repository)
        .contextCollisionPolicy(collisionPolicy)
        .addContextContributor(this.contributor)
        .build();
  }

  private static final java.util.concurrent.atomic.AtomicInteger TEMPLATE_COUNTER =
      new java.util.concurrent.atomic.AtomicInteger();

  private String render(
      TemplateEngine engine,
      String templateContent,
      Map<String, Object> model,
      MockHttpServletRequest request)
      throws IOException {
    TemplateId templateId = TemplateId.of("test_" + TEMPLATE_COUNTER.incrementAndGet() + ".vtl");
    this.repository.put(templateId, templateContent);

    Map<String, Object> attributes =
        request != null ? Map.of(SpringRenderAttributes.SERVLET_REQUEST, request) : Map.of();
    RenderRequest renderRequest = RenderRequest.of(templateId, RenderContext.of(model), attributes);

    StringTemplateOutput output = new StringTemplateOutput();
    engine.render(renderRequest, output);
    return output.toString();
  }

  @Test
  @DisplayName("Unauthenticated request provides anonymous SecurityView with authenticated=false")
  void unauthenticatedRequestProvidesAnonymousView() throws Exception {
    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template =
        "#if($security.authenticated)Logged in: $security.name#else Anonymous (anon:"
            + " $security.anonymous)#end";

    String result = render(engine, template, Map.of(), new MockHttpServletRequest());
    assertThat(result.trim()).isEqualTo("Anonymous (anon: true)");
  }

  @Test
  @DisplayName("AnonymousAuthenticationToken produces normalized anonymous SecurityView")
  void anonymousAuthenticationTokenHandling() throws Exception {
    Authentication anonAuth =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    SecurityContextHolder.getContext().setAuthentication(anonAuth);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template =
        "#if($security.authenticated)Auth#else"
            + " Anon#end|auth:$security.authenticated|name:$security.name|hasAnonAuth:$security.hasAuthority('ROLE_ANONYMOUS')";

    String result = render(engine, template, Map.of(), new MockHttpServletRequest());
    assertThat(result.trim()).isEqualTo("Anon|auth:false|name:|hasAnonAuth:false");
  }

  @Test
  @DisplayName("Authenticated user: exposes name, authorities, and authority query methods")
  void authenticatedUserPropertiesAndMethods() throws Exception {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "secret", AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_DEVELOPER"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template =
        "Welcome, $security.name! "
            + "#if($security.hasAuthority('ROLE_USER'))HasUser #end"
            + "#if($security.hasAuthority('ROLE_ADMIN'))HasAdmin #end"
            + "#if($security.hasAnyAuthority('ROLE_ADMIN', 'ROLE_DEVELOPER'))HasDevOrAdmin #end "
            + "AuthCount:$security.authorities.size()";

    String result = render(engine, template, Map.of(), new MockHttpServletRequest());
    assertThat(result).isEqualTo("Welcome, alice! HasUser HasDevOrAdmin  AuthCount:2");
  }

  @Test
  @DisplayName("Malicious principal name is automatically HTML-escaped by template engine")
  void adversarialPrincipalNameHtmlEscaping() throws Exception {
    String maliciousName = "<script>alert('xss')</script>";
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            maliciousName, "secret", AuthorityUtils.createAuthorityList("ROLE_USER"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template = "Hello, $security.name!";

    String result = render(engine, template, Map.of(), new MockHttpServletRequest());
    assertThat(result).isEqualTo("Hello, " + maliciousName + "!");

    // Verify security facade returns standard String (not SafeHtml/SafeContent),
    // ensuring normal template/runtime escaping rules escape adversarial content
    StringTemplateOutput escapedOutput = new StringTemplateOutput();
    io.github.minh124199.viettemplate.runtime.HtmlTextEscaper.INSTANCE.escape(
        result, escapedOutput);
    assertThat(escapedOutput.toString())
        .doesNotContain("<script>")
        .contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
  }

  @Test
  @DisplayName("CSRF token is exposed under $csrf when present on the request")
  void csrfTokenContributedWhenPresent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "sample-token-uuid-12345");
    request.setAttribute(CsrfToken.class.getName(), token);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template =
        "<form method=\"post\">\n"
            + "  <input type=\"hidden\" name=\"$csrf.parameterName\" value=\"$csrf.token\">\n"
            + "</form>";

    String result = render(engine, template, Map.of(), request);
    assertThat(result).contains("name=\"_csrf\"").contains("value=\"sample-token-uuid-12345\"");
  }

  @Test
  @DisplayName("CSRF helper is absent ($csrf evaluates to null) when CSRF token is not present")
  void csrfTokenAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();

    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template = "#if($csrf)CsrfPresent#else CsrfAbsent#end";

    String result = render(engine, template, Map.of(), request);
    assertThat(result.trim()).isEqualTo("CsrfAbsent");
  }

  @Test
  @DisplayName(
      "Context collision: MODEL_WINS preserves user-supplied model value over security helper")
  void modelWinsOnCollision() throws Exception {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "secret", AuthorityUtils.createAuthorityList("ROLE_USER"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.MODEL_WINS);
    String template = "$security";

    // Application explicitly binds "security" to its own custom string
    String result =
        render(
            engine,
            template,
            Map.of("security", "CustomAppSecurity"),
            new MockHttpServletRequest());
    assertThat(result).isEqualTo("CustomAppSecurity");
  }

  @Test
  @DisplayName("Context collision: ERROR_ON_COLLISION throws ContextCollisionException")
  void errorOnCollisionThrows() throws Exception {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "secret", AuthorityUtils.createAuthorityList("ROLE_USER"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.ERROR_ON_COLLISION);
    String template = "$security.name";

    assertThatThrownBy(
            () ->
                render(
                    engine,
                    template,
                    Map.of("security", "CustomAppSecurity"),
                    new MockHttpServletRequest()))
        .isInstanceOf(ContextCollisionException.class);
  }

  @Test
  @DisplayName("Context collision: CONTRIBUTOR_WINS preserves contributor security helper")
  void contributorWinsOnCollision() throws Exception {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "secret", AuthorityUtils.createAuthorityList("ROLE_USER"));
    SecurityContextHolder.getContext().setAuthentication(auth);

    TemplateEngine engine = createEngine(ContextCollisionPolicy.CONTRIBUTOR_WINS);
    String template = "$security.name";

    String result =
        render(
            engine,
            template,
            Map.of("security", "CustomAppSecurity"),
            new MockHttpServletRequest());
    assertThat(result).isEqualTo("alice");
  }
}
