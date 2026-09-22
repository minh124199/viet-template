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
  }
}
