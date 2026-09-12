package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationLevel;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.vtl.compiler.*;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.engine.context.ContributingContextComposer;
import io.github.minh124199.viettemplate.vtl.engine.dependency.DefaultTemplateDependencyGraph;
import io.github.minh124199.viettemplate.vtl.engine.dependency.StaticDependencyExtractor;
import io.github.minh124199.viettemplate.vtl.engine.layout.DefaultLayoutRenderPlan;
import io.github.minh124199.viettemplate.vtl.engine.macro.GlobalMacroManager;
import io.github.minh124199.viettemplate.vtl.engine.watcher.DevelopmentFileWatcher;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.SpaceGobbler;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResource;
import io.github.minh124199.viettemplate.vtl.interpreter.TemplateResourceResolver;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
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
  private final Optional<DevelopmentFileWatcher> fileWatcher;

  private final TemplateDependencyGraph dependencyGraph;
  private final GlobalMacroManager globalMacroManager;
  private final List<RenderContextContributor> contextContributors;
  private final ContextCollisionPolicy contextCollisionPolicy;
  private final LayoutConfiguration layoutConfiguration;
  private final ThreadLocal<Set<TemplateId>> compilingTemplates =
      ThreadLocal.withInitial(java.util.HashSet::new);

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

    this.dependencyGraph =
        dependencyGraph != null ? dependencyGraph : new DefaultTemplateDependencyGraph();
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
  }

  public static VtlTemplateEngineBuilder builder() {
    return new VtlTemplateEngineBuilder();
  }

  @Override
  public Template get(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");

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
    String accessPolicyId = interpreterOptions.securityPolicy().policyFingerprint();
    String modelSignature = semanticOptions.modelSchema().parameters().toString();
    String backendHash =
        interpreterOptions.profile().name() + ":" + semanticOptions.profile().name();
    String macroFingerprint = globalMacroManager.computeFingerprint();

    CompileCacheKey key =
        CompileCacheKey.of(
            id,
            source.fingerprint(),
            COMPILER_VERSION,
            optimizationLevel,
            executionTier,
            accessPolicyId,
            modelSignature,
            backendHash,
            macroFingerprint);

    // 4. Cache hit check
    Optional<CompiledTemplateHandle> cachedHandle = cache.get(key);
    SourceText sourceText = SourceText.of(id, source.content());

    if (cachedHandle.isPresent()) {
      return new VtlTemplate(
          TemplateDescriptor.of(id, executionTier.name()),
          cachedHandle.get(),
          sourceText,
          interpreterOptions);
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
    CompiledTemplateHandle compiledHandle = compileTemplate(id, key, sourceText);
    cache.put(key, compiledHandle);

    return new VtlTemplate(
        TemplateDescriptor.of(id, executionTier.name()),
        compiledHandle,
        sourceText,
        interpreterOptions);
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

    io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput countingOutput =
        (output
                instanceof
                io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput cto)
            ? cto
            : new io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput(
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
    return dependencyGraph;
  }

  @Override
  public Set<TemplateId> invalidateWithDependents(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");
    globalMacroManager.invalidate(id);
    return cache.invalidateWithDependents(id, dependencyGraph);
  }

  @Override
  public TemplateRepository repository() {
    return repository;
  }

  @Override
  public boolean rejectRuntimeCompilation() {
    return rejectRuntimeCompilation;
  }

  public TemplateCompileCache cache() {
    return cache;
  }

  public void invalidate(TemplateId id) {
    globalMacroManager.invalidate(id);
    cache.invalidate(id);
  }

  public void invalidateAll() {
    globalMacroManager.invalidateAll();
    cache.invalidateAll();
  }

  @Override
  public void close() {
    fileWatcher.ifPresent(DevelopmentFileWatcher::close);
    invalidateAll();
  }

  private CompiledTemplateHandle compileTemplate(
      TemplateId id, CompileCacheKey key, SourceText sourceText) {
    VtlParseResult parseResult = VtlParser.parse(sourceText);
    if (parseResult.hasErrors()) {
      cache.recordNegative(id, "Syntax errors: " + parseResult.diagnostics());
      throw new TemplateSyntaxException(
          "Syntax error while compiling template " + id.value(),
          id,
          parseResult.template().span(),
          DiagnosticCode.of("SYNTAX", "PARSE_ERROR"));
    }

    BitSet gobbled =
        SpaceGobbler.computeGobbledIndices(sourceText, interpreterOptions.spaceGobbling());
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate irTemplate =
        AstToIrLowerer.lower(
            parseResult.template(), sourceText, analysis, semanticOptions, gobbled);
    IrTemplate optimizedIr = IrOptimizer.optimize(irTemplate, optimizationOptions);

    // Merge global macros into template compilation
    optimizedIr = globalMacroManager.mergeWithTemplate(optimizedIr);

    // Extract static dependencies and update dependency graph
    Set<TemplateDependency> deps =
        StaticDependencyExtractor.extract(
            optimizedIr,
            globalMacroManager.libraryIds(),
            layoutConfiguration.resolver().resolveLayout(id, RenderContext.empty()));
    dependencyGraph.replaceDependencies(id, deps);

    Set<TemplateId> compiling = compilingTemplates.get();
    if (compiling.add(id)) {
      try {
        for (TemplateDependency dep : deps) {
          if ((dep.kind() == TemplateDependencyKind.STATIC_PARSE
                  || dep.kind() == TemplateDependencyKind.STATIC_INCLUDE)
              && !compiling.contains(dep.target())) {
            try {
              get(dep.target());
            } catch (Exception ignored) {
            }
          }
        }
      } finally {
        compiling.remove(id);
      }
    }

    long gen = cache.nextGeneration();

    if (executionTier == ExecutionTier.AOT_BYTECODE) {
      BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
      BackendOptions backendOptions =
          BackendOptions.builder()
              .securityPolicy(interpreterOptions.securityPolicy().toLinkerAccessPolicy())
              .optimizationOptions(optimizationOptions)
              .setNullAllowed(interpreterOptions.setNullAllowed())
              .build();
      BackendResult result = compiler.compile(optimizedIr, backendOptions);

      if (result.isSuccess() && result.compiledTemplate().isPresent()) {
        CompiledTemplate ct = result.compiledTemplate().get();
        TemplateClassLoader cl =
            (ct.getClass().getClassLoader() instanceof TemplateClassLoader tcl) ? tcl : null;
        return CompiledTemplateHandle.ofBytecode(id, gen, key, ct, optimizedIr, cl);
      } else if (result.status() == CompilationStatus.INTERPRETER_REQUIRED_EVALUATE) {
        return CompiledTemplateHandle.ofIr(id, gen, key, optimizedIr);
      } else {
        cache.recordNegative(id, "Compilation failure: " + result.diagnostics());
        throw new TemplateCompilationException(
            "AOT compilation failed: " + result.diagnostics(),
            id,
            SourceSpan.UNKNOWN,
            DiagnosticCode.of("COMPILER", "CODEGEN_ERROR"));
      }
    }

    return CompiledTemplateHandle.ofIr(id, gen, key, optimizedIr);
  }
}
