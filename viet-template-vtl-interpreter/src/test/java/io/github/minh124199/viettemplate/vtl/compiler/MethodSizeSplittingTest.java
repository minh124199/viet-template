package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MethodSizeSplittingTest {

  @Test
  @DisplayName("Splits oversized method into chunk helper methods and executes properly")
  void testOversizedMethodSplitting() throws Exception {
    StringBuilder sb = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 60; i++) {
      sb.append("#if($name) item_").append(i).append(": $name #end\n");
      expected.append(" item_").append(i).append(": Antigravity \n");
    }

    SourceText source = SourceText.of("oversized.vtl", sb.toString());
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

    // Set a low threshold (20 statements) to trigger MethodSizePlanningPass chunk splitting
    BackendOptions options = BackendOptions.builder().methodSplitThreshold(20).build();

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, options);

    assertThat(result.isSuccess()).isTrue();
    Class<? extends CompiledTemplate> clazz = result.compiledClass();

    // Verify chunk helper methods were created on the compiled class
    List<Method> chunkMethods =
        Arrays.stream(clazz.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("renderChunk_"))
            .toList();

    assertThat(chunkMethods)
        .as("Compiler should generate chunk methods for oversized blocks")
        .isNotEmpty();

    // Execute the compiled template and verify that all statements produced correct output
    CompiledTemplate instance = result.compiledTemplate().orElseThrow();
    StringTemplateOutput out = new StringTemplateOutput();
    instance.render(MapRenderContext.of(Map.of("name", "Antigravity")), out);

    assertThat(out.toString()).isEqualTo(expected.toString());
  }

  @Test
  @DisplayName("Compiles very large template (>500 statements) with default split threshold")
  void testLargeTemplateDefaultThreshold() throws Exception {
    StringBuilder sb = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 600; i++) {
      sb.append("#if($val)line_").append(i).append("#end\n");
      expected.append("line_").append(i).append("\n");
    }

    SourceText source = SourceText.of("huge.vtl", sb.toString());
    var parseResult = VtlParser.parse(source);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendResult result = compiler.compile(ir, BackendOptions.defaults());

    assertThat(result.isSuccess()).isTrue();
    Class<? extends CompiledTemplate> clazz = result.compiledClass();

    // Verify chunk methods generated
    List<Method> chunkMethods =
        Arrays.stream(clazz.getDeclaredMethods())
            .filter(m -> m.getName().startsWith("renderChunk_"))
            .toList();

    assertThat(chunkMethods)
        .as("Method splitting should trigger for template > 500 statements")
        .isNotEmpty();

    CompiledTemplate instance = result.compiledTemplate().orElseThrow();
    StringTemplateOutput out = new StringTemplateOutput();
    instance.render(MapRenderContext.of(Map.of("val", true)), out);

    assertThat(out.toString()).isEqualTo(expected.toString());
  }

  @Test
  @DisplayName(
      "Compiles 100-statement template through VtlTemplateEngine with AOT_BYTECODE without"
          + " ClassFormatError")
  void testRepeatedOptimizationThroughEngineAndCompiler() throws Exception {
    StringBuilder sb = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      sb.append("#if($val)item_").append(i).append(": $val#end\n");
      expected.append("item_").append(i).append(": test\n");
    }

    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("repeat_opt.vm", sb.toString());

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build()) {
      Template template = engine.get("repeat_opt.vm");
      StringTemplateOutput out = new StringTemplateOutput();
      template.render(MapRenderContext.of(Map.of("val", "test")), out);
      assertThat(out.toString()).isEqualTo(expected.toString());
    }
  }

  @Test
  @DisplayName("Optimizer is idempotent when template is below method split threshold")
  void testOptimizerIdempotenceBelowThreshold() {
    String source = "#set($x = 1)\n#set($y = 2)\nResult: $x, $y\n";
    SourceText st = SourceText.of("small.vm", source);
    var parseResult = VtlParser.parse(st);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), st, analysis, semanticOptions);

    IrTemplate once = IrOptimizer.optimize(ir);
    IrTemplate twice = IrOptimizer.optimize(once);

    assertThat(once.functions()).isEmpty();
    assertThat(twice.functions()).isEmpty();
    assertThat(twice.root().statements().size()).isEqualTo(once.root().statements().size());
  }

  @Test
  @DisplayName("Optimizer is idempotent when template exceeds method split threshold")
  void testOptimizerIdempotenceAboveThreshold() throws Exception {
    StringBuilder sb = new StringBuilder();
    StringBuilder expected = new StringBuilder();
    for (int i = 0; i < 120; i++) {
      sb.append("#if($val)line_").append(i).append(": $val#end\n");
      expected.append("line_").append(i).append(": hello\n");
    }

    SourceText st = SourceText.of("large.vm", sb.toString());
    var parseResult = VtlParser.parse(st);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), st, analysis, semanticOptions);

    IrTemplate once = IrOptimizer.optimize(ir);
    IrTemplate twice = IrOptimizer.optimize(once);

    assertThat(once.functions()).isNotEmpty();
    assertThat(twice.functions()).hasSameSizeAs(once.functions());

    List<String> onceNames = once.functions().stream().map(IrFunction::name).toList();
    List<String> twiceNames = twice.functions().stream().map(IrFunction::name).toList();
    assertThat(twiceNames).isEqualTo(onceNames);
    assertThat(twiceNames).doesNotHaveDuplicates();

    assertThat(twice.root().statements().size()).isEqualTo(once.root().statements().size());

    // Both must compile cleanly to bytecode and render identical output
    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendOptions options = BackendOptions.defaultOptions();
    CompiledTemplate onceCompiled =
        compiler.compile(once, options).compiledTemplate().orElseThrow();
    CompiledTemplate twiceCompiled =
        compiler.compile(twice, options).compiledTemplate().orElseThrow();

    StringTemplateOutput outOnce = new StringTemplateOutput();
    onceCompiled.render(MapRenderContext.of(Map.of("val", "hello")), outOnce);

    StringTemplateOutput outTwice = new StringTemplateOutput();
    twiceCompiled.render(MapRenderContext.of(Map.of("val", "hello")), outTwice);

    assertThat(outOnce.toString()).isEqualTo(expected.toString());
    assertThat(outTwice.toString()).isEqualTo(expected.toString());
  }

  @Test
  @DisplayName("BytecodeTemplateCompiler fails fast when duplicate helper method names are passed")
  void testBytecodeCompilerFailsFastOnDuplicateMethod() {
    String source = "Hello $val";
    SourceText st = SourceText.of("test.vm", source);
    var parseResult = VtlParser.parse(st);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
    SemanticAnalysisResult analysis =
        VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
    IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), st, analysis, semanticOptions);

    IrFunction dup1 =
        new IrFunction(
            "__render_chunk_1", List.of(), List.of(), new IrBlock(List.of(), ir.span()), ir.span());
    IrFunction dup2 =
        new IrFunction(
            "__render_chunk_1", List.of(), List.of(), new IrBlock(List.of(), ir.span()), ir.span());

    IrTemplate invalidTemplate =
        new IrTemplate(
            ir.id(),
            ir.parameters(),
            ir.root(),
            ir.constants(),
            ir.capabilities(),
            List.of(dup1, dup2),
            ir.span());

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendOptions options = BackendOptions.defaultOptions();
    assertThatThrownBy(() -> compiler.compile(invalidTemplate, options))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate generated helper method name")
        .hasMessageContaining("detected for function '__render_chunk_1'");
  }
}
