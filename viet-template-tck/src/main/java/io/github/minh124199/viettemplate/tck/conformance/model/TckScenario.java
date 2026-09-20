package io.github.minh124199.viettemplate.tck.conformance.model;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineBuilder;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Executable conformance scenario verifying a single language feature claim. */
public final class TckScenario {

  private final String id;
  private final String featureId;
  private final String template;
  private final Supplier<Map<String, Object>> contextSupplier;
  private final String expectedOutput;
  private final String expectedErrorCode;
  private final Class<? extends Throwable> expectedExceptionClass;
  private final Set<ExecutionTier> backends;
  private final Map<String, String> resources;
  private final VtlProfile profile;
  private final Consumer<VtlTemplateEngineBuilder> engineCustomizer;

  private TckScenario(Builder builder) {
    this.id = Objects.requireNonNull(builder.id, "id must not be null");
    this.featureId = Objects.requireNonNull(builder.featureId, "featureId must not be null");
    this.template = Objects.requireNonNull(builder.template, "template must not be null");
    this.contextSupplier =
        builder.contextSupplier != null ? builder.contextSupplier : Collections::emptyMap;
    this.expectedOutput = builder.expectedOutput;
    this.expectedErrorCode = builder.expectedErrorCode;
    this.expectedExceptionClass = builder.expectedExceptionClass;
    this.backends =
        builder.backends.isEmpty()
            ? Set.of(ExecutionTier.IR, ExecutionTier.AOT_BYTECODE)
            : Set.copyOf(builder.backends);
    this.resources = Map.copyOf(builder.resources);
    this.profile = builder.profile;
    this.engineCustomizer = builder.engineCustomizer;

    if (expectedOutput == null && expectedExceptionClass == null) {
      throw new IllegalArgumentException(
          "Scenario '" + id + "' must declare either expectedOutput or expectedExceptionClass");
    }
  }

  public String id() {
    return id;
  }

  public String featureId() {
    return featureId;
  }

  public String template() {
    return template;
  }

  public Map<String, Object> createContext() {
    return contextSupplier.get();
  }

  public String expectedOutput() {
    return expectedOutput;
  }

  public String expectedErrorCode() {
    return expectedErrorCode;
  }

  public Class<? extends Throwable> expectedExceptionClass() {
    return expectedExceptionClass;
  }

  public Set<ExecutionTier> backends() {
    return backends;
  }

  public Map<String, String> resources() {
    return resources;
  }

  public VtlProfile profile() {
    return profile;
  }

  public Consumer<VtlTemplateEngineBuilder> engineCustomizer() {
    return engineCustomizer;
  }

  public boolean expectsError() {
    return expectedExceptionClass != null;
  }

  public static Builder builder(String id, String featureId) {
    return new Builder(id, featureId);
  }

  public static final class Builder {
    private final String id;
    private final String featureId;
    private String template;
    private Supplier<Map<String, Object>> contextSupplier = Collections::emptyMap;
    private String expectedOutput;
    private String expectedErrorCode;
    private Class<? extends Throwable> expectedExceptionClass;
    private Set<ExecutionTier> backends = Set.of(ExecutionTier.IR, ExecutionTier.AOT_BYTECODE);
    private final Map<String, String> resources = new HashMap<>();
    private VtlProfile profile;
    private Consumer<VtlTemplateEngineBuilder> engineCustomizer;

    public Builder(String id, String featureId) {
      this.id = Objects.requireNonNull(id, "id must not be null");
      this.featureId = Objects.requireNonNull(featureId, "featureId must not be null");
    }

    public Builder template(String template) {
      this.template = template;
      return this;
    }

    public Builder context(Map<String, Object> context) {
      this.contextSupplier = () -> new HashMap<>(context);
      return this;
    }

    public Builder contextSupplier(Supplier<Map<String, Object>> supplier) {
      this.contextSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
      return this;
    }

    public Builder expectedOutput(String expectedOutput) {
      this.expectedOutput = expectedOutput;
      return this;
    }

    public Builder expectedError(Class<? extends Throwable> exClass, String errorCode) {
      this.expectedExceptionClass = Objects.requireNonNull(exClass, "exClass must not be null");
      this.expectedErrorCode = errorCode;
      return this;
    }

    public Builder expectedException(Class<? extends Throwable> exClass) {
      this.expectedExceptionClass = Objects.requireNonNull(exClass, "exClass must not be null");
      return this;
    }

    public Builder backends(Set<ExecutionTier> backends) {
      this.backends = Set.copyOf(backends);
      return this;
    }

    public Builder backends(ExecutionTier... backends) {
      this.backends = Set.of(backends);
      return this;
    }

    public Builder resource(String name, String content) {
      this.resources.put(name, content);
      return this;
    }

    public Builder resources(Map<String, String> resources) {
      this.resources.putAll(resources);
      return this;
    }

    public Builder profile(VtlProfile profile) {
      this.profile = profile;
      return this;
    }

    public Builder engineCustomizer(Consumer<VtlTemplateEngineBuilder> customizer) {
      this.engineCustomizer = customizer;
      return this;
    }

    public TckScenario build() {
      return new TckScenario(this);
    }
  }
}
