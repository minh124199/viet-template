package io.github.minh124199.viettemplate.quarkus.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class QuarkusSecurityRenderContextContributorTest {

  @Test
  public void testContributeWithSupplier() throws Exception {
    QuarkusSecurityViewTest.TestSecurityIdentity identity =
        new QuarkusSecurityViewTest.TestSecurityIdentity("bob", false, Set.of("ADMIN", "USER"));

    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(() -> identity);

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put(
                "test.vtl",
                "User: $security.name, Auth: $security.authenticated, IsAdmin:"
                    + " $security.hasRole('ADMIN'), Roles: $security.roles");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("test.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("User: bob");
    assertThat(output).contains("Auth: true");
    assertThat(output).contains("IsAdmin: true");
  }

  @Test
  public void testContributeAnonymousWhenNoIdentityAvailable() throws Exception {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put(
                "anon.vtl",
                "Auth: $security.authenticated, Anon: $security.anonymous, HasUser:"
                    + " $security.hasRole('USER')");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("anon.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("Auth: false");
    assertThat(output).contains("Anon: true");
    assertThat(output).contains("HasUser: false");
  }

  @Test
  public void testContributeFromRequestAttribute() throws Exception {
    QuarkusSecurityViewTest.TestSecurityIdentity identity =
        new QuarkusSecurityViewTest.TestSecurityIdentity("charlie", false, Set.of("VIEWER"));

    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put("req.vtl", "#if($security.authenticated)Hello $security.name!#end");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    RenderRequest request =
        new RenderRequest(
            TemplateId.of("req.vtl"),
            RenderContext.empty(),
            Map.of(QuarkusSecurityRenderContextContributor.SECURITY_IDENTITY_ATTRIBUTE, identity));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(request, out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Hello charlie!");
  }

  @Test
  public void testCustomVariableName() throws Exception {
    QuarkusSecurityViewTest.TestSecurityIdentity identity =
        new QuarkusSecurityViewTest.TestSecurityIdentity("diana", false, Set.of("ADMIN"));

    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(() -> identity, "auth");

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put("custom.vtl", "Principal: $auth.name, Variable: " + contributor.getVariableName());

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("custom.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Principal: diana, Variable: auth");
    assertThat(contributor.getSecurityVariableName()).isEqualTo("auth");
    assertThat(contributor.getCsrfVariableName()).isEqualTo("csrf");
  }

  @Test
  public void testCustomViewFactory() throws Exception {
    QuarkusSecurityViewTest.TestSecurityIdentity identity =
        new QuarkusSecurityViewTest.TestSecurityIdentity("eve", false, Set.of("USER"));

    QuarkusSecurityViewFactory customFactory =
        id ->
            new QuarkusSecurityView(
                "CUSTOM_" + id.getPrincipal().getName(), true, false, Set.of("SUPERUSER"));

    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(
            () -> identity, customFactory, null, "sec", "csrf");

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put("factory.vtl", "User: $sec.name, HasSuper: $sec.hasRole('SUPERUSER')");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("factory.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("User: CUSTOM_eve, HasSuper: true");
    assertThat(contributor.getViewFactory()).isSameAs(customFactory);
  }

  @Test
  public void testCsrfViaStringAttribute() throws Exception {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put(
                "csrf-str.vtl",
                "Token: $csrf.token, Avail: $csrf.available, Param: $csrf.parameterName");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    RenderRequest request =
        new RenderRequest(
            TemplateId.of("csrf-str.vtl"),
            RenderContext.empty(),
            Map.of(QuarkusSecurityRenderContextContributor.CSRF_TOKEN_ATTRIBUTE, "token-xyz-123"));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(request, out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Token: token-xyz-123, Avail: true, Param: csrf-token");
  }

  @Test
  public void testCsrfViaViewAttribute() throws Exception {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put("csrf-view.vtl", "$csrf.parameterName=$csrf.token ($csrf.headerName)");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    QuarkusCsrfView customView = QuarkusCsrfView.of("tok-456", "my_csrf", "X-MY-CSRF");
    RenderRequest request =
        new RenderRequest(
            TemplateId.of("csrf-view.vtl"),
            RenderContext.empty(),
            Map.of(QuarkusSecurityRenderContextContributor.CSRF_TOKEN_ATTRIBUTE, customView));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(request, out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("my_csrf=tok-456 (X-MY-CSRF)");
  }

  @Test
  public void testCsrfViaShortAttributes() throws Exception {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create().put("csrf-short.vtl", "CSRF: $csrf.token");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    // Test "csrf" key
    RenderRequest req1 =
        new RenderRequest(
            TemplateId.of("csrf-short.vtl"), RenderContext.empty(), Map.of("csrf", "tok-short"));
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos1)) {
      engine.render(req1, out);
    }
    assertThat(baos1.toString(StandardCharsets.UTF_8)).isEqualTo("CSRF: tok-short");

    // Test "csrfToken" key
    RenderRequest req2 =
        new RenderRequest(
            TemplateId.of("csrf-short.vtl"),
            RenderContext.empty(),
            Map.of("csrfToken", "tok-token"));
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos2)) {
      engine.render(req2, out);
    }
    assertThat(baos2.toString(StandardCharsets.UTF_8)).isEqualTo("CSRF: tok-token");
  }

  @Test
  public void testCsrfViaRoutingContextAttribute() throws Exception {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put("routing.vtl", "Token: $csrf.token, Avail: $csrf.available");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    // Mock RoutingContext object with public Object get(String key)
    Object mockRoutingContext =
        new Object() {
          public Object get(String key) {
            if ("csrf_token".equals(key)) {
              return "rc-token-789";
            }
            return null;
          }
        };

    RenderRequest request =
        new RenderRequest(
            TemplateId.of("routing.vtl"),
            RenderContext.empty(),
            Map.of("io.vertx.ext.web.RoutingContext", mockRoutingContext));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(request, out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Token: rc-token-789, Avail: true");
  }

  @Test
  public void testCsrfViaSupplier() throws Exception {
    QuarkusCsrfView suppliedView = QuarkusCsrfView.of("supplied-token-999");
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(null, () -> suppliedView);

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put(
                "supp.vtl",
                "CSRF: $csrf.token, Supplier: " + (contributor.getCsrfSupplier() != null));

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("supp.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("CSRF: supplied-token-999, Supplier: true");
  }

  @Test
  public void testCsrfUnavailableFallback() throws Exception {
    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor();

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put("unavail.vtl", "Avail: $csrf.available, TokenEmpty: '$csrf.token'");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("unavail.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo("Avail: false, TokenEmpty: ''");
  }

  @Test
  public void testCombinedSecurityAndCsrfForm() throws Exception {
    QuarkusSecurityViewTest.TestSecurityIdentity identity =
        new QuarkusSecurityViewTest.TestSecurityIdentity("admin-user", false, Set.of("ADMIN"));
    QuarkusCsrfView csrf = QuarkusCsrfView.of("form-csrf-secret");

    QuarkusSecurityRenderContextContributor contributor =
        new QuarkusSecurityRenderContextContributor(() -> identity, () -> csrf);

    InMemoryTemplateRepository repo =
        InMemoryTemplateRepository.create()
            .put(
                "form.vtl",
                "<form method=\"POST\">\n"
                    + "  <input type=\"hidden\" name=\"$csrf.parameterName\""
                    + " value=\"$csrf.token\"/>\n"
                    + "  <span>Logged in as: $security.name</span>\n"
                    + "  <button type=\"submit\"#if(!$security.hasRole('ADMIN'))"
                    + " disabled#end>Delete</button>\n"
                    + "</form>");

    TemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).addContextContributor(contributor).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(RenderRequest.of(TemplateId.of("form.vtl"), RenderContext.empty()), out);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("<input type=\"hidden\" name=\"csrf-token\" value=\"form-csrf-secret\"/>");
    assertThat(output).contains("<span>Logged in as: admin-user</span>");
    assertThat(output).contains("<button type=\"submit\">Delete</button>");
    assertThat(output).doesNotContain("disabled");
  }
}
