package io.github.minh124199.viettemplate.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineBuilder;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SpringSecurityCrossSessionCsrfAndAuthIsolationTest {

  private InMemoryTemplateRepository repository;
  private SpringSecurityRenderContextContributor contributor;
  private TemplateEngine engine;
  private VietTemplateView view;
  private TemplateId templateId;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    this.repository = InMemoryTemplateRepository.create();
    this.contributor = new SpringSecurityRenderContextContributor();
    this.engine =
        TemplateEngine.builder()
            .repository(this.repository)
            .contextCollisionPolicy(ContextCollisionPolicy.MODEL_WINS)
            .addContextContributor(this.contributor)
            .build();
    this.templateId = TemplateId.of("session-test.vtl");
    this.repository.put(
        this.templateId,
        "name:$security.name|auth:$security.authenticated|admin:$security.hasAuthority('ROLE_ADMIN')|user:$security.hasAuthority('ROLE_USER')|csrf:#if($csrf)$csrf.token#else"
            + " NONE#end");
    this.view = new VietTemplateView(this.engine, this.templateId);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName(
      "Requirement 7 & 8: Multi-session CSRF token and authentication isolation across Alice, Bob,"
          + " and Anonymous")
  void multiSessionCsrfAndAuthIsolation() throws Exception {
    MockHttpSession sessionA = new MockHttpSession(null, "session-alice-111");
    MockHttpSession sessionB = new MockHttpSession(null, "session-bob-222");
    MockHttpSession sessionC = new MockHttpSession(null, "session-anon-333");

    CsrfToken tokenA = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "TOKEN_ALICE_SECURE_AAA");
    CsrfToken tokenB = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "TOKEN_BOB_SECURE_BBB");
    CsrfToken tokenC = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "TOKEN_ANON_SECURE_CCC");

    Authentication authAlice =
        new UsernamePasswordAuthenticationToken(
            "alice", "n/a", AuthorityUtils.createAuthorityList("ROLE_ADMIN", "ROLE_USER"));
    Authentication authBob =
        new UsernamePasswordAuthenticationToken(
            "bob", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
    Authentication authAnon =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    // 1. Render Session A (Alice)
    MockHttpServletRequest reqA1 = new MockHttpServletRequest();
    reqA1.setSession(sessionA);
    reqA1.setAttribute(CsrfToken.class.getName(), tokenA);
    reqA1.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authAlice));
    MockHttpServletResponse respA1 = new MockHttpServletResponse();
    this.view.render(Map.of(), reqA1, respA1);
    String resA1 = respA1.getContentAsString();
    assertThat(resA1)
        .isEqualTo("name:alice|auth:true|admin:true|user:true|csrf:TOKEN_ALICE_SECURE_AAA");

    // 2. Render Session B (Bob) - verify tokenA never appears in Session B
    MockHttpServletRequest reqB1 = new MockHttpServletRequest();
    reqB1.setSession(sessionB);
    reqB1.setAttribute(CsrfToken.class.getName(), tokenB);
    reqB1.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authBob));
    MockHttpServletResponse respB1 = new MockHttpServletResponse();
    this.view.render(Map.of(), reqB1, respB1);
    String resB1 = respB1.getContentAsString();
    assertThat(resB1)
        .isEqualTo("name:bob|auth:true|admin:false|user:true|csrf:TOKEN_BOB_SECURE_BBB")
        .doesNotContain("alice")
        .doesNotContain("TOKEN_ALICE_SECURE_AAA");

    // 3. Render Session A again - verify token remains stable within session
    MockHttpServletRequest reqA2 = new MockHttpServletRequest();
    reqA2.setSession(sessionA);
    reqA2.setAttribute(CsrfToken.class.getName(), tokenA);
    reqA2.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authAlice));
    MockHttpServletResponse respA2 = new MockHttpServletResponse();
    this.view.render(Map.of(), reqA2, respA2);
    String resA2 = respA2.getContentAsString();
    assertThat(resA2).isEqualTo(resA1).doesNotContain("bob").doesNotContain("TOKEN_BOB_SECURE_BBB");

    // 4. Render Session C (Anonymous) - verify no token or identity retained from previous requests
    MockHttpServletRequest reqC1 = new MockHttpServletRequest();
    reqC1.setSession(sessionC);
    reqC1.setAttribute(CsrfToken.class.getName(), tokenC);
    reqC1.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authAnon));
    MockHttpServletResponse respC1 = new MockHttpServletResponse();
    this.view.render(Map.of(), reqC1, respC1);
    String resC1 = respC1.getContentAsString();
    assertThat(resC1)
        .isEqualTo("name:|auth:false|admin:false|user:false|csrf:TOKEN_ANON_SECURE_CCC")
        .doesNotContain("alice")
        .doesNotContain("bob")
        .doesNotContain("TOKEN_ALICE_SECURE_AAA")
        .doesNotContain("TOKEN_BOB_SECURE_BBB");
  }

  @Test
  @DisplayName(
      "High-concurrency full-rendering isolation: shared TemplateEngine, shared VietTemplateView"
          + " across concurrent sessions")
  void concurrentFullPipelineIsolation() throws Exception {
    CsrfToken tokenAlice = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "CSRF_ALICE_TOKEN");
    CsrfToken tokenBob = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "CSRF_BOB_TOKEN");
    CsrfToken tokenAnon = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "CSRF_ANON_TOKEN");

    Authentication authAlice =
        new UsernamePasswordAuthenticationToken(
            "alice", "n/a", AuthorityUtils.createAuthorityList("ROLE_ADMIN", "ROLE_USER"));
    Authentication authBob =
        new UsernamePasswordAuthenticationToken(
            "bob", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
    Authentication authAnon =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    int threadCount = 30;
    int iterationsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              Random random = new Random(threadId * 31L);
              for (int i = 0; i < iterationsPerThread; i++) {
                int sessionChoice = random.nextInt(3);
                MockHttpServletRequest request = new MockHttpServletRequest();
                MockHttpServletResponse response = new MockHttpServletResponse();

                String expected;
                String forbiddenToken1;
                String forbiddenToken2;
                String forbiddenUser;

                if (sessionChoice == 0) {
                  request.setSession(new MockHttpSession(null, "sess-alice-" + threadId));
                  request.setAttribute(CsrfToken.class.getName(), tokenAlice);
                  request.setAttribute(
                      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                      new SecurityContextImpl(authAlice));
                  expected = "name:alice|auth:true|admin:true|user:true|csrf:CSRF_ALICE_TOKEN";
                  forbiddenToken1 = "CSRF_BOB_TOKEN";
                  forbiddenToken2 = "CSRF_ANON_TOKEN";
                  forbiddenUser = "bob";
                } else if (sessionChoice == 1) {
                  request.setSession(new MockHttpSession(null, "sess-bob-" + threadId));
                  request.setAttribute(CsrfToken.class.getName(), tokenBob);
                  request.setAttribute(
                      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                      new SecurityContextImpl(authBob));
                  expected = "name:bob|auth:true|admin:false|user:true|csrf:CSRF_BOB_TOKEN";
                  forbiddenToken1 = "CSRF_ALICE_TOKEN";
                  forbiddenToken2 = "CSRF_ANON_TOKEN";
                  forbiddenUser = "alice";
                } else {
                  request.setSession(new MockHttpSession(null, "sess-anon-" + threadId));
                  request.setAttribute(CsrfToken.class.getName(), tokenAnon);
                  request.setAttribute(
                      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                      new SecurityContextImpl(authAnon));
                  expected = "name:|auth:false|admin:false|user:false|csrf:CSRF_ANON_TOKEN";
                  forbiddenToken1 = "CSRF_ALICE_TOKEN";
                  forbiddenToken2 = "CSRF_BOB_TOKEN";
                  forbiddenUser = "alice";
                }

                view.render(Collections.emptyMap(), request, response);
                String result = response.getContentAsString();

                if (!result.equals(expected)) {
                  failureCount.incrementAndGet();
                }
                if (result.contains(forbiddenToken1)
                    || result.contains(forbiddenToken2)
                    || (sessionChoice != 0 && sessionChoice != 1 && result.contains("bob"))
                    || result.contains(forbiddenUser)) {
                  failureCount.incrementAndGet();
                }
              }
            } catch (Exception e) {
              failureCount.incrementAndGet();
            }
          });
    }

    startLatch.countDown();
    executor.shutdown();
    boolean completed = executor.awaitTermination(20, TimeUnit.SECONDS);

    assertThat(completed).isTrue();
    assertThat(failureCount.get()).isZero();
  }

  @Test
  @DisplayName(
      "Requirement 3: Adversarial principal and authority HTML injection prevention through full"
          + " template rendering")
  void adversarialHtmlEscapingThroughFullPipeline() throws Exception {
    TemplateEngine safeEngine =
        ((VtlTemplateEngineBuilder) TemplateEngine.builder())
            .repository(this.repository)
            .contextCollisionPolicy(ContextCollisionPolicy.MODEL_WINS)
            .addContextContributor(this.contributor)
            .interpreterOptions(
                VtlInterpreterOptions.builder().profile(VtlProfile.VTL_SAFE).build())
            .build();

    TemplateId escapeTemplateId = TemplateId.of("xss-test.vtl");
    this.repository.put(
        escapeTemplateId,
        "<h1>Hello, $security.name!</h1>\n"
            + "#if($csrf)\n"
            + "<input type=\"hidden\" name=\"$csrf.parameterName\" value=\"$csrf.token\" />\n"
            + "#end");
    VietTemplateView escapeView = new VietTemplateView(safeEngine, escapeTemplateId);

    String maliciousName = "<script>alert(\"xss\")</script>";
    String maliciousToken = "tok\"<>&'en";
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            maliciousName, "pass", AuthorityUtils.createAuthorityList("ROLE_<ADMIN&USER>"));
    CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", maliciousToken);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(CsrfToken.class.getName(), token);
    request.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(auth));

    MockHttpServletResponse response = new MockHttpServletResponse();
    escapeView.render(Map.of(), request, response);
    String renderedHtml = response.getContentAsString();

    // Security check: Verify raw malicious markup is NEVER rendered verbatim and HTML-escaped
    // tokens are present
    assertThat(renderedHtml)
        .doesNotContain("<script>")
        .doesNotContain("alert(\"xss\")</script>")
        .contains("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;");

    // Direct assertions: SecurityView.getName() and CsrfView.getToken() return plain
    // java.lang.String,
    // not SafeHtml
    SecurityView secView = new DefaultSecurityViewFactory().create(auth, request);
    CsrfView csrfView = new DefaultCsrfViewFactory().create(request);
    assertThat(secView.getName()).isEqualTo(maliciousName).isInstanceOf(String.class);
    assertThat((Object) secView.getName())
        .isNotInstanceOf(io.github.minh124199.viettemplate.runtime.SafeHtml.class);

    assertThat(csrfView.getToken()).isEqualTo(maliciousToken).isInstanceOf(String.class);
    assertThat((Object) csrfView.getToken())
        .isNotInstanceOf(io.github.minh124199.viettemplate.runtime.SafeHtml.class);
  }
}
