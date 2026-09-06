package io.github.minh124199.viettemplate.tck.velocity.scenario;

/**
 * Engine-agnostic execution settings for differential compatibility scenarios.
 *
 * @param strictReferences Whether strict reference checking is enforced.
 * @param emptyCheck Whether directive.if.empty_check is enabled.
 * @param spaceGobbling The space gobbling policy (NONE, LINES, BC, STRUCTURED).
 * @param allowHyphenIdentifiers Whether hyphens are allowed in reference identifiers.
 * @param immutableRanges Whether range literals produce immutable collections.
 * @param allowBareNullLiteral Whether bare null literal syntax is accepted.
 * @param setNullAllowed Whether #set allows null right-hand side.
 */
public record CompatibilityConfiguration(
    boolean strictReferences,
    boolean emptyCheck,
    SpaceGobblingMode spaceGobbling,
    boolean allowHyphenIdentifiers,
    boolean immutableRanges,
    boolean allowBareNullLiteral,
    boolean setNullAllowed) {

  public enum SpaceGobblingMode {
    NONE,
    LINES,
    BC,
    STRUCTURED
  }

  public static CompatibilityConfiguration defaultConfiguration() {
    return new CompatibilityConfiguration(
        false, true, SpaceGobblingMode.LINES, false, true, false, true);
  }

  public CompatibilityConfiguration withStrictReferences(boolean strict) {
    return new CompatibilityConfiguration(
        strict,
        emptyCheck,
        spaceGobbling,
        allowHyphenIdentifiers,
        immutableRanges,
        allowBareNullLiteral,
        setNullAllowed);
  }

  public CompatibilityConfiguration withEmptyCheck(boolean check) {
    return new CompatibilityConfiguration(
        strictReferences,
        check,
        spaceGobbling,
        allowHyphenIdentifiers,
        immutableRanges,
        allowBareNullLiteral,
        setNullAllowed);
  }

  public CompatibilityConfiguration withSpaceGobbling(SpaceGobblingMode mode) {
    return new CompatibilityConfiguration(
        strictReferences,
        emptyCheck,
        mode,
        allowHyphenIdentifiers,
        immutableRanges,
        allowBareNullLiteral,
        setNullAllowed);
  }

  public CompatibilityConfiguration withAllowHyphenIdentifiers(boolean allow) {
    return new CompatibilityConfiguration(
        strictReferences,
        emptyCheck,
        spaceGobbling,
        allow,
        immutableRanges,
        allowBareNullLiteral,
        setNullAllowed);
  }

  public CompatibilityConfiguration withImmutableRanges(boolean immutable) {
    return new CompatibilityConfiguration(
        strictReferences,
        emptyCheck,
        spaceGobbling,
        allowHyphenIdentifiers,
        immutable,
        allowBareNullLiteral,
        setNullAllowed);
  }

  public CompatibilityConfiguration withAllowBareNullLiteral(boolean allow) {
    return new CompatibilityConfiguration(
        strictReferences,
        emptyCheck,
        spaceGobbling,
        allowHyphenIdentifiers,
        immutableRanges,
        allow,
        setNullAllowed);
  }

  public CompatibilityConfiguration withSetNullAllowed(boolean allowed) {
    return new CompatibilityConfiguration(
        strictReferences,
        emptyCheck,
        spaceGobbling,
        allowHyphenIdentifiers,
        immutableRanges,
        allowBareNullLiteral,
        allowed);
  }
}
