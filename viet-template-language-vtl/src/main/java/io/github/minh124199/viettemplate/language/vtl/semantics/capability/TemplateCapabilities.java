package io.github.minh124199.viettemplate.language.vtl.semantics.capability;

/**
 * Immutable analysis flags indicating runtime capabilities required by a template.
 *
 * <p>Used by the compiler and execution engine to determine optimization levels and Ahead-Of-Time
 * (AOT) lowering eligibility.
 */
public record TemplateCapabilities(
    boolean requiresDynamicMemberResolution,
    boolean requiresArbitraryMethodCalls,
    boolean requiresDynamicIncludeParse,
    boolean requiresRuntimeEvaluation,
    boolean accessesRawUnescapedOutput,
    boolean usesUnknownModelTypes,
    boolean eligibleForStaticAot) {

  public static TemplateCapabilities of(
      boolean requiresDynamicMemberResolution,
      boolean requiresArbitraryMethodCalls,
      boolean requiresDynamicIncludeParse,
      boolean requiresRuntimeEvaluation,
      boolean accessesRawUnescapedOutput,
      boolean usesUnknownModelTypes) {
    return of(
        requiresDynamicMemberResolution,
        requiresArbitraryMethodCalls,
        requiresDynamicIncludeParse,
        requiresRuntimeEvaluation,
        accessesRawUnescapedOutput,
        usesUnknownModelTypes,
        false);
  }

  public static TemplateCapabilities of(
      boolean requiresDynamicMemberResolution,
      boolean requiresArbitraryMethodCalls,
      boolean requiresDynamicIncludeParse,
      boolean requiresRuntimeEvaluation,
      boolean accessesRawUnescapedOutput,
      boolean usesUnknownModelTypes,
      boolean hasErrors) {
    boolean eligibleForStaticAot =
        !hasErrors
            && !requiresDynamicMemberResolution
            && !requiresArbitraryMethodCalls
            && !requiresDynamicIncludeParse
            && !requiresRuntimeEvaluation
            && !usesUnknownModelTypes;
    return new TemplateCapabilities(
        requiresDynamicMemberResolution,
        requiresArbitraryMethodCalls,
        requiresDynamicIncludeParse,
        requiresRuntimeEvaluation,
        accessesRawUnescapedOutput,
        usesUnknownModelTypes,
        eligibleForStaticAot);
  }

  public static TemplateCapabilities empty() {
    return of(false, false, false, false, false, false, false);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private boolean requiresDynamicMemberResolution;
    private boolean requiresArbitraryMethodCalls;
    private boolean requiresDynamicIncludeParse;
    private boolean requiresRuntimeEvaluation;
    private boolean accessesRawUnescapedOutput;
    private boolean usesUnknownModelTypes;
    private boolean hasErrors;

    private Builder() {}

    public Builder setRequiresDynamicMemberResolution(boolean val) {
      this.requiresDynamicMemberResolution = val;
      return this;
    }

    public Builder setRequiresArbitraryMethodCalls(boolean val) {
      this.requiresArbitraryMethodCalls = val;
      return this;
    }

    public Builder setRequiresDynamicIncludeParse(boolean val) {
      this.requiresDynamicIncludeParse = val;
      return this;
    }

    public Builder setRequiresRuntimeEvaluation(boolean val) {
      this.requiresRuntimeEvaluation = val;
      return this;
    }

    public Builder setAccessesRawUnescapedOutput(boolean val) {
      this.accessesRawUnescapedOutput = val;
      return this;
    }

    public Builder setUsesUnknownModelTypes(boolean val) {
      this.usesUnknownModelTypes = val;
      return this;
    }

    public Builder setHasErrors(boolean val) {
      this.hasErrors = val;
      return this;
    }

    public boolean isRequiresDynamicMemberResolution() {
      return requiresDynamicMemberResolution;
    }

    public boolean isRequiresArbitraryMethodCalls() {
      return requiresArbitraryMethodCalls;
    }

    public boolean isRequiresDynamicIncludeParse() {
      return requiresDynamicIncludeParse;
    }

    public boolean isRequiresRuntimeEvaluation() {
      return requiresRuntimeEvaluation;
    }

    public boolean isAccessesRawUnescapedOutput() {
      return accessesRawUnescapedOutput;
    }

    public boolean isUsesUnknownModelTypes() {
      return usesUnknownModelTypes;
    }

    public boolean hasErrors() {
      return hasErrors;
    }

    public TemplateCapabilities build() {
      return TemplateCapabilities.of(
          requiresDynamicMemberResolution,
          requiresArbitraryMethodCalls,
          requiresDynamicIncludeParse,
          requiresRuntimeEvaluation,
          accessesRawUnescapedOutput,
          usesUnknownModelTypes,
          hasErrors);
    }
  }
}
