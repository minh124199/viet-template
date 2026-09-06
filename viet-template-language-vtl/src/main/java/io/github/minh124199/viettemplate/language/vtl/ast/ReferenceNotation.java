package io.github.minh124199.viettemplate.language.vtl.ast;

/**
 * Encapsulates the syntactic notation of a reference: whether it uses quiet notation ($!) and/or
 * formal notation (${}).
 */
public record ReferenceNotation(boolean quiet, boolean formal) {

  public static final ReferenceNotation NORMAL = new ReferenceNotation(false, false);
  public static final ReferenceNotation QUIET = new ReferenceNotation(true, false);
  public static final ReferenceNotation FORMAL = new ReferenceNotation(false, true);
  public static final ReferenceNotation QUIET_FORMAL = new ReferenceNotation(true, true);

  public static ReferenceNotation of(boolean quiet, boolean formal) {
    if (quiet && formal) {
      return QUIET_FORMAL;
    }
    if (quiet) {
      return QUIET;
    }
    if (formal) {
      return FORMAL;
    }
    return NORMAL;
  }
}
