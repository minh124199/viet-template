package io.github.minh124199.viettemplate.spring.security;

import io.github.minh124199.viettemplate.api.TemplateData;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Default immutable implementation of {@link SecurityView}. */
@TemplateData
final class DefaultSecurityView implements SecurityView {

  static final DefaultSecurityView ANONYMOUS =
      new DefaultSecurityView(false, true, "", Collections.emptySet());

  private final boolean authenticated;
  private final boolean anonymous;
  private final String name;
  private final Set<String> authorities;

  DefaultSecurityView(
      boolean authenticated, boolean anonymous, String name, Set<String> authorities) {
    this.authenticated = authenticated;
    this.anonymous = anonymous;
    this.name = name != null ? name : "";
    if (authorities == null || authorities.isEmpty()) {
      this.authorities = Collections.emptySet();
    } else {
      this.authorities = Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
    }
  }

  @Override
  public boolean isAuthenticated() {
    return this.authenticated;
  }

  @Override
  public boolean isAnonymous() {
    return this.anonymous;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Set<String> getAuthorities() {
    return this.authorities;
  }

  @Override
  public boolean hasAuthority(String authority) {
    if (authority == null || authority.isBlank()) {
      return false;
    }
    return this.authorities.contains(authority);
  }

  @Override
  public boolean hasAnyAuthority(String... authorities) {
    if (authorities == null || authorities.length == 0) {
      return false;
    }
    for (String authority : authorities) {
      if (authority != null && !authority.isBlank() && this.authorities.contains(authority)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DefaultSecurityView other)) {
      return false;
    }
    return this.authenticated == other.authenticated
        && this.anonymous == other.anonymous
        && Objects.equals(this.name, other.name)
        && Objects.equals(this.authorities, other.authorities);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.authenticated, this.anonymous, this.name, this.authorities);
  }

  @Override
  public String toString() {
    return "SecurityView[authenticated="
        + this.authenticated
        + ", anonymous="
        + this.anonymous
        + ", name="
        + this.name
        + ", authorities="
        + this.authorities
        + "]";
  }
}
