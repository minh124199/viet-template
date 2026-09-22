package io.github.minh124199.viettemplate.quarkus.security;

import io.github.minh124199.viettemplate.api.TemplateData;
import io.quarkus.security.identity.SecurityIdentity;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, safe presentation facade representing Quarkus security identity state for template
 * rendering.
 *
 * <h2>Security Invariant: Presentation Only</h2>
 *
 * <p><strong>WARNING:</strong> Template authorization helpers control presentation logic only. They
 * <strong>do not</strong> establish an authorization boundary or replace backend access control.
 * Endpoints and services must continue enforcing authorization via Quarkus security annotations
 * (such as {@code @RolesAllowed}, {@code @Authenticated}, or {@code @PermitAll}).
 *
 * <h2>Isolation Guarantees</h2>
 *
 * <p>Instances of this class are strictly detached from mutable framework state. They do not hold
 * references to Quarkus credentials, permissions, or security context object graphs, preventing
 * template sandbox escapes.
 */
@TemplateData
public final class QuarkusSecurityView {

  private static final QuarkusSecurityView ANONYMOUS =
      new QuarkusSecurityView("", false, true, Collections.emptySet());

  private final String name;
  private final boolean authenticated;
  private final boolean anonymous;
  private final Set<String> roles;

  /**
   * Constructs an immutable {@link QuarkusSecurityView}.
   *
   * @param name principal name (null defaults to empty string)
   * @param authenticated whether the identity is authenticated
   * @param anonymous whether the identity is anonymous
   * @param roles set of assigned roles (null defaults to empty set)
   */
  public QuarkusSecurityView(
      String name, boolean authenticated, boolean anonymous, Set<String> roles) {
    this.name = name != null ? name : "";
    this.authenticated = authenticated;
    this.anonymous = anonymous;
    if (roles == null || roles.isEmpty()) {
      this.roles = Collections.emptySet();
    } else {
      this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }
  }

  /**
   * Returns a default anonymous security view representing an unauthenticated request.
   *
   * @return anonymous security view
   */
  public static QuarkusSecurityView anonymous() {
    return ANONYMOUS;
  }

  /**
   * Creates an immutable {@link QuarkusSecurityView} snapshot from a Quarkus {@link
   * SecurityIdentity}.
   *
   * @param identity the Quarkus security identity, or {@code null}
   * @return immutable security view snapshot
   */
  public static QuarkusSecurityView from(SecurityIdentity identity) {
    if (identity == null) {
      return ANONYMOUS;
    }
    try {
      if (identity.isAnonymous()) {
        Principal principal = identity.getPrincipal();
        String principalName =
            (principal != null && principal.getName() != null) ? principal.getName() : "";
        Set<String> roles =
            identity.getRoles() != null ? identity.getRoles() : Collections.emptySet();
        return new QuarkusSecurityView(principalName, false, true, roles);
      }
      Principal principal = identity.getPrincipal();
      String principalName =
          (principal != null && principal.getName() != null) ? principal.getName() : "";
      return new QuarkusSecurityView(principalName, true, false, identity.getRoles());
    } catch (Throwable ignored) {
      return ANONYMOUS;
    }
  }

  /**
   * Returns the authenticated principal name, or an empty string if unauthenticated.
   *
   * @return principal name
   */
  public String name() {
    return this.name;
  }

  /**
   * JavaBean getter alias for {@link #name()}.
   *
   * @return principal name
   */
  public String getName() {
    return this.name;
  }

  /**
   * Returns whether the current identity is authenticated.
   *
   * @return {@code true} if authenticated, {@code false} otherwise
   */
  public boolean isAuthenticated() {
    return this.authenticated;
  }

  /**
   * Returns whether the current identity is anonymous or unauthenticated.
   *
   * @return {@code true} if anonymous, {@code false} otherwise
   */
  public boolean isAnonymous() {
    return this.anonymous;
  }

  /**
   * Returns whether the current identity has the specified role.
   *
   * @param role role name to check
   * @return {@code true} if present, {@code false} if absent, null, or blank
   */
  public boolean hasRole(String role) {
    if (role == null || role.isBlank()) {
      return false;
    }
    return this.roles.contains(role);
  }

  /**
   * Returns an unmodifiable set of granted roles.
   *
   * @return set of roles
   */
  public Set<String> roles() {
    return this.roles;
  }

  /**
   * JavaBean getter alias for {@link #roles()}.
   *
   * @return set of roles
   */
  public Set<String> getRoles() {
    return this.roles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof QuarkusSecurityView other)) {
      return false;
    }
    return this.authenticated == other.authenticated
        && this.anonymous == other.anonymous
        && Objects.equals(this.name, other.name)
        && Objects.equals(this.roles, other.roles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.name, this.authenticated, this.anonymous, this.roles);
  }

  @Override
  public String toString() {
    return "QuarkusSecurityView[authenticated="
        + this.authenticated
        + ", anonymous="
        + this.anonymous
        + ", name=[REDACTED], roles=[REDACTED]]";
  }
}
