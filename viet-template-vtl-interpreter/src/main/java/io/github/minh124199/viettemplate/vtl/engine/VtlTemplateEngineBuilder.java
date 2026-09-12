package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.ContextCollisionPolicy;
import io.github.minh124199.viettemplate.api.GlobalMacroPrecedence;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.RenderContextContributor;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fluent builder for constructing {@link VtlTemplateEngine} instances. */
public final class VtlTemplateEngineBuilder implements TemplateEngine.Builder {

  private TemplateRepository repository = ClasspathTemplateRepository.of("");
  private boolean rejectRuntimeCompilation = false;
  private int maxCacheEntries = 500;
  private long negativeCacheTtlMillis = 5000L;
  private int maxNegativeEntries = 200;
  private boolean hotReload = false;
  private long watchDebounceMillis = 50L;

  private ExecutionTier executionTier = ExecutionTier.AOT_BYTECODE;
  private boolean executionTierExplicitlySet = false;
  private OptimizationLevel optimizationLevel = OptimizationLevel.O2;
  private IrOptimizationOptions optimizationOptions =
      IrOptimizationOptions.forLevel(OptimizationLevel.O2);
  private VtlSemanticOptions semanticOptions = VtlSemanticOptions.builder().build();
  private VtlInterpreterOptions interpreterOptions = VtlInterpreterOptions.DEFAULT;

  private TemplateDependencyGraph dependencyGraph = new DefaultTemplateDependencyGraph();
  private List<TemplateId> globalMacroLibraries = List.of();
  private GlobalMacroPrecedence globalMacroPrecedence = GlobalMacroPrecedence.LAST_WINS;
  private final List<RenderContextContributor> contextContributors = new ArrayList<>();
  private ContextCollisionPolicy contextCollisionPolicy = ContextCollisionPolicy.MODEL_WINS;
  private LayoutConfiguration layoutConfiguration = LayoutConfiguration.builder().build();
  private io.github.minh124199.viettemplate.api.MemberAccessPolicy memberAccessPolicy = null;

  public VtlTemplateEngineBuilder() {}

  @Override
  public VtlTemplateEngineBuilder repository(TemplateRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder rejectRuntimeCompilation(boolean reject) {
    this.rejectRuntimeCompilation = reject;
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder maxCacheEntries(int maxEntries) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxCacheEntries must be positive: " + maxEntries);
    }
    this.maxCacheEntries = maxEntries;
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder negativeCacheTtlMillis(long ttlMillis) {
    this.negativeCacheTtlMillis = Math.max(0L, ttlMillis);
    return this;
  }

  public VtlTemplateEngineBuilder maxNegativeEntries(int maxEntries) {
    this.maxNegativeEntries = Math.max(1, maxEntries);
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder hotReload(boolean enabled) {
    this.hotReload = enabled;
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder watchDebounceMillis(long millis) {
    this.watchDebounceMillis = Math.max(10L, millis);
    return this;
  }

  public VtlTemplateEngineBuilder executionTier(ExecutionTier tier) {
    this.executionTier = Objects.requireNonNull(tier, "tier must not be null");
    this.executionTierExplicitlySet = true;
    return this;
  }

  public VtlTemplateEngineBuilder optimizationLevel(OptimizationLevel level) {
    this.optimizationLevel = Objects.requireNonNull(level, "level must not be null");
    this.optimizationOptions = IrOptimizationOptions.forLevel(level);
    return this;
  }

  public VtlTemplateEngineBuilder semanticOptions(VtlSemanticOptions options) {
    this.semanticOptions = Objects.requireNonNull(options, "options must not be null");
    return this;
  }

  public VtlTemplateEngineBuilder interpreterOptions(VtlInterpreterOptions options) {
    this.interpreterOptions = Objects.requireNonNull(options, "options must not be null");
    if (!executionTierExplicitlySet && options.executionTier() != null) {
      this.executionTier = options.executionTier();
    }
    return this;
  }

  public VtlTemplateEngineBuilder dependencyGraph(TemplateDependencyGraph dependencyGraph) {
    this.dependencyGraph =
        Objects.requireNonNull(dependencyGraph, "dependencyGraph must not be null");
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder globalMacroLibraries(List<TemplateId> libraries) {
    this.globalMacroLibraries =
        List.copyOf(Objects.requireNonNull(libraries, "libraries must not be null"));
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder globalMacroPrecedence(GlobalMacroPrecedence precedence) {
    this.globalMacroPrecedence = Objects.requireNonNull(precedence, "precedence must not be null");
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder addContextContributor(RenderContextContributor contributor) {
    Objects.requireNonNull(contributor, "contributor must not be null");
    this.contextContributors.add(contributor);
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder contextContributors(List<RenderContextContributor> contributors) {
    Objects.requireNonNull(contributors, "contributors must not be null");
    this.contextContributors.clear();
    this.contextContributors.addAll(contributors);
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder contextCollisionPolicy(ContextCollisionPolicy policy) {
    this.contextCollisionPolicy = Objects.requireNonNull(policy, "policy must not be null");
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder layoutConfiguration(LayoutConfiguration configuration) {
    this.layoutConfiguration =
        Objects.requireNonNull(configuration, "configuration must not be null");
    return this;
  }

  @Override
  public VtlTemplateEngineBuilder memberAccessPolicy(
      io.github.minh124199.viettemplate.api.MemberAccessPolicy policy) {
    this.memberAccessPolicy = Objects.requireNonNull(policy, "policy must not be null");
    return this;
  }

  @Override
  public VtlTemplateEngine build() {
    VtlInterpreterOptions effectiveInterpreterOptions = interpreterOptions;
    if (memberAccessPolicy != null) {
      effectiveInterpreterOptions =
          effectiveInterpreterOptions.toBuilder()
              .securityPolicy(
                  io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy.of(
                      memberAccessPolicy))
              .build();
    }
    ExecutionTier effectiveExecutionTier = executionTier;
    if (!executionTierExplicitlySet
        && interpreterOptions != VtlInterpreterOptions.DEFAULT
        && interpreterOptions.executionTier() != null) {
      effectiveExecutionTier = interpreterOptions.executionTier();
    }
    if (effectiveInterpreterOptions.executionTier() != effectiveExecutionTier) {
      effectiveInterpreterOptions =
          effectiveInterpreterOptions.toBuilder().executionTier(effectiveExecutionTier).build();
    }
    VtlSemanticOptions effectiveSemanticOptions = semanticOptions;
    if (effectiveInterpreterOptions.profile() != null
        && (effectiveSemanticOptions.profile() != effectiveInterpreterOptions.profile()
            || effectiveSemanticOptions.strictMode()
                != effectiveInterpreterOptions.strictReferences())) {
      effectiveSemanticOptions =
          effectiveSemanticOptions.toBuilder()
              .profile(effectiveInterpreterOptions.profile())
              .strictMode(effectiveInterpreterOptions.strictReferences())
              .build();
    }
    TemplateCompileCache cache =
        new TemplateCompileCache(maxCacheEntries, negativeCacheTtlMillis, maxNegativeEntries);
    return new VtlTemplateEngine(
        repository,
        cache,
        rejectRuntimeCompilation,
        effectiveExecutionTier,
        optimizationLevel,
        optimizationOptions,
        effectiveSemanticOptions,
        effectiveInterpreterOptions,
        hotReload,
        watchDebounceMillis,
        dependencyGraph,
        globalMacroLibraries,
        globalMacroPrecedence,
        contextContributors,
        contextCollisionPolicy,
        layoutConfiguration);
  }
}
