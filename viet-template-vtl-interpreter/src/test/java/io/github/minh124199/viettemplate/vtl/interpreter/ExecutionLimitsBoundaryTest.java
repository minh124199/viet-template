package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionLimitsBoundaryTest extends AbstractInterpreterTest {

  @Test
  @DisplayName("P8: maxLoopIterations enforces exact N vs N+1 boundary")
  void loopIterationsExactBoundary() {
    int limit = 5;
    ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(limit).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    // Exact N (5) iterations: must succeed
    String templateN = "#foreach($i in [1..5])$i#end";
    String outputN = render(templateN, Map.of(), interpreter);
    assertThat(outputN).isEqualTo("12345");

    // N + 1 (6) iterations: must throw TemplateLimitException
    String templateNPlus1 = "#foreach($i in [1..6])$i#end";
    assertThatThrownBy(() -> render(templateNPlus1, Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maximum foreach iterations");
  }

  @Test
  @DisplayName("P8: maxOutputCharacters enforces exact N vs N+1 boundary")
  void outputCharactersExactBoundary() {
    long limit = 10;
    ExecutionLimits limits = ExecutionLimits.builder().maxOutputCharacters(limit).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    // Exactly 10 characters
    String output10 = render("1234567890", Map.of(), interpreter);
    assertThat(output10).isEqualTo("1234567890");

    // 11 characters
    assertThatThrownBy(() -> render("12345678901", Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("rendered output characters limit");
  }

  @Test
  @DisplayName("P8: maxRangeSize enforces exact N vs N+1 boundary")
  void rangeSizeExactBoundary() {
    int limit = 5;
    ExecutionLimits limits = ExecutionLimits.builder().maxRangeSize(limit).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    // Range size 5: [1..5]
    String outputN = render("#foreach($i in [1..5])$i#end", Map.of(), interpreter);
    assertThat(outputN).isEqualTo("12345");

    // Range size 6: [1..6]
    assertThatThrownBy(() -> render("#foreach($i in [1..6])$i#end", Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Range size exceeds maximum limit");
  }

  @Test
  @DisplayName("P8: maxMacroDepth enforces exact recursion boundary")
  void macroDepthExactBoundary() {
    int limit = 3;
    ExecutionLimits limits = ExecutionLimits.builder().maxMacroDepth(limit).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    // Depth 3 recursion: #rec(3) -> calls rec(2) -> calls rec(1)
    String templateRec3 = "#macro(rec $d)#if($d > 1)[$d#rec($d - 1)]#{else}[1]#end#end#rec(3)";
    String output3 = render(templateRec3, Map.of(), interpreter);
    assertThat(output3).isEqualTo("[3[2[1]]]");

    // Depth 4 recursion: #rec(4) -> calls rec(3) -> calls rec(2) -> calls rec(1) (depth 4)
    String templateRec4 = "#macro(rec $d)#if($d > 1)[$d#rec($d - 1)]#{else}[1]#end#end#rec(4)";
    assertThatThrownBy(() -> render(templateRec4, Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maximum macro recursion depth");
  }

  @Test
  @DisplayName("P8: maxParseDepth enforces exact #parse chain boundary")
  void parseDepthExactBoundary() {
    int limit = 2;
    Map<String, String> templates = new HashMap<>();
    templates.put("top.vtl", "#parse('mid.vtl')");
    templates.put("mid.vtl", "#parse('leaf.vtl')");
    templates.put("leaf.vtl", "leafContent");

    TemplateResourceResolver resolver =
        (curr, path) ->
            Optional.ofNullable(templates.get(path))
                .map(content -> new TemplateResource(TemplateId.of(path), content));

    ExecutionLimits limits = ExecutionLimits.builder().maxParseDepth(limit).build();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().limits(limits).resourceResolver(resolver).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    // Chain: top -> mid -> leaf (depth 2): should succeed
    String output = render("#parse('mid.vtl')", Map.of(), interpreter);
    assertThat(output.trim()).isEqualTo("leafContent");

    // Chain: top -> mid -> leaf (depth 3 from top): should fail
    assertThatThrownBy(() -> render("#parse('top.vtl')", Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maxParseDepth");
  }

  @Test
  @DisplayName("P8: maxEvaluateDepth enforces exact #evaluate chain boundary")
  void evaluateDepthExactBoundary() {
    int limit = 2;
    ExecutionLimits limits = ExecutionLimits.builder().maxEvaluateDepth(limit).build();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).limits(limits).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    // 2 nested evaluates: #evaluate('#evaluate("done")')
    String template2 = "#evaluate('#evaluate(\"done\")')";
    String output2 = render(template2, Map.of(), interpreter);
    assertThat(output2.trim()).isEqualTo("done");

    // 3 nested evaluates: #evaluate('#evaluate("#evaluate(\"fail\")")')
    String template3 = "#evaluate('#evaluate(\"#evaluate(\\\"fail\\\")\")')";
    assertThatThrownBy(() -> render(template3, Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maximum #evaluate depth");
  }

  @Test
  @DisplayName("Counter boundary and saturating arithmetic in RenderBudget")
  void renderBudgetCounterOverflowSafety() {
    // 1. Rejects negative limits
    assertThatThrownBy(() -> new RenderBudget(-1, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RenderBudget(10, -1, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RenderBudget(10, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);

    // 2. Large additions saturate at Long.MAX_VALUE without arithmetic wrap-around
    RenderBudget budget = new RenderBudget(100, 0, 100);
    TemplateId dummyId = TemplateId.of("test.vtl");
    SourceSpan dummySpan = SourceSpan.UNKNOWN;

    // Consume large count to trigger limit
    assertThatThrownBy(() -> budget.consumeCharacters(Integer.MAX_VALUE, dummyId, dummySpan))
        .isInstanceOf(TemplateLimitException.class);

    // Iterations saturating at limit
    RenderBudget loopBudget = new RenderBudget(1000, 0, 2);
    loopBudget.countLoopIteration(dummyId, dummySpan);
    loopBudget.countLoopIteration(dummyId, dummySpan);
    assertThatThrownBy(() -> loopBudget.countLoopIteration(dummyId, dummySpan))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maximum foreach iterations");
  }
}
