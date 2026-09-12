package io.github.minh124199.viettemplate.vtl.compiler;

import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import java.util.Objects;
import java.util.Optional;

/** Configuration options for template execution/compilation backends. */
public final class BackendOptions {

  private final IrOptimizationOptions optimizationOptions;
  private final LinkerAccessPolicy securityPolicy;
  private final ModelSchema modelSchema;
  private final String packagePrefix;
  private final boolean failOnDynamicFallback;
  private final boolean includeDebugMetadata;
  private final int methodSplitThreshold;
  private final TemplateClassLoader classLoader;
  private final boolean setNullAllowed;
  private final boolean strictReferences;

  private BackendOptions(Builder builder) {
    this.optimizationOptions = builder.optimizationOptions;
    this.securityPolicy = builder.securityPolicy;
    this.modelSchema = builder.modelSchema;
    this.packagePrefix = builder.packagePrefix;
    this.failOnDynamicFallback = builder.failOnDynamicFallback;
    this.includeDebugMetadata = builder.includeDebugMetadata;
    this.methodSplitThreshold = builder.methodSplitThreshold;
    this.classLoader = builder.classLoader;
    this.setNullAllowed = builder.setNullAllowed;
    this.strictReferences = builder.strictReferences;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BackendOptions defaults() {
    return builder().build();
  }

  public static BackendOptions defaultOptions() {
    return defaults();
  }

  public IrOptimizationOptions optimizationOptions() {
    return optimizationOptions;
  }

  public LinkerAccessPolicy securityPolicy() {
    return securityPolicy;
  }

  public Optional<ModelSchema> modelSchema() {
    return Optional.ofNullable(modelSchema);
  }

  public String packagePrefix() {
    return packagePrefix;
  }

  public boolean failOnDynamicFallback() {
    return failOnDynamicFallback;
  }

  public boolean includeDebugMetadata() {
    return includeDebugMetadata;
  }

  public int methodSplitThreshold() {
    return methodSplitThreshold;
  }

  public Optional<TemplateClassLoader> classLoader() {
    return Optional.ofNullable(classLoader);
  }

  public boolean setNullAllowed() {
    return setNullAllowed;
  }

  public boolean strictReferences() {
    return strictReferences;
  }

  public static final class Builder {
    private IrOptimizationOptions optimizationOptions = IrOptimizationOptions.o2();
    private LinkerAccessPolicy securityPolicy = LinkerAccessPolicy.standard();
    private ModelSchema modelSchema;
    private String packagePrefix = "io.github.minh124199.viettemplate.generated";
    private boolean failOnDynamicFallback = false;
    private boolean includeDebugMetadata = true;
    private int methodSplitThreshold = 500;
    private TemplateClassLoader classLoader;
    private boolean setNullAllowed = true;
    private boolean strictReferences = false;

    public Builder optimizationOptions(IrOptimizationOptions options) {
      this.optimizationOptions = Objects.requireNonNull(options, "options must not be null");
      return this;
    }

    public Builder securityPolicy(LinkerAccessPolicy policy) {
      this.securityPolicy = Objects.requireNonNull(policy, "policy must not be null");
      return this;
    }

    public Builder modelSchema(ModelSchema schema) {
      this.modelSchema = schema;
      return this;
    }

    public Builder packagePrefix(String prefix) {
      this.packagePrefix = Objects.requireNonNull(prefix, "prefix must not be null");
      return this;
    }

    public Builder failOnDynamicFallback(boolean fail) {
      this.failOnDynamicFallback = fail;
      return this;
    }

    public Builder includeDebugMetadata(boolean include) {
      this.includeDebugMetadata = include;
      return this;
    }

    public Builder methodSplitThreshold(int threshold) {
      this.methodSplitThreshold = threshold;
      return this;
    }

    public Builder classLoader(TemplateClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
    }

    public Builder setNullAllowed(boolean setNullAllowed) {
      this.setNullAllowed = setNullAllowed;
      return this;
    }

    public Builder strictReferences(boolean strictReferences) {
      this.strictReferences = strictReferences;
      return this;
    }

    public BackendOptions build() {
      return new BackendOptions(this);
    }
  }
}
