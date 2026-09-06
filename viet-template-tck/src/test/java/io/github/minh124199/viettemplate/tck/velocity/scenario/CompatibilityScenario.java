package io.github.minh124199.viettemplate.tck.velocity.scenario;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable test scenario for differential compatibility testing.
 *
 * @param id Unique, stable scenario identifier (e.g. {@code truthiness.number.zero}).
 * @param category Functional category.
 * @param template Template source code to execute.
 * @param contextFactory Factory supplying independent fresh contexts.
 * @param configuration Engine settings.
 * @param resources In-memory template resources (for #parse and #include).
 * @param observableContextKeys Variable names whose final state in the context should be compared.
 * @param tags Optional classification and filter tags.
 */
public record CompatibilityScenario(
    String id,
    ScenarioCategory category,
    String template,
    ContextFactory contextFactory,
    CompatibilityConfiguration configuration,
    Map<String, String> resources,
    Set<String> observableContextKeys,
    Set<String> tags) {

  public CompatibilityScenario {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(template, "template must not be null");
    contextFactory = contextFactory != null ? contextFactory : ContextFactory.empty();
    configuration =
        configuration != null ? configuration : CompatibilityConfiguration.defaultConfiguration();
    resources = resources != null ? Collections.unmodifiableMap(resources) : Collections.emptyMap();
    observableContextKeys =
        observableContextKeys != null
            ? Collections.unmodifiableSet(new HashSet<>(observableContextKeys))
            : Collections.emptySet();
    tags = tags != null ? Collections.unmodifiableSet(new HashSet<>(tags)) : Collections.emptySet();
  }

  public static Builder builder(String id, ScenarioCategory category) {
    return new Builder(id, category);
  }

  public static CompatibilityScenario simple(
      String id, ScenarioCategory category, String template) {
    return builder(id, category).template(template).build();
  }

  public static CompatibilityScenario of(
      String id, ScenarioCategory category, String template, ContextFactory contextFactory) {
    return builder(id, category).template(template).contextFactory(contextFactory).build();
  }

  public static class Builder {
    private final String id;
    private final ScenarioCategory category;
    private String template = "";
    private ContextFactory contextFactory = ContextFactory.empty();
    private CompatibilityConfiguration configuration =
        CompatibilityConfiguration.defaultConfiguration();
    private Map<String, String> resources = Collections.emptyMap();
    private Set<String> observableContextKeys = Collections.emptySet();
    private Set<String> tags = Collections.emptySet();

    public Builder(String id, ScenarioCategory category) {
      this.id = id;
      this.category = category;
    }

    public Builder template(String template) {
      this.template = template;
      return this;
    }

    public Builder contextFactory(ContextFactory contextFactory) {
      this.contextFactory = contextFactory;
      return this;
    }

    public Builder configuration(CompatibilityConfiguration configuration) {
      this.configuration = configuration;
      return this;
    }

    public Builder resources(Map<String, String> resources) {
      this.resources = resources;
      return this;
    }

    public Builder observeContext(String... keys) {
      this.observableContextKeys = Set.of(keys);
      return this;
    }

    public Builder tags(String... tags) {
      this.tags = Set.of(tags);
      return this;
    }

    public CompatibilityScenario build() {
      return new CompatibilityScenario(
          id,
          category,
          template,
          contextFactory,
          configuration,
          resources,
          observableContextKeys,
          tags);
    }
  }
}
