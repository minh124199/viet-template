package io.github.minh124199.viettemplate.spring.security;

import io.github.minh124199.viettemplate.api.TemplateData;
import java.util.Set;

/**
 * Immutable, safe presentation facade representing Spring Security authentication state for
 * template rendering.
 *
 * <h2>Security Invariant: Presentation Only</h2>
 *
 * <p><strong>WARNING:</strong> Template authorization helpers control presentation logic only. They
 * <strong>do not</strong> establish an authorization boundary or replace backend access control.
 * Endpoints and services must continue enforcing authorization via Spring Security's {@code
 * authorizeHttpRequests}, {@code requestMatchers}, {@code @PreAuthorize}, {@code @Secured}, or
 * method security.
 *
 * <h2>Isolation Guarantees</h2>
 *
 * <p>Instances of this interface are strictly detached from mutable framework state. They do not
 * hold references to Spring's {@code Authentication}, {@code SecurityContext}, {@code
 * SecurityContextHolder}, credentials, details, or principal object graphs, preventing template
 * sandbox escapes.
 */
@TemplateData
public interface SecurityView {

  /** Returns whether the current request is associated with an authenticated end user. */
  boolean isAuthenticated();

  /** Returns whether the current request is unauthenticated or anonymous. */
  boolean isAnonymous();

  /** Returns the name of the authenticated principal, or an empty string if unauthenticated. */
  String getName();

  /**
   * Returns an unmodifiable, deterministically ordered set of granted authority strings.
   *
   * @return set of granted authorities (e.g. {@code ["ROLE_USER", "ROLE_ADMIN"]})
   */
  Set<String> getAuthorities();

  /**
   * Returns whether the authenticated user has the specified authority.
   *
   * @param authority the authority name to check (e.g. {@code "ROLE_ADMIN"})
   * @return {@code true} if present, {@code false} if absent, null, or blank
   */
  boolean hasAuthority(String authority);

  /**
   * Returns whether the authenticated user has any of the specified authorities.
   *
   * @param authorities candidate authority names
   * @return {@code true} if any match, {@code false} otherwise
   */
  boolean hasAnyAuthority(String... authorities);

  /**
   * Convenience overload for two authorities to support direct template method invocation.
   *
   * @param authority1 first candidate authority
   * @param authority2 second candidate authority
   * @return {@code true} if either matches, {@code false} otherwise
   */
  default boolean hasAnyAuthority(String authority1, String authority2) {
    return hasAnyAuthority(new String[] {authority1, authority2});
  }

  /**
   * Convenience overload for three authorities to support direct template method invocation.
   *
   * @param authority1 first candidate authority
   * @param authority2 second candidate authority
   * @param authority3 third candidate authority
   * @return {@code true} if any matches, {@code false} otherwise
   */
  default boolean hasAnyAuthority(String authority1, String authority2, String authority3) {
    return hasAnyAuthority(new String[] {authority1, authority2, authority3});
  }

  /** Returns a default empty/anonymous view representing an unauthenticated request. */
  static SecurityView anonymousView() {
    return DefaultSecurityView.ANONYMOUS;
  }
}
