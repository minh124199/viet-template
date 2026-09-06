package io.github.minh124199.viettemplate.language.vtl;

/** VTL compatibility profile defining language feature availability and security boundaries. */
public enum VtlProfile {

  /**
   * Plain text, references ($var, ${var}, $!var), property/index access, #set, #if, #foreach,
   * #break, static #include, static #parse.
   */
  VTL_CORE(false, false, false),

  /**
   * Adds legacy Velocity property lookup order, broader implicit coercions, macros, and legacy
   * undefined reference behavior.
   */
  VTL_MIGRATION(false, false, false),

  /**
   * Explicit opt-in for arbitrary approved public method invocation and runtime evaluation
   * (#evaluate).
   */
  VTL_DYNAMIC(true, true, false),

  /**
   * Strict sandboxed profile: no arbitrary methods, no #evaluate, static resource roots,
   * auto-escaping enabled by default, bounded loops.
   */
  VTL_SAFE(false, false, true);

  private final boolean arbitraryMethodsAllowed;
  private final boolean evaluateAllowed;
  private final boolean sandboxEnforced;

  VtlProfile(boolean arbitraryMethodsAllowed, boolean evaluateAllowed, boolean sandboxEnforced) {
    this.arbitraryMethodsAllowed = arbitraryMethodsAllowed;
    this.evaluateAllowed = evaluateAllowed;
    this.sandboxEnforced = sandboxEnforced;
  }

  public boolean isArbitraryMethodsAllowed() {
    return arbitraryMethodsAllowed;
  }

  public boolean isEvaluateAllowed() {
    return evaluateAllowed;
  }

  public boolean isSandboxEnforced() {
    return sandboxEnforced;
  }

  public static VtlProfile defaultProfile() {
    return VTL_CORE;
  }
}
