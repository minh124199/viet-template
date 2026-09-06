package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.util.Objects;

/** Configuration options controlling VTL parsing behavior. */
public record VtlParserOptions(
    int maxNestingDepth,
    VtlProfile profile,
    boolean allowHyphenatedIdentifiers,
    boolean allowBareNullLiteral) {

  public static final int DEFAULT_MAX_NESTING_DEPTH = 256;

  public static final VtlParserOptions DEFAULT =
      new VtlParserOptions(DEFAULT_MAX_NESTING_DEPTH, VtlProfile.defaultProfile(), false, false);

  public VtlParserOptions {
    if (maxNestingDepth <= 0) {
      throw new IllegalArgumentException("maxNestingDepth must be greater than zero");
    }
    Objects.requireNonNull(profile, "profile must not be null");
  }

  public VtlParserOptions(
      int maxNestingDepth, VtlProfile profile, boolean allowHyphenatedIdentifiers) {
    this(maxNestingDepth, profile, allowHyphenatedIdentifiers, false);
  }

  public static VtlParserOptions ofDefaults() {
    return DEFAULT;
  }

  public VtlParserOptions withMaxNestingDepth(int newMaxDepth) {
    return new VtlParserOptions(
        newMaxDepth, profile, allowHyphenatedIdentifiers, allowBareNullLiteral);
  }

  public VtlParserOptions withProfile(VtlProfile newProfile) {
    return new VtlParserOptions(
        maxNestingDepth, newProfile, allowHyphenatedIdentifiers, allowBareNullLiteral);
  }

  public VtlParserOptions withAllowHyphenatedIdentifiers(boolean allow) {
    return new VtlParserOptions(maxNestingDepth, profile, allow, allowBareNullLiteral);
  }

  public VtlParserOptions withAllowBareNullLiteral(boolean allow) {
    return new VtlParserOptions(maxNestingDepth, profile, allowHyphenatedIdentifiers, allow);
  }
}
