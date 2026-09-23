package io.github.minh124199.viettemplate.vtl.engine;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateCompilationException;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSyntaxException;
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
import io.github.minh124199.viettemplate.vtl.engine.cache.CompileCacheKey;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.engine.cache.TemplateCompileCache;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.CompilationStatus;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.internal.engine.macro.GlobalMacroManager;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.SpaceGobbler;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.BitSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Encapsulates template compilation (parsing, AST-to-IR lowering, optimization, bytecode
 * compilation, prepared IR layout, and fallback handling), producing a {@link
 * CompiledTemplateHandle}.
 */
final class TemplateCompilationCoordinator {

  private final OptimizationLevel optimizationLevel;
  private final IrOptimizationOptions optimizationOptions;
  private final ExecutionTier executionTier;
  private final VtlInterpreterOptions interpreterOptions;
  private final VtlSemanticOptions semanticOptions;
  private final GlobalMacroManager globalMacroManager;
  private final TemplateDependencyCoordinator dependencyCoordinator;
  private final VtlInterpreter interpreter;
  private final LayoutConfiguration layoutConfiguration;
  private final TemplateCompileCache cache;
  private final Consumer<TemplateId> precompileAction;

  TemplateCompilationCoordinator(
      OptimizationLevel optimizationLevel,
      IrOptimizationOptions optimizationOptions,
      ExecutionTier executionTier,
      VtlInterpreterOptions interpreterOptions,
      VtlSemanticOptions semanticOptions,
      GlobalMacroManager globalMacroManager,
      TemplateDependencyCoordinator dependencyCoordinator,
      VtlInterpreter interpreter,
      LayoutConfiguration layoutConfiguration,
      TemplateCompileCache cache,
      Consumer<TemplateId> precompileAction) {
    this.optimizationLevel =
        Objects.requireNonNull(optimizationLevel, "optimizationLevel must not be null");
    this.optimizationOptions =
        Objects.requireNonNull(optimizationOptions, "optimizationOptions must not be null");
    this.executionTier = Objects.requireNonNull(executionTier, "executionTier must not be null");
    this.interpreterOptions =
        Objects.requireNonNull(interpreterOptions, "interpreterOptions must not be null");
    this.semanticOptions =
        Objects.requireNonNull(semanticOptions, "semanticOptions must not be null");
    this.globalMacroManager =
        Objects.requireNonNull(globalMacroManager, "globalMacroManager must not be null");
    this.dependencyCoordinator =
        Objects.requireNonNull(dependencyCoordinator, "dependencyCoordinator must not be null");
    this.interpreter = Objects.requireNonNull(interpreter, "interpreter must not be null");
    this.layoutConfiguration =
        Objects.requireNonNull(layoutConfiguration, "layoutConfiguration must not be null");
    this.cache = cache;
    this.precompileAction = precompileAction;
  }

  TemplateCompilationCoordinator(
      OptimizationLevel optimizationLevel,
      IrOptimizationOptions optimizationOptions,
      ExecutionTier executionTier,
      VtlInterpreterOptions interpreterOptions,
      VtlSemanticOptions semanticOptions,
      GlobalMacroManager globalMacroManager,
      TemplateDependencyCoordinator dependencyCoordinator,
      VtlInterpreter interpreter,
      LayoutConfiguration layoutConfiguration) {
    this(
        optimizationLevel,
        optimizationOptions,
        executionTier,
        interpreterOptions,
        semanticOptions,
        globalMacroManager,
        dependencyCoordinator,
        interpreter,
        layoutConfiguration,
        null,
        null);
  }

  CompiledTemplateHandle compileTemplate(
      TemplateId id, CompileCacheKey key, SourceText sourceText, long generation) {
    return compileTemplate(
        id,
        key,
        sourceText,
        generation,
        this.precompileAction != null ? this.precompileAction : targetId -> {});
  }

  CompiledTemplateHandle compileTemplate(
      TemplateId id,
      CompileCacheKey key,
      SourceText sourceText,
      long generation,
      Consumer<TemplateId> precompileAction) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(sourceText, "sourceText must not be null");

    VtlParseResult parseResult = VtlParser.parse(sourceText);
    if (parseResult.hasErrors()) {
      if (cache != null) {
        cache.recordNegative(id, "Syntax errors: " + parseResult.diagnostics());
      }
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
    Optional<TemplateId> layoutId =
        layoutConfiguration.resolver().resolveLayout(id, RenderContext.empty());
    dependencyCoordinator.recordAndPrecompileDependencies(
        id,
        optimizedIr,
        globalMacroManager.libraryIds(),
        layoutId,
        precompileAction != null ? precompileAction : (targetId -> {}));

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
        return CompiledTemplateHandle.ofBytecode(id, generation, key, ct, optimizedIr, cl);
      } else if (result.status() == CompilationStatus.INTERPRETER_REQUIRED_EVALUATE) {
        IrTemplate finalIr = IrOptimizer.optimize(optimizedIr, optimizationOptions);
        return preparedIrHandle(id, generation, key, sourceText, finalIr);
      } else {
        if (cache != null) {
          cache.recordNegative(id, "Compilation failure: " + result.diagnostics());
        }
        throw new TemplateCompilationException(
            "AOT compilation failed: " + result.diagnostics(),
            id,
            SourceSpan.UNKNOWN,
            DiagnosticCode.of("COMPILER", "CODEGEN_ERROR"));
      }
    }

    // Global composition changes the final function set after the preliminary local-template
    // optimization. Finalize and verify that immutable generation before preparing IR execution.
    IrTemplate finalIr = IrOptimizer.optimize(optimizedIr, optimizationOptions);
    return preparedIrHandle(id, generation, key, sourceText, finalIr);
  }

  CompiledTemplateHandle preparedIrHandle(
      TemplateId id,
      long generation,
      CompileCacheKey key,
      SourceText sourceText,
      IrTemplate optimizedIr) {
    CompiledTemplate preparedTemplate =
        EngineInterpreterBridge.prepareIr(interpreter, optimizedIr, sourceText);
    return CompiledTemplateHandle.ofPreparedIr(id, generation, key, preparedTemplate, optimizedIr);
  }

  OptimizationLevel optimizationLevel() {
    return optimizationLevel;
  }

  IrOptimizationOptions optimizationOptions() {
    return optimizationOptions;
  }

  ExecutionTier executionTier() {
    return executionTier;
  }

  VtlInterpreterOptions interpreterOptions() {
    return interpreterOptions;
  }

  VtlSemanticOptions semanticOptions() {
    return semanticOptions;
  }

  GlobalMacroManager globalMacroManager() {
    return globalMacroManager;
  }

  TemplateDependencyCoordinator dependencyCoordinator() {
    return dependencyCoordinator;
  }

  VtlInterpreter interpreter() {
    return interpreter;
  }

  LayoutConfiguration layoutConfiguration() {
    return layoutConfiguration;
  }
}
