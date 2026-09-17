package io.github.minh124199.viettemplate.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.spring.web.servlet.SpringRenderAttributes;
import java.util.Map;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SpringSecurityConcurrencyStressTest {

  private InMemoryTemplateRepository repository;
  private SpringSecurityRenderContextContributor contributor;
  private TemplateEngine engine;

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
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName(
      "High-concurrency stress test: zero identity, authority, or CSRF token leakage across"
          + " threads")
  void highConcurrencyNoStateLeakage() throws Exception {
    TemplateId templateId = TemplateId.of("profile.vtl");
    this.repository.put(
        templateId,
        "user:$security.name|auth:$security.authenticated|role:$security.hasAuthority('ROLE_' +"
            + " $security.name)|csrf:#if($csrf)$csrf.token#end");

    int threadCount = 30;
    int iterationsPerThread = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      final String userName = "user" + threadId;
      final String roleName = "ROLE_" + userName;

      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < iterationsPerThread; i++) {
                String csrfTokenVal = "csrf-" + threadId + "-" + i;
                MockHttpServletRequest request = new MockHttpServletRequest();
                CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", csrfTokenVal);
                request.setAttribute(CsrfToken.class.getName(), token);

                Authentication auth =
                    new UsernamePasswordAuthenticationToken(
                        userName, "n/a", AuthorityUtils.createAuthorityList(roleName));

                // Pass request attributes containing the servlet request and explicit auth
                Map<String, Object> attributes =
                    Map.of(
                        SpringRenderAttributes.SERVLET_REQUEST, request,
                        SpringSecurityRenderContextContributor.AUTHENTICATION_ATTRIBUTE, auth);

                RenderRequest renderRequest =
                    RenderRequest.of(templateId, RenderContext.empty(), attributes);

                StringTemplateOutput output = new StringTemplateOutput();
                engine.render(renderRequest, output);
                String rendered = output.toString();

                String expected = "user:" + userName + "|auth:true|role:true|csrf:" + csrfTokenVal;
                if (!rendered.equals(expected)) {
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
    boolean completed = executor.awaitTermination(15, TimeUnit.SECONDS);

    assertThat(completed).isTrue();
    assertThat(failureCount.get()).isZero();
  }

  @Test
  @DisplayName(
      "Requirement 33 dedicated regression test: concurrent Alice (ADMIN), Bob (USER), and"
          + " Anonymous")
  void threePartyConcurrencyIsolationRegression() throws Exception {
    TemplateId templateId = TemplateId.of("access.vtl");
    this.repository.put(
        templateId,
        "name:$security.name|auth:$security.authenticated|admin:$security.hasAuthority('ROLE_ADMIN')|user:$security.hasAuthority('ROLE_USER')");

    int threadCount = 24;
    int iterationsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int t = 0; t < threadCount; t++) {
      final int mode = t % 3;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < iterationsPerThread; i++) {
                Map<String, Object> attributes;
                String expected;

                if (mode == 0) {
                  // Request A: Alice (ROLE_ADMIN)
                  Authentication auth =
                      new UsernamePasswordAuthenticationToken(
                          "alice", "n/a", AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
                  SecurityContext sc = new SecurityContextImpl(auth);
                  attributes =
                      Map.of(SpringSecurityRenderContextContributor.SECURITY_CONTEXT_ATTRIBUTE, sc);
                  expected = "name:alice|auth:true|admin:true|user:false";
                } else if (mode == 1) {
                  // Request B: Bob (ROLE_USER)
                  Authentication auth =
                      new UsernamePasswordAuthenticationToken(
                          "bob", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
                  SecurityContext sc = new SecurityContextImpl(auth);
                  attributes =
                      Map.of(SpringSecurityRenderContextContributor.SECURITY_CONTEXT_ATTRIBUTE, sc);
                  expected = "name:bob|auth:true|admin:false|user:true";
                } else {
                  // Request C: Anonymous
                  Authentication auth =
                      new AnonymousAuthenticationToken(
                          "key",
                          "anonymousUser",
                          AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
                  SecurityContext sc = new SecurityContextImpl(auth);
                  attributes =
                      Map.of(SpringSecurityRenderContextContributor.SECURITY_CONTEXT_ATTRIBUTE, sc);
                  expected = "name:|auth:false|admin:false|user:false";
                }

                RenderRequest renderRequest =
                    RenderRequest.of(templateId, RenderContext.empty(), attributes);

                StringTemplateOutput output = new StringTemplateOutput();
                engine.render(renderRequest, output);
                String rendered = output.toString();

                if (!rendered.equals(expected)) {
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
    boolean completed = executor.awaitTermination(15, TimeUnit.SECONDS);

    assertThat(completed).isTrue();
    assertThat(failureCount.get()).isZero();
  }
}
