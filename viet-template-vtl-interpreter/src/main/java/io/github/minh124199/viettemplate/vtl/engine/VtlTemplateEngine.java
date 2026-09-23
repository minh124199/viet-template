package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.linker.CallSiteRegistry;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.PreparedTemplateEntry;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.internal.engine.context.ContributingContextComposer;
import io.github.minh124199.viettemplate.vtl.internal.engine.layout.DefaultLayoutRenderPlan;
import io.github.minh124199.viettemplate.vtl.internal.engine.macro.GlobalMacroManager;
import io.github.minh124199.viettemplate.vtl.internal.engine.watcher.DevelopmentFileWatcher;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResource;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResourceResolver;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical reference implementation of {@link TemplateEngine}. */
public final class VtlTemplateEngine implements TemplateEngine, AutoCloseable {

  private static final String COMPILER_VERSION = "0.2.0";

  private final TemplateRepository repository;
  private final TemplateCompileCache cache;
  private final boolean rejectRuntimeCompilation;
  private final ExecutionTier executionTier;
  private final OptimizationLevel optimizationLevel;
  private final IrOptimizationOptions optimizationOptions;
  private final VtlSemanticOptions semanticOptions;
  private final VtlInterpreterOptions interpreterOptions;
  private final CallSiteRegistry callSiteRegistry;
  private final VtlInterpreter interpreter;
  private final Optional<DevelopmentFileWatcher> fileWatcher;

  private final GlobalMacroManager globalMacroManager;
  private final List<RenderContextContributor> contextContributors;
  private final ContextCollisionPolicy contextCollisionPolicy;
  private final LayoutConfiguration layoutConfiguration;
  private final EngineFingerprint engineFingerprint;

  private final AotTemplateRegistry aotRegistry;
  private final TemplateDependencyCoordinator dependencyCoordinator;
  private final TemplateCompilationCoordinator compilationCoordinator;
  final ThreadLocal<Set<TemplateId>> compilingTemplates;

  VtlTemplateEngine(
      TemplateRepository repository,
      TemplateCompileCache cache,
      boolean rejectRuntimeCompilation,
      ExecutionTier executionTier,
      OptimizationLevel optimizationLevel,
      IrOptimizationOptions optimizationOptions,
      VtlSemanticOptions semanticOptions,
      VtlInterpreterOptions interpreterOptions,
      boolean enableWatcher,
      long watchDebounceMillis,
      TemplateDependencyGraph dependencyGraph,
      List<TemplateId> globalMacroLibraries,
      GlobalMacroPrecedence globalMacroPrecedence,
      List<RenderContextContributor> contextContributors,
      ContextCollisionPolicy contextCollisionPolicy,
      LayoutConfiguration layoutConfiguration) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.rejectRuntimeCompilation = rejectRuntimeCompilation;
    this.executionTier = Objects.requireNonNull(executionTier, "executionTier must not be null");
    this.optimizationLevel =
        Objects.requireNonNull(optimizationLevel, "optimizationLevel must not be null");
    this.optimizationOptions =
        Objects.requireNonNull(optimizationOptions, "optimizationOptions must not be null");
    this.semanticOptions =
        Objects.requireNonNull(semanticOptions, "semanticOptions must not be null");

    TemplateResourceResolver originalResolver =
        interpreterOptions != null
            ? interpreterOptions.resourceResolver()
            : TemplateResourceResolver.empty();
    TemplateResourceResolver engineResolver =
        (current, path) -> {
          Optional<TemplateResource> custom = originalResolver.resolve(current, path);
          if (custom.isPresent()) {
            return custom;
          }
          try {
            TemplateId targetId = TemplateId.normalize(path);
            try {
              get(targetId);
            } catch (Exception ignored) {
            }
            return repository
                .find(targetId)
                .map(src -> new TemplateResource(src.id(), src.content()));
          } catch (Exception e) {
            return Optional.empty();
          }
        };

    this.interpreterOptions =
        (interpreterOptions != null ? interpreterOptions : VtlInterpreterOptions.DEFAULT)
            .toBuilder().executionTier(executionTier).resourceResolver(engineResolver).build();

    this.callSiteRegistry = new CallSiteRegistry();
    this.interpreter =
        EngineInterpreterBridge.create(this.interpreterOptions, this.callSiteRegistry);

    TemplateDependencyGraph depGraph =
        dependencyGraph != null ? dependencyGraph : new DefaultTemplateDependencyGraph();
    this.dependencyCoordinator = new TemplateDependencyCoordinator(depGraph);
    this.compilingTemplates = this.dependencyCoordinator.compilingTemplates();

    this.globalMacroManager =
        new GlobalMacroManager(
            repository,
            globalMacroLibraries != null ? globalMacroLibraries : List.of(),
            globalMacroPrecedence != null ? globalMacroPrecedence : GlobalMacroPrecedence.LAST_WINS,
            semanticOptions,
            interpreterOptions,
            optimizationOptions);
    this.contextContributors =
        contextContributors != null ? List.copyOf(contextContributors) : List.of();
    this.contextCollisionPolicy =
        contextCollisionPolicy != null ? contextCollisionPolicy : ContextCollisionPolicy.MODEL_WINS;
    this.layoutConfiguration =
        layoutConfiguration != null ? layoutConfiguration : LayoutConfiguration.builder().build();
    this.engineFingerprint =
        new EngineFingerprint(
            COMPILER_VERSION,
            this.optimizationLevel,
            this.executionTier,
            this.interpreterOptions.securityPolicy().policyFingerprint(),
            this.semanticOptions.modelSchema().parameters().toString(),
            this.interpreterOptions.profile().name() + ":" + this.semanticOptions.profile().name());

    if (enableWatcher && repository instanceof FilesystemTemplateRepository fsRepo) {
      try {
        this.fileWatcher =
            Optional.of(
                new DevelopmentFileWatcher(
                    fsRepo.rootDirectory(),
                    watchDebounceMillis,
                    changedPath -> {
                      try {
                        Path rel = fsRepo.rootDirectory().relativize(changedPath);
                        TemplateId id = TemplateId.normalize(rel.toString());
                        invalidateWithDependents(id);
                      } catch (Exception ignored) {
                      }
                    }));
      } catch (IOException e) {
        throw new IllegalStateException("Failed to initialize development filesystem watcher", e);
      }
    } else {
      this.fileWatcher = Optional.empty();
    }

    this.compilationCoordinator =
        new TemplateCompilationCoordinator(
            this.optimizationLevel,
            this.optimizationOptions,
            this.executionTier,
            this.interpreterOptions,
            this.semanticOptions,
            this.globalMacroManager,
            this.dependencyCoordinator,
            this.interpreter,
            this.layoutConfiguration,
            this.cache,
            this::get);

    this.aotRegistry =
        AotTemplateRegistry.create(
            repository,
            this.engineFingerprint,
            this.interpreterOptions,
            this.semanticOptions,
            this.interpreter);
  }

  public static VtlTemplateEngineBuilder builder() {
    return new VtlTemplateEngineBuilder();
  }

  @Override
  public Template get(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");

    // 0. AOT precompiled template registry check
    Optional<Template> aotTemplate = aotRegistry.find(id);
    if (aotTemplate.isPresent()) {
      return aotTemplate.get();
    }

    // Fast-path generation-aware cache check
    Optional<PreparedTemplateEntry> activeOpt = cache.getActiveEntry(id);
    if (activeOpt.isPresent()) {
      PreparedTemplateEntry active = activeOpt.get();
      if (active.freshnessToken() != null && active.macroGeneration() == globalMacroManager.generation()) {
        Optional<FreshnessToken> currentToken = repository.freshnessToken(id);
        if (currentToken.isPresent() && currentToken.get().equals(active.freshnessToken())) {
          return active.templateInstance();
        }
      }
    }

    // 1. Negative cache check
    if (cache.isNegativelyCached(id)) {
      throw new TemplateResourceException(
          "Template not found (cached negative lookup): " + id.value(),
          id,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("RESOURCE", "NOT_FOUND"));
    }

    // 2. Repository lookup
    Optional<TemplateSource> sourceOpt = repository.find(id);
    if (sourceOpt.isEmpty()) {
      cache.recordNegative(id, "Not found in repository");
      throw new TemplateResourceException(
          "Template not found in repository: " + id.value(),
          id,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("RESOURCE", "NOT_FOUND"));
    }

    TemplateSource source = sourceOpt.get();

    // 3. Multi-dimensional cache key
    String macroFingerprint = globalMacroManager.computeFingerprint();

    CompileCacheKey key =
        CompileCacheKey.of(id, source.fingerprint(), engineFingerprint, macroFingerprint);

    // 4. Cache hit check
    Optional<PreparedTemplateEntry> cachedEntry = cache.getEntry(key);
    if (cachedEntry.isPresent()) {
      return cachedEntry.get().templateInstance();
    }

    // 5. Hardened production check: reject runtime compilation
    if (rejectRuntimeCompilation) {
      throw new TemplateSecurityException(
          "Runtime template compilation is rejected in production mode: " + id.value(),
          id,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("SECURITY", "COMPILATION_REJECTED"));
    }

    // 6. Compilation on cache miss
    SourceText sourceText = SourceText.of(id, source.content());
    long generation = cache.nextGeneration();
    CompiledTemplateHandle compiledHandle =
        compilationCoordinator.compileTemplate(id, key, sourceText, generation);
    TemplateDescriptor descriptor = TemplateDescriptor.of(id, executionTier.name());
    VtlTemplate template =
        new VtlTemplate(descriptor, compiledHandle, sourceText, interpreterOptions, interpreter);
    Optional<FreshnessToken> freshness = repository.freshnessToken(id);
    PreparedTemplateEntry entry =
        new PreparedTemplateEntry(
            id,
            compiledHandle.generation(),
            key,
            compiledHandle,
            template,
            descriptor,
            freshness.orElse(null),
            globalMacroManager.generation());
    cache.put(entry);

    return template;
  }

  @Override
  public void render(RenderRequest request, TemplateOutput output) throws IOException {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(output, "output must not be null");

    ContributingContextComposer.CompositionResult composition =
        ContributingContextComposer.compose(
            request,
            contextContributors,
            contextCollisionPolicy,
            Set.of(layoutConfiguration.screenContentKey()),
            Map.of());

    io.github.minh124199.viettemplate.vtl.internal.interpreter.CountingTemplateOutput
        countingOutput =
            (output
                    instanceof
                    io.github.minh124199.viettemplate.vtl.internal.interpreter
                            .CountingTemplateOutput
                        cto)
                ? cto
                : new io.github.minh124199.viettemplate.vtl.internal.interpreter
                    .CountingTemplateOutput(
                    output, interpreterOptions.limits().createRenderBudget(), request.templateId());

    Optional<TemplateId> layout =
        layoutConfiguration.resolver().resolveLayout(request.templateId(), composition.context());
    if (layout.isPresent()) {
      LayoutRenderPlan plan = prepareLayoutPlan(request.templateId(), composition.context());
      plan.render(composition.context(), countingOutput);
    } else {
      Template template = get(request.templateId());
      template.render(composition.context(), countingOutput);
    }
  }

  @Override
  public LayoutRenderPlan prepareLayoutPlan(TemplateId screenId, RenderContext context) {
    Objects.requireNonNull(screenId, "screenId must not be null");
    return new DefaultLayoutRenderPlan(this, screenId, layoutConfiguration, interpreterOptions);
  }

  @Override
  public TemplateDependencyGraph dependencyGraph() {
    return dependencyCoordinator.graph();
  }

  @Override
  public Set<TemplateId> invalidateWithDependents(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    globalMacroManager.invalidate(id);
    return dependencyCoordinator.invalidateWithDependents(id, cache);
  }

  @Override
  public TemplateRepository repository() {
    return repository;
  }

  @Override
  public boolean rejectRuntimeCompilation() {
    return rejectRuntimeCompilation;
  }

  TemplateCompileCache cache() {
    return cache;
  }

  @Override
  public void invalidate(TemplateId id) {
    globalMacroManager.invalidate(id);
    cache.invalidate(id);
  }

  @Override
  public void invalidateAll() {
    globalMacroManager.invalidateAll();
    cache.invalidateAll();
  }

  @Override
  public void close() {
    fileWatcher.ifPresent(DevelopmentFileWatcher::close);
    invalidateAll();
    callSiteRegistry.clear();
    aotRegistry.close();
  }

  CallSiteRegistry callSiteRegistry() {
    return callSiteRegistry;
  }

  Optional<DevelopmentFileWatcher> fileWatcher() {
    return fileWatcher;
  }

  EngineFingerprint engineFingerprint() {
    return engineFingerprint;
  }

  AotTemplateRegistry aotRegistry() {
    return aotRegistry;
  }

  TemplateCompilationCoordinator compilationCoordinator() {
    return compilationCoordinator;
  }

  TemplateDependencyCoordinator dependencyCoordinator() {
    return dependencyCoordinator;
  }

  GlobalMacroManager globalMacroManager() {
    return globalMacroManager;
  }
}
