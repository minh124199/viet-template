package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import java.util.Objects;

/** Configuration options controlling reference interpreter execution behavior. */
public record VtlInterpreterOptions(
    VtlProfile profile,
    boolean strictReferences,
    boolean setNullAllowed,
    boolean emptyCheck,
    SpaceGobbler.Mode spaceGobbling,
    boolean allowBareNullLiteral,
    ExecutionLimits limits,
    VtlSecurityPolicy securityPolicy,
    TemplateResourceResolver resourceResolver,
    ExecutionTier executionTier,
    IrOptimizationOptions optimizationOptions) {

  public static final VtlInterpreterOptions DEFAULT =
      new VtlInterpreterOptions(
          VtlProfile.VTL_CORE,
          false,
          true,
          true,
          SpaceGobbler.Mode.LINES,
          false,
          ExecutionLimits.DEFAULT,
          VtlSecurityPolicy.standard(),
          TemplateResourceResolver.empty(),
          ExecutionTier.AST,
          IrOptimizationOptions.defaultOptions());

  public VtlInterpreterOptions {
    Objects.requireNonNull(profile, "profile must not be null");
    Objects.requireNonNull(spaceGobbling, "spaceGobbling must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(securityPolicy, "securityPolicy must not be null");
    Objects.requireNonNull(resourceResolver, "resourceResolver must not be null");
    Objects.requireNonNull(executionTier, "executionTier must not be null");
    Objects.requireNonNull(optimizationOptions, "optimizationOptions must not be null");
  }

  public VtlInterpreterOptions(
      VtlProfile profile,
      boolean strictReferences,
      boolean setNullAllowed,
      boolean emptyCheck,
      SpaceGobbler.Mode spaceGobbling,
      boolean allowBareNullLiteral,
      ExecutionLimits limits,
      VtlSecurityPolicy securityPolicy,
      TemplateResourceResolver resourceResolver,
      ExecutionTier executionTier) {
    this(
        profile,
        strictReferences,
        setNullAllowed,
        emptyCheck,
        spaceGobbling,
        allowBareNullLiteral,
        limits,
        securityPolicy,
        resourceResolver,
        executionTier,
        IrOptimizationOptions.defaultOptions());
  }

  public VtlInterpreterOptions(
      VtlProfile profile,
      boolean strictReferences,
      boolean setNullAllowed,
      boolean emptyCheck,
      SpaceGobbler.Mode spaceGobbling,
      boolean allowBareNullLiteral,
      ExecutionLimits limits,
      VtlSecurityPolicy securityPolicy,
      TemplateResourceResolver resourceResolver) {
    this(
        profile,
        strictReferences,
        setNullAllowed,
        emptyCheck,
        spaceGobbling,
        allowBareNullLiteral,
        limits,
        securityPolicy,
        resourceResolver,
        ExecutionTier.AST);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .profile(profile)
        .strictReferences(strictReferences)
        .setNullAllowed(setNullAllowed)
        .emptyCheck(emptyCheck)
        .spaceGobbling(spaceGobbling)
        .allowBareNullLiteral(allowBareNullLiteral)
        .limits(limits)
        .securityPolicy(securityPolicy)
        .resourceResolver(resourceResolver)
        .executionTier(executionTier)
        .optimizationOptions(optimizationOptions);
  }

  public static final class Builder {
    private VtlProfile profile = VtlProfile.VTL_CORE;
    private boolean strictReferences = false;
    private boolean setNullAllowed = true;
    private boolean emptyCheck = true;
    private SpaceGobbler.Mode spaceGobbling = SpaceGobbler.Mode.LINES;
    private boolean allowBareNullLiteral = false;
    private ExecutionLimits limits = ExecutionLimits.DEFAULT;
    private VtlSecurityPolicy securityPolicy = VtlSecurityPolicy.standard();
    private TemplateResourceResolver resourceResolver = TemplateResourceResolver.empty();
    private ExecutionTier executionTier = ExecutionTier.AST;
    private IrOptimizationOptions optimizationOptions = IrOptimizationOptions.defaultOptions();

    public Builder profile(VtlProfile profile) {
      this.profile = Objects.requireNonNull(profile, "profile must not be null");
      return this;
    }

    public Builder strictReferences(boolean strictReferences) {
      this.strictReferences = strictReferences;
      return this;
    }

    public Builder setNullAllowed(boolean setNullAllowed) {
      this.setNullAllowed = setNullAllowed;
      return this;
    }

    public Builder emptyCheck(boolean emptyCheck) {
      this.emptyCheck = emptyCheck;
      return this;
    }

    public Builder spaceGobbling(SpaceGobbler.Mode spaceGobbling) {
      this.spaceGobbling = Objects.requireNonNull(spaceGobbling, "spaceGobbling must not be null");
      return this;
    }

    public Builder allowBareNullLiteral(boolean allowBareNullLiteral) {
      this.allowBareNullLiteral = allowBareNullLiteral;
      return this;
    }

    public Builder limits(ExecutionLimits limits) {
      this.limits = Objects.requireNonNull(limits, "limits must not be null");
      return this;
    }

    public Builder securityPolicy(VtlSecurityPolicy securityPolicy) {
      this.securityPolicy =
          Objects.requireNonNull(securityPolicy, "securityPolicy must not be null");
      return this;
    }

    public Builder resourceResolver(TemplateResourceResolver resourceResolver) {
      this.resourceResolver =
          Objects.requireNonNull(resourceResolver, "resourceResolver must not be null");
      return this;
    }

    public Builder executionTier(ExecutionTier executionTier) {
      this.executionTier = Objects.requireNonNull(executionTier, "executionTier must not be null");
      return this;
    }

    public Builder optimizationOptions(IrOptimizationOptions optimizationOptions) {
      this.optimizationOptions =
          Objects.requireNonNull(optimizationOptions, "optimizationOptions must not be null");
      return this;
    }

    public VtlInterpreterOptions build() {
      return new VtlInterpreterOptions(
          profile,
          strictReferences,
          setNullAllowed,
          emptyCheck,
          spaceGobbling,
          allowBareNullLiteral,
          limits,
          securityPolicy,
          resourceResolver,
          executionTier,
          optimizationOptions);
    }
  }
}
