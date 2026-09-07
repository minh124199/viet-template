package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.ClasspathTemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
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
  private OptimizationLevel optimizationLevel = OptimizationLevel.O2;
  private IrOptimizationOptions optimizationOptions =
      IrOptimizationOptions.forLevel(OptimizationLevel.O2);
  private VtlSemanticOptions semanticOptions = VtlSemanticOptions.builder().build();
  private VtlInterpreterOptions interpreterOptions = VtlInterpreterOptions.DEFAULT;

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
    return this;
  }

  @Override
  public VtlTemplateEngine build() {
    TemplateCompileCache cache =
        new TemplateCompileCache(maxCacheEntries, negativeCacheTtlMillis, maxNegativeEntries);
    return new VtlTemplateEngine(
        repository,
        cache,
        rejectRuntimeCompilation,
        executionTier,
        optimizationLevel,
        optimizationOptions,
        semanticOptions,
        interpreterOptions,
        hotReload,
        watchDebounceMillis);
  }
}
