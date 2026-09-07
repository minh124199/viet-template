package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeTemplateCompiler;
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
}
