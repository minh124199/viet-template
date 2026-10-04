package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;

/** Enforces canonical render-time class checking semantics across interpreted execution tiers. */
final class RenderSecurityEnforcement {

  private RenderSecurityEnforcement() {}

  /**
   * Determines whether render-time class permission policy (isClassPermitted) must be enforced.
   *
   * <p>Enforced exclusively in safe sandbox profile (VTL_SAFE) across all output modes, preserving
   * standard VTL_CORE compatibility where developer templates permit rendering model instances.
   */
  static boolean shouldCheckRenderableClass(VtlProfile profile, VtlSecurityPolicy securityPolicy) {
    return profile == VtlProfile.VTL_SAFE
        || (securityPolicy != null && securityPolicy.isSafeProfile());
  }
}
