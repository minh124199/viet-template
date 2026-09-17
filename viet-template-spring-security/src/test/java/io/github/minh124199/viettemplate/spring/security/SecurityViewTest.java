package io.github.minh124199.viettemplate.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityViewTest {

  @Test
  @DisplayName("Anonymous SecurityView contract: unauthenticated, empty name, empty authorities")
  void anonymousViewContract() {
    SecurityView view = SecurityView.anonymousView();

    assertThat(view.isAuthenticated()).isFalse();
    assertThat(view.isAnonymous()).isTrue();
    assertThat(view.getName()).isEmpty();
    assertThat(view.getAuthorities()).isEmpty();

    assertThat(view.hasAuthority("ROLE_USER")).isFalse();
    assertThat(view.hasAuthority(null)).isFalse();
    assertThat(view.hasAuthority("")).isFalse();
    assertThat(view.hasAuthority("   ")).isFalse();

    assertThat(view.hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")).isFalse();
    assertThat(view.hasAnyAuthority()).isFalse();
    assertThat(view.hasAnyAuthority((String[]) null)).isFalse();
  }

  @Test
  @DisplayName("Authenticated SecurityView contract: holds normalized immutable state")
  void authenticatedViewContract() {
    SecurityView view =
        new DefaultSecurityView(true, false, "adminUser", Set.of("ROLE_ADMIN", "ROLE_USER"));

    assertThat(view.isAuthenticated()).isTrue();
    assertThat(view.isAnonymous()).isFalse();
    assertThat(view.getName()).isEqualTo("adminUser");
    assertThat(view.getAuthorities()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");

    assertThat(view.hasAuthority("ROLE_ADMIN")).isTrue();
    assertThat(view.hasAuthority("ROLE_USER")).isTrue();
    assertThat(view.hasAuthority("ROLE_GUEST")).isFalse();
    assertThat(view.hasAuthority(null)).isFalse();
    assertThat(view.hasAuthority("")).isFalse();
    assertThat(view.hasAuthority("   ")).isFalse();

    assertThat(view.hasAnyAuthority("ROLE_GUEST", "ROLE_ADMIN")).isTrue();
    assertThat(view.hasAnyAuthority("ROLE_GUEST", "ROLE_AUDITOR")).isFalse();
    assertThat(view.hasAnyAuthority()).isFalse();
    assertThat(view.hasAnyAuthority((String[]) null)).isFalse();
    assertThat(view.hasAnyAuthority("ROLE_GUEST", null, "   ")).isFalse();
  }

  @Test
  @DisplayName("Authorities set is strictly immutable")
  void authoritiesSetImmutability() {
    SecurityView view = new DefaultSecurityView(true, false, "alice", Set.of("ROLE_USER"));

    Set<String> authorities = view.getAuthorities();
    assertThatThrownBy(() -> authorities.add("ROLE_ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("SecurityView equals, hashCode, and toString")
  void equalsHashCodeToString() {
    SecurityView view1 = new DefaultSecurityView(true, false, "bob", Set.of("ROLE_USER"));
    SecurityView view2 = new DefaultSecurityView(true, false, "bob", Set.of("ROLE_USER"));
    SecurityView view3 = new DefaultSecurityView(true, false, "charlie", Set.of("ROLE_USER"));

    assertThat(view1).isEqualTo(view2);
    assertThat(view1.hashCode()).isEqualTo(view2.hashCode());
    assertThat(view1).isNotEqualTo(view3);

    String str = view1.toString();
    assertThat(str).contains("bob").contains("ROLE_USER").contains("authenticated=true");
  }
}
