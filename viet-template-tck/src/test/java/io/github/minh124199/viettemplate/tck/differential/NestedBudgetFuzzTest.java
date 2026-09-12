package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionLimits;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NestedBudgetFuzzTest {

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("P9: Nested #parse cannot reset monotonic loop iteration budget across tiers")
  void nestedParseCannotResetLoopBudget(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    // Parent runs 4 iterations, child runs 4 iterations per parent iteration = 16 total iterations
    repo.put("parent.vtl", "#foreach($p in [1..4])#parse('child.vtl')#end");
    repo.put("child.vtl", "#foreach($c in [1..4])C#end");

    // Budget limit is 10 iterations (< 16). If child resets budget, it would erroneously succeed!
    ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(10).build();
    VtlInterpreterOptions opts =
        VtlInterpreterOptions.builder().limits(limits).executionTier(tier).build();

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
      StringTemplateOutput out = new StringTemplateOutput();
      assertThatThrownBy(
              () -> engine.render(TemplateId.of("parent.vtl"), RenderContext.empty(), out))
          .isInstanceOf(TemplateLimitException.class)
          .hasMessageContaining("maximum foreach iterations");
    }
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("P9: Nested macro calls cannot reset monotonic character output budget across tiers")
  void nestedMacroCannotResetCharacterBudget(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    // Macro emits 20 characters, called 3 times = 60 characters
    repo.put(
        "macro_test.vtl",
        "#macro(emitText)01234567890123456789#end#emitText()#emitText()#emitText()");

    // Budget limit is 50 characters (< 60)
    ExecutionLimits limits = ExecutionLimits.builder().maxOutputCharacters(50).build();
    VtlInterpreterOptions opts =
        VtlInterpreterOptions.builder().limits(limits).executionTier(tier).build();

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {
      StringTemplateOutput out = new StringTemplateOutput();
      assertThatThrownBy(
              () -> engine.render(TemplateId.of("macro_test.vtl"), RenderContext.empty(), out))
          .isInstanceOf(TemplateLimitException.class)
          .hasMessageContaining("rendered output characters limit");
    }
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("P9: Screen and Layout execution share a single monotonic RenderBudget across tiers")
  void screenAndLayoutShareMonotonicBudget(ExecutionTier tier) throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    // Screen runs 6 loop iterations, Layout runs 6 loop iterations = 12 total iterations
    repo.put("screen.vtl", "#foreach($s in [1..6])S#end");
    repo.put("layout.vtl", "Header:[#foreach($l in [1..6])L#end]:$screen_content");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.fromContextVariable("layout", TemplateId.of("layout.vtl")))
            .build();

    // Limit is 10 iterations (< 12 total)
    ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(10).build();
    VtlInterpreterOptions opts =
        VtlInterpreterOptions.builder().limits(limits).executionTier(tier).build();

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .layoutConfiguration(layoutConfig)
            .interpreterOptions(opts)
            .build()) {

      LayoutRenderPlan plan =
          engine.prepareLayoutPlan(TemplateId.of("screen.vtl"), RenderContext.empty());
      StringTemplateOutput out = new StringTemplateOutput();

      assertThatThrownBy(() -> plan.render(RenderContext.empty(), out))
          .isInstanceOf(TemplateLimitException.class)
          .hasMessageContaining("maximum foreach iterations");
    }
  }
}
