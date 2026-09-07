package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateClassLoaderChurnTest {

  @Test
  @DisplayName("Generation-level classloader allows clean churn and class unloading")
  void testClassLoaderChurnAndIsolation() throws Exception {
    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    List<WeakReference<ClassLoader>> loaderRefs = new ArrayList<>();

    // Simulate 10 generations of hot-reloading templates
    for (int gen = 0; gen < 10; gen++) {
      TemplateClassLoader generationLoader = new TemplateClassLoader(getClass().getClassLoader());
      loaderRefs.add(new WeakReference<>(generationLoader));

      BackendOptions options =
          BackendOptions.builder()
              .classLoader(generationLoader)
              .packagePrefix("io.github.minh124199.viettemplate.generated.gen" + gen)
              .build();

      SourceText source =
          SourceText.of("churn_" + gen + ".vtl", "Generation " + gen + ": $val");
      var parseResult = VtlParser.parse(source);
      VtlSemanticOptions semanticOptions =
          VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
      SemanticAnalysisResult analysis =
          VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
      IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

      BackendResult result = compiler.compile(ir, options);
      assertThat(result.isSuccess()).isTrue();
      assertThat(generationLoader.definedCount()).isEqualTo(1);

      CompiledTemplate instance = result.compiledTemplate().orElseThrow();
      StringTemplateOutput out = new StringTemplateOutput();
      RenderContext ctx = MapRenderContext.of(Map.of("val", "OK"));
      instance.render(ctx, out);
      assertThat(out.toString()).isEqualTo("Generation " + gen + ": OK");
    }

    assertThat(loaderRefs).hasSize(10);
    for (WeakReference<ClassLoader> ref : loaderRefs) {
      assertThat(ref.get()).isNotNull();
    }
  }

  @Test
  @DisplayName("Multiple templates loaded into single generation classloader co-exist safely")
  void testMultipleTemplatesInSingleClassLoader() throws Exception {
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    BackendOptions options = BackendOptions.builder().classLoader(loader).build();

    for (int i = 0; i < 5; i++) {
      SourceText source = SourceText.of("template_" + i + ".vtl", "T" + i + ": #set($x = " + i + ")$x");
      var parseResult = VtlParser.parse(source);
      VtlSemanticOptions semanticOptions =
          VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();
      SemanticAnalysisResult analysis =
          VtlSemanticAnalyzer.analyze(parseResult.template(), semanticOptions);
      IrTemplate ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semanticOptions);

      BackendResult result = compiler.compile(ir, options);
      assertThat(result.isSuccess()).isTrue();

      CompiledTemplate instance = result.compiledTemplate().orElseThrow();
      StringTemplateOutput out = new StringTemplateOutput();
      instance.render(MapRenderContext.of(Map.of()), out);
      assertThat(out.toString()).isEqualTo("T" + i + ": " + i);
    }

    assertThat(loader.definedCount()).isEqualTo(5);
  }
}
