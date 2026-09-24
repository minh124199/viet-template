package io.github.minh124199.viettemplate.quarkus.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import java.security.Permission;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class QuarkusSecurityViewTest {

  @Test
  public void testAnonymousView() {
    QuarkusSecurityView view = QuarkusSecurityView.anonymous();

    assertThat(view.isAuthenticated()).isFalse();
    assertThat(view.isAnonymous()).isTrue();
    assertThat(view.name()).isEmpty();
    assertThat(view.getName()).isEmpty();
    assertThat(view.roles()).isEmpty();
    assertThat(view.getRoles()).isEmpty();
    assertThat(view.hasRole("ADMIN")).isFalse();
    assertThat(view.hasRole(null)).isFalse();
    assertThat(view.hasRole("")).isFalse();
  }

  @Test
  public void testAuthenticatedView() {
    QuarkusSecurityView view =
        new QuarkusSecurityView("john.doe", true, false, Set.of("USER", "ADMIN"));

    assertThat(view.isAuthenticated()).isTrue();
    assertThat(view.isAnonymous()).isFalse();
    assertThat(view.name()).isEqualTo("john.doe");
    assertThat(view.getName()).isEqualTo("john.doe");
    assertThat(view.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
    assertThat(view.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
    assertThat(view.hasRole("ADMIN")).isTrue();
    assertThat(view.hasRole("USER")).isTrue();
    assertThat(view.hasRole("MANAGER")).isFalse();
    assertThat(view.hasRole(null)).isFalse();
    assertThat(view.hasRole("  ")).isFalse();
  }

  @Test
  public void testFromSecurityIdentityAuthenticated() {
    SecurityIdentity identity =
        new TestSecurityIdentity("alice", false, Set.of("READER", "WRITER"));

    QuarkusSecurityView view = QuarkusSecurityView.from(identity);

    assertThat(view.isAuthenticated()).isTrue();
    assertThat(view.isAnonymous()).isFalse();
    assertThat(view.name()).isEqualTo("alice");
    assertThat(view.getName()).isEqualTo("alice");
    assertThat(view.hasRole("READER")).isTrue();
    assertThat(view.hasRole("ADMIN")).isFalse();
  }

  @Test
  public void testFromSecurityIdentityAnonymous() {
    SecurityIdentity identity = new TestSecurityIdentity(null, true, Set.of());

    QuarkusSecurityView view = QuarkusSecurityView.from(identity);

    assertThat(view.isAuthenticated()).isFalse();
    assertThat(view.isAnonymous()).isTrue();
    assertThat(view.name()).isEmpty();
    assertThat(view.roles()).isEmpty();
  }

  @Test
  public void testFromNullSecurityIdentity() {
    QuarkusSecurityView view = QuarkusSecurityView.from(null);

    assertThat(view.isAuthenticated()).isFalse();
    assertThat(view.isAnonymous()).isTrue();
    assertThat(view.name()).isEmpty();
    assertThat(view.roles()).isEmpty();
  }

  @Test
  public void testToStringRedaction() {
    QuarkusSecurityView view =
        new QuarkusSecurityView("super-secret-user", true, false, Set.of("SECRET_ROLE"));

    String str = view.toString();
    assertThat(str).doesNotContain("super-secret-user");
    assertThat(str).doesNotContain("SECRET_ROLE");
    assertThat(str).contains("[REDACTED]");
    assertThat(str).contains("authenticated=true");
  }

  @Test
  public void testEqualityAndHashCode() {
    QuarkusSecurityView v1 = new QuarkusSecurityView("bob", true, false, Set.of("ADMIN"));
    QuarkusSecurityView v2 = new QuarkusSecurityView("bob", true, false, Set.of("ADMIN"));
    QuarkusSecurityView v3 = new QuarkusSecurityView("bob", true, false, Set.of("USER"));

    assertThat(v1).isEqualTo(v2);
    assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
    assertThat(v1).isNotEqualTo(v3);
    assertThat(v1).isNotEqualTo("some string");
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
