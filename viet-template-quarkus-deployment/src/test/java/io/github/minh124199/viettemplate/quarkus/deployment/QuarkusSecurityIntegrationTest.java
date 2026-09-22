package io.github.minh124199.viettemplate.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer;
import io.github.minh124199.viettemplate.quarkus.security.QuarkusSecurityRenderContextContributor;
import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("removal")
public class QuarkusSecurityIntegrationTest {

  @RegisterExtension
  static final QuarkusUnitTest unitTest =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addAsResource(
                          new StringAsset(
                              "Auth: $security.authenticated, Anon: $security.anonymous, User:"
                                  + " $security.name, Admin: $security.hasRole('ADMIN')"),
                          "templates/security-check.vtl"));

  @Inject TemplateEngine engine;

  @Inject VietTemplateRenderer renderer;

  @Test
  public void testDefaultAnonymousSecurityBinding() {
    // When rendered normally in unauthenticated context
    String output = renderer.render("security-check.vtl", Map.of());
    assertThat(output).contains("Auth: false");
    assertThat(output).contains("Anon: true");
    assertThat(output).contains("Admin: false");
  }

  @Test
  public void testAuthenticatedSecurityBindingViaRequestAttribute() throws Exception {
    SecurityIdentity testIdentity =
        new TestSecurityIdentity("alice", false, Set.of("ADMIN", "USER"));

    RenderRequest request =
        new RenderRequest(
            TemplateId.of("security-check.vtl"),
            RenderContext.empty(),
            Map.of(
                QuarkusSecurityRenderContextContributor.SECURITY_IDENTITY_ATTRIBUTE, testIdentity));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (var templateOutput =
        new io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput(baos)) {
      engine.render(request, templateOutput);
    }

    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("Auth: true");
    assertThat(output).contains("Anon: false");
    assertThat(output).contains("User: alice");
    assertThat(output).contains("Admin: true");
  }

  static class TestSecurityIdentity implements SecurityIdentity {
    private final Principal principal;
    private final boolean anonymous;
    private final Set<String> roles;

    TestSecurityIdentity(String name, boolean anonymous, Set<String> roles) {
      this.principal = name != null ? () -> name : null;
      this.anonymous = anonymous;
      this.roles = roles != null ? roles : Collections.emptySet();
    }

    @Override
    public Principal getPrincipal() {
      return this.principal;
    }

    @Override
    public boolean isAnonymous() {
      return this.anonymous;
    }

    @Override
    public Set<String> getRoles() {
      return this.roles;
    }

    @Override
    public boolean hasRole(String role) {
      return this.roles.contains(role);
    }

    @Override
    public Set<Permission> getPermissions() {
      return Collections.emptySet();
    }

    @Override
    public <T extends Credential> T getCredential(Class<T> credentialType) {
      return null;
    }

    @Override
    public Set<Credential> getCredentials() {
      return Collections.emptySet();
    }

    @Override
    public <T> T getAttribute(String name) {
      return null;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return Collections.emptyMap();
    }

    @Override
    public Uni<Boolean> checkPermission(Permission permission) {
      return Uni.createFrom().item(Boolean.FALSE);
    }
  }
}
