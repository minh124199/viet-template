package io.github.minh124199.viettemplate.language.vtl.parser;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.util.Objects;

/** Configuration options controlling VTL parsing behavior and compile-time limits. */
public record VtlParserOptions(
    int maxNestingDepth,
    VtlProfile profile,
    boolean allowHyphenatedIdentifiers,
    boolean allowBareNullLiteral,
    int maxSourceCharacters,
    int maxAstNodes,
    int maxExpressionDepth,
    int maxDirectiveNesting) {

  public static final int DEFAULT_MAX_NESTING_DEPTH = 256;
  public static final int DEFAULT_MAX_SOURCE_CHARACTERS = 1_000_000;
  public static final int DEFAULT_MAX_AST_NODES = 50_000;
  public static final int DEFAULT_MAX_EXPRESSION_DEPTH = 100;
  public static final int DEFAULT_MAX_DIRECTIVE_NESTING = 100;

  public static final VtlParserOptions DEFAULT =
      new VtlParserOptions(
          DEFAULT_MAX_NESTING_DEPTH,
          VtlProfile.defaultProfile(),
          false,
          false,
          DEFAULT_MAX_SOURCE_CHARACTERS,
          DEFAULT_MAX_AST_NODES,
          DEFAULT_MAX_EXPRESSION_DEPTH,
          DEFAULT_MAX_DIRECTIVE_NESTING);

  public VtlParserOptions {
    if (maxNestingDepth <= 0) {
      throw new IllegalArgumentException("maxNestingDepth must be greater than zero");
    }
    if (maxSourceCharacters <= 0) {
      throw new IllegalArgumentException("maxSourceCharacters must be greater than zero");
    }
    if (maxAstNodes <= 0) {
      throw new IllegalArgumentException("maxAstNodes must be greater than zero");
    }
    if (maxExpressionDepth <= 0) {
      throw new IllegalArgumentException("maxExpressionDepth must be greater than zero");
    }
    if (maxDirectiveNesting <= 0) {
      throw new IllegalArgumentException("maxDirectiveNesting must be greater than zero");
    }
    Objects.requireNonNull(profile, "profile must not be null");
  }

  public VtlParserOptions(
      int maxNestingDepth,
      VtlProfile profile,
      boolean allowHyphenatedIdentifiers,
      boolean allowBareNullLiteral) {
    this(
        maxNestingDepth,
        profile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        DEFAULT_MAX_SOURCE_CHARACTERS,
        DEFAULT_MAX_AST_NODES,
        DEFAULT_MAX_EXPRESSION_DEPTH,
        DEFAULT_MAX_DIRECTIVE_NESTING);
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
        newMaxDepth,
        profile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        maxSourceCharacters,
        maxAstNodes,
        maxExpressionDepth,
        maxDirectiveNesting);
  }

  public VtlParserOptions withProfile(VtlProfile newProfile) {
    return new VtlParserOptions(
        maxNestingDepth,
        newProfile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        maxSourceCharacters,
        maxAstNodes,
        maxExpressionDepth,
        maxDirectiveNesting);
  }

  public VtlParserOptions withAllowHyphenatedIdentifiers(boolean allow) {
    return new VtlParserOptions(
        maxNestingDepth,
        profile,
        allow,
        allowBareNullLiteral,
        maxSourceCharacters,
        maxAstNodes,
        maxExpressionDepth,
        maxDirectiveNesting);
  }

  public VtlParserOptions withAllowBareNullLiteral(boolean allow) {
    return new VtlParserOptions(
        maxNestingDepth,
        profile,
        allowHyphenatedIdentifiers,
        allow,
        maxSourceCharacters,
        maxAstNodes,
        maxExpressionDepth,
        maxDirectiveNesting);
  }

  public VtlParserOptions withMaxSourceCharacters(int newMax) {
    return new VtlParserOptions(
        maxNestingDepth,
        profile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        newMax,
        maxAstNodes,
        maxExpressionDepth,
        maxDirectiveNesting);
  }

  public VtlParserOptions withMaxAstNodes(int newMax) {
    return new VtlParserOptions(
        maxNestingDepth,
        profile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        maxSourceCharacters,
        newMax,
        maxExpressionDepth,
        maxDirectiveNesting);
  }

  public VtlParserOptions withMaxExpressionDepth(int newMax) {
    return new VtlParserOptions(
        maxNestingDepth,
        profile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        maxSourceCharacters,
        maxAstNodes,
        newMax,
        maxDirectiveNesting);
  }

  public VtlParserOptions withMaxDirectiveNesting(int newMax) {
    return new VtlParserOptions(
        maxNestingDepth,
        profile,
        allowHyphenatedIdentifiers,
        allowBareNullLiteral,
        maxSourceCharacters,
        maxAstNodes,
        maxExpressionDepth,
        newMax);
  }
}
