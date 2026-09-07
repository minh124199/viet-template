package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.util.BitSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IrInterpreterTest {

  @Test
  void rendersDirectIrTemplate() throws IOException {
    String sourceStr = "Hello $name, count is $count!";
    SourceText source = SourceText.of("direct.vm", sourceStr);
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlSemanticOptions semOptions =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).strictMode(false).build();
    var analysis = VtlSemanticAnalyzer.analyze(ast, semOptions);
    IrTemplate ir = AstToIrLowerer.lower(ast, source, analysis, semOptions, new BitSet());

    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build());
    StringTemplateOutput output = new StringTemplateOutput();
    interpreter.render(
        ir, source, MapRenderContext.of(Map.of("name", "World", "count", 42)), output);

    assertThat(output.toString()).isEqualTo("Hello World, count is 42!");
  }

  @Test
  void enforcesExecutionTierInInterpreterOptions() {
    VtlInterpreterOptions irOptions =
        VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build();
    assertThat(irOptions.executionTier()).isEqualTo(ExecutionTier.IR);

    VtlInterpreterOptions astOptions =
        VtlInterpreterOptions.builder().executionTier(ExecutionTier.AST).build();
    assertThat(astOptions.executionTier()).isEqualTo(ExecutionTier.AST);
  }

  @Test
  void enforcesExecutionLimitsInIrTier() {
    VtlInterpreterOptions limitedOptions =
        VtlInterpreterOptions.builder()
            .executionTier(ExecutionTier.IR)
            .limits(ExecutionLimits.builder().maxLoopIterations(3).build())
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(limitedOptions);

    String template = "#foreach($i in [1..10])$i#end";
    SourceText source = SourceText.of("limits.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    assertThatThrownBy(
            () ->
                interpreter.interpret(
                    ast, source, MapRenderContext.of(Map.of()), new StringTemplateOutput()))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Exceeded maximum foreach iterations: 3");
  }

  @Test
  void verifiesOutputLimitsWithCountingOutput() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder()
            .executionTier(ExecutionTier.IR)
            .limits(ExecutionLimits.builder().maxOutputCharacters(10).build())
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    String template = "This is a very long text that exceeds limit";
    SourceText source = SourceText.of("out_limit.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    assertThatThrownBy(
            () ->
                interpreter.interpret(
                    ast, source, MapRenderContext.of(Map.of()), new StringTemplateOutput()))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Exceeded maximum rendered output characters limit");
  }

  @Test
  void streamsUtf8BytesAndPrimitivesDirectly() throws IOException {
    String template = "Hello constant text! $count $flag";
    SourceText source = SourceText.of("stream.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build());
    RecordingTemplateOutput out = new RecordingTemplateOutput();
    interpreter.interpret(
        ast, source, MapRenderContext.of(Map.of("count", 123, "flag", true)), out);

    assertThat(out.text.toString()).isEqualTo("Hello constant text! 123 true");
    assertThat(out.utf8CallCount).isGreaterThanOrEqualTo(1);
    assertThat(out.intCallCount).isGreaterThanOrEqualTo(1);
    assertThat(out.booleanCallCount).isGreaterThanOrEqualTo(1);
  }

  @Test
  void evaluateDirectiveGatedBehindProfile() {
    String template = "#evaluate('dynamic text')";
    SourceText source = SourceText.of("eval.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreter interpreterCore =
        new VtlInterpreter(
            VtlInterpreterOptions.builder()
                .executionTier(ExecutionTier.IR)
                .profile(VtlProfile.VTL_CORE)
                .build());

    assertThatThrownBy(
            () ->
                interpreterCore.interpret(
                    ast, source, MapRenderContext.of(Map.of()), new StringTemplateOutput()))
        .isInstanceOf(io.github.minh124199.viettemplate.api.TemplateSecurityException.class)
        .hasMessageContaining("#evaluate is disabled in profile VTL_CORE");
  }

  @Test
  void evaluateDirectiveAllowedUnderVtlDynamic() throws IOException {
    String template = "#evaluate('$greeting from dynamic')";
    SourceText source = SourceText.of("eval_dyn.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreter interpreterDyn =
        new VtlInterpreter(
            VtlInterpreterOptions.builder()
                .executionTier(ExecutionTier.IR)
                .profile(VtlProfile.VTL_DYNAMIC)
                .build());

    StringTemplateOutput out = new StringTemplateOutput();
    interpreterDyn.interpret(ast, source, MapRenderContext.of(Map.of("greeting", "Hello")), out);

    assertThat(out.toString()).isEqualTo("Hello from dynamic");
  }

  @Test
  void sourcePositionReportedOnException() {
    String template = "Line 1\n#set($x = 10 / 0)\nLine 3";
    SourceText source = SourceText.of("divzero.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build());

    assertThatThrownBy(
            () ->
                interpreter.interpret(
                    ast, source, MapRenderContext.of(Map.of()), new StringTemplateOutput()))
        .isInstanceOf(io.github.minh124199.viettemplate.api.TemplateRenderException.class)
        .satisfies(
            e -> {
              var span = ((io.github.minh124199.viettemplate.api.TemplateRenderException) e).span();
              assertThat(span.startLine()).isEqualTo(2);
            });
  }

  @Test
  void rendersAlternateValueCorrectly() throws IOException {
    String template = "[${val|'default'}] [${emptyVal|'empty-fallback'}] [${presentVal|'actual'}]";
    SourceText source = SourceText.of("alt.vm", template);
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build());
    StringTemplateOutput out = new StringTemplateOutput();
    interpreter.interpret(
        ast, source, MapRenderContext.of(Map.of("emptyVal", "", "presentVal", "actual")), out);

    assertThat(out.toString()).isEqualTo("[default] [empty-fallback] [actual]");
  }

  static class RecordingTemplateOutput
      implements io.github.minh124199.viettemplate.api.TemplateOutput {
    final StringBuilder text = new StringBuilder();
    int utf8CallCount = 0;
    int intCallCount = 0;
    int booleanCallCount = 0;

    @Override
    public void write(CharSequence value) {
      if (value != null) text.append(value);
    }

    @Override
    public void write(char value) {
      text.append(value);
    }

    @Override
    public void writeUtf8(byte[] bytes) {
      utf8CallCount++;
      text.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void writeInt(int value) {
      intCallCount++;
      text.append(value);
    }

    @Override
    public void writeLong(long value) {
      text.append(value);
    }

    @Override
    public void writeDouble(double value) {
      text.append(value);
    }

    @Override
    public void writeBoolean(boolean value) {
      booleanCallCount++;
      text.append(value);
    }
  }
}
