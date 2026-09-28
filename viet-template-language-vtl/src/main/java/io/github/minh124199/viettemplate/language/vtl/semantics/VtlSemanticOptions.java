package io.github.minh124199.viettemplate.language.vtl.semantics;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import java.util.Objects;

/** Configuration options governing template semantic analysis and type checking. */
public record VtlSemanticOptions(
    VtlProfile profile,
    ModelSchema modelSchema,
    boolean strictMode,
    boolean allowArbitraryMethods,
    MemberAccessPolicy memberAccessPolicy) {

  public VtlSemanticOptions {
    Objects.requireNonNull(profile, "profile must not be null");
    Objects.requireNonNull(modelSchema, "modelSchema must not be null");
    memberAccessPolicy =
        memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard();
  }

  public VtlSemanticOptions(
      VtlProfile profile,
      ModelSchema modelSchema,
      boolean strictMode,
      boolean allowArbitraryMethods) {
    this(profile, modelSchema, strictMode, allowArbitraryMethods, MemberAccessPolicy.standard());
  }

  public static VtlSemanticOptions defaults() {
    return new VtlSemanticOptions(
        VtlProfile.VTL_CORE, ModelSchema.empty(), false, false, MemberAccessPolicy.standard());
  }

  public static VtlSemanticOptions of(ModelSchema modelSchema) {
    return new VtlSemanticOptions(
        VtlProfile.VTL_CORE, modelSchema, true, false, MemberAccessPolicy.standard());
  }

  public static VtlSemanticOptions of(VtlProfile profile, ModelSchema modelSchema) {
    return new VtlSemanticOptions(
        profile,
        modelSchema,
        true,
        profile.isArbitraryMethodsAllowed(),
        MemberAccessPolicy.standard());
  }

  public Builder toBuilder() {
    return new Builder()
        .profile(profile)
        .modelSchema(modelSchema)
        .strictMode(strictMode)
        .allowArbitraryMethods(allowArbitraryMethods)
        .memberAccessPolicy(memberAccessPolicy);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private VtlProfile profile = VtlProfile.VTL_CORE;
    private ModelSchema modelSchema = ModelSchema.empty();
    private boolean strictMode = false;
    private Boolean allowArbitraryMethods = null;
    private MemberAccessPolicy memberAccessPolicy = MemberAccessPolicy.standard();

    private Builder() {}

    public Builder profile(VtlProfile profile) {
      this.profile = Objects.requireNonNull(profile, "profile must not be null");
      return this;
    }

    public Builder modelSchema(ModelSchema modelSchema) {
      this.modelSchema = Objects.requireNonNull(modelSchema, "modelSchema must not be null");
      return this;
    }

    public Builder strictMode(boolean strictMode) {
      this.strictMode = strictMode;
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

    public VtlSemanticOptions build() {
      boolean allow =
          allowArbitraryMethods != null
              ? allowArbitraryMethods
              : profile.isArbitraryMethodsAllowed();
      return new VtlSemanticOptions(
          profile,
          modelSchema,
          strictMode,
          allow,
          memberAccessPolicy != null ? memberAccessPolicy : MemberAccessPolicy.standard());
    }
  }
}
