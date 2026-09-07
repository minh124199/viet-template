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
import io.github.minh124199.viettemplate.vtl.engine.watcher.DevelopmentFileWatcher;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.SpaceGobbler;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Objects;
import java.util.Optional;

/** Canonical reference implementation of {@link TemplateEngine}. */
public final class VtlTemplateEngine implements TemplateEngine, AutoCloseable {

  private static final String COMPILER_VERSION = "0.1.1-SNAPSHOT";

  private final TemplateRepository repository;
  private final TemplateCompileCache cache;
  private final boolean rejectRuntimeCompilation;
  private final ExecutionTier executionTier;
  private final OptimizationLevel optimizationLevel;
  private final IrOptimizationOptions optimizationOptions;
  private final VtlSemanticOptions semanticOptions;
  private final VtlInterpreterOptions interpreterOptions;
  private final Optional<DevelopmentFileWatcher> fileWatcher;

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
      long watchDebounceMillis) {
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
    this.interpreterOptions =
        Objects.requireNonNull(interpreterOptions, "interpreterOptions must not be null");

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
                        cache.invalidate(id);
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
    String accessPolicyId = interpreterOptions.securityPolicy().getClass().getName();
    String modelSignature = semanticOptions.modelSchema().parameters().toString();
    String backendHash = "v1";

    CompileCacheKey key =
        CompileCacheKey.of(
            id,
            source.fingerprint(),
            COMPILER_VERSION,
            optimizationLevel,
            executionTier,
            accessPolicyId,
            modelSignature,
            backendHash);

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
    cache.invalidate(id);
  }

  public void invalidateAll() {
    cache.invalidateAll();
  }

  @Override
  public void close() {
    fileWatcher.ifPresent(DevelopmentFileWatcher::close);
    cache.invalidateAll();
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

    long gen = cache.nextGeneration();

    if (executionTier == ExecutionTier.AOT_BYTECODE) {
      BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
      BackendResult result = compiler.compile(optimizedIr, BackendOptions.builder().build());

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
