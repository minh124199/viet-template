package io.github.minh124199.viettemplate.language.vtl.semantics;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import java.util.Objects;

/** Configuration options governing template semantic analysis and type checking. */
public record VtlSemanticOptions(
    VtlProfile profile,
    ModelSchema modelSchema,
    TypeCheckingMode typeCheckingMode,
    boolean allowArbitraryMethods,
    MemberAccessPolicy memberAccessPolicy,
    IrEscapeMode escapeMode) {

  public VtlSemanticOptions {
    Objects.requireNonNull(profile, "profile must not be null");
    Objects.requireNonNull(modelSchema, "modelSchema must not be null");
    typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
    memberAccessPolicy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();
    escapeMode =
        escapeMode != null
            ? escapeMode
            : ((profile == VtlProfile.VTL_SAFE) ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW);
  }

  public VtlSemanticOptions(
      VtlProfile profile,
      ModelSchema modelSchema,
      TypeCheckingMode typeCheckingMode,
      boolean allowArbitraryMethods,
      MemberAccessPolicy memberAccessPolicy) {
    this(
        profile,
        modelSchema,
        typeCheckingMode,
        allowArbitraryMethods,
        memberAccessPolicy,
        (profile == VtlProfile.VTL_SAFE) ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW);
  }

  public VtlSemanticOptions(
      VtlProfile profile,
      ModelSchema modelSchema,
      boolean strictMode,
      boolean allowArbitraryMethods,
      MemberAccessPolicy memberAccessPolicy) {
    this(
        profile,
        modelSchema,
        strictMode ? TypeCheckingMode.ERROR : TypeCheckingMode.OFF,
        allowArbitraryMethods,
        memberAccessPolicy);
  }

  public VtlSemanticOptions(
      VtlProfile profile,
      ModelSchema modelSchema,
      boolean strictMode,
      boolean allowArbitraryMethods) {
    this(
        profile,
        modelSchema,
        strictMode ? TypeCheckingMode.ERROR : TypeCheckingMode.OFF,
        allowArbitraryMethods,
        MemberAccessPolicy.standard());
  }

  public VtlSemanticOptions(
      VtlProfile profile,
      ModelSchema modelSchema,
      TypeCheckingMode typeCheckingMode,
      boolean allowArbitraryMethods) {
    this(
        profile,
        modelSchema,
        typeCheckingMode,
        allowArbitraryMethods,
        MemberAccessPolicy.standard());
  }

  public boolean strictMode() {
    return typeCheckingMode != TypeCheckingMode.OFF;
  }

  public static VtlSemanticOptions defaults() {
    return new VtlSemanticOptions(
        VtlProfile.VTL_CORE,
        ModelSchema.empty(),
        TypeCheckingMode.OFF,
        false,
        MemberAccessPolicy.standard());
  }

  public static VtlSemanticOptions of(ModelSchema modelSchema) {
    return new VtlSemanticOptions(
        VtlProfile.VTL_CORE,
        modelSchema,
        TypeCheckingMode.ERROR,
        false,
        MemberAccessPolicy.standard());
  }

  public static VtlSemanticOptions of(VtlProfile profile, ModelSchema modelSchema) {
    return new VtlSemanticOptions(
        profile,
        modelSchema,
        TypeCheckingMode.ERROR,
        profile.isArbitraryMethodsAllowed(),
        MemberAccessPolicy.standard());
  }

  public Builder toBuilder() {
    Builder b =
        new Builder()
            .profile(profile)
            .modelSchema(modelSchema)
            .typeCheckingMode(typeCheckingMode)
            .allowArbitraryMethods(allowArbitraryMethods)
            .memberAccessPolicy(memberAccessPolicy);
    if (escapeMode
        != ((profile == VtlProfile.VTL_SAFE) ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW)) {
      b.escapeMode(escapeMode);
    }
    return b;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private VtlProfile profile = VtlProfile.VTL_CORE;
    private ModelSchema modelSchema = ModelSchema.empty();
    private TypeCheckingMode typeCheckingMode = TypeCheckingMode.OFF;
    private Boolean allowArbitraryMethods = null;
    private MemberAccessPolicy memberAccessPolicy = MemberAccessPolicy.standard();
    private IrEscapeMode escapeMode;

    private Builder() {}

    public Builder profile(VtlProfile profile) {
      this.profile = Objects.requireNonNull(profile, "profile must not be null");
      return this;
    }

    public Builder modelSchema(ModelSchema modelSchema) {
      this.modelSchema = Objects.requireNonNull(modelSchema, "modelSchema must not be null");
      return this;
    }

    public Builder typeCheckingMode(TypeCheckingMode typeCheckingMode) {
      this.typeCheckingMode = typeCheckingMode != null ? typeCheckingMode : TypeCheckingMode.OFF;
      return this;
    }

    public Builder strictMode(boolean strictMode) {
      this.typeCheckingMode = strictMode ? TypeCheckingMode.ERROR : TypeCheckingMode.OFF;
      return this;
    }

    public Builder allowArbitraryMethods(boolean allowArbitraryMethods) {
      this.allowArbitraryMethods = allowArbitraryMethods;
      return this;
    }

    public Builder memberAccessPolicy(MemberAccessPolicy memberAccessPolicy) {
      this.memberAccessPolicy =
          Objects.requireNonNull(memberAccessPolicy, "memberAccessPolicy must not be null");
      return this;
    }

    public Builder escapeMode(IrEscapeMode escapeMode) {
      this.escapeMode = escapeMode;
      return this;
    }

    public Builder autoEscape(boolean autoEscape) {
      this.escapeMode = autoEscape ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW;
      return this;
    }

    public VtlSemanticOptions build() {
      boolean allow =
          allowArbitraryMethods != null
              ? allowArbitraryMethods
              : profile.isArbitraryMethodsAllowed();
      IrEscapeMode mode =
          escapeMode != null
              ? escapeMode
              : ((profile == VtlProfile.VTL_SAFE) ? IrEscapeMode.HTML_TEXT : IrEscapeMode.RAW);
      return new VtlSemanticOptions(
          profile,
          modelSchema,
          typeCheckingMode,
          allow,
          memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard(),
          mode);
    }
  }
}
