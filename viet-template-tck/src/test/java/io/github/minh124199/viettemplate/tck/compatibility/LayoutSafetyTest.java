package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutRenderPlan;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLayoutException;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionLimits;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LayoutSafetyTest {

  @Test
  void detectsLayoutCycleAndFailsWithClearDiagnostic() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("cycle.vm", "#set($layout = 'cycle.vm')$screen_content");
    repo.put("screen.vm", "#set($layout = 'cycle.vm')Content");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.fromContextVariable("layout", TemplateId.of("cycle.vm")))
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    StringTemplateOutput out = new StringTemplateOutput();

    // When cycle.vm resolves to itself inside layout rendering, the engine plan detects recursion
    LayoutRenderPlan plan =
        engine.prepareLayoutPlan(TemplateId.of("screen.vm"), RenderContext.empty());

    // Calling render directly with screenId = cycle.vm wrapped in layout cycle.vm
    LayoutRenderPlan cyclicPlan =
        engine.prepareLayoutPlan(TemplateId.of("cycle.vm"), RenderContext.empty());

    assertThatThrownBy(() -> cyclicPlan.render(RenderContext.empty(), out))
        .isInstanceOf(TemplateLayoutException.class)
        .satisfies(
            ex -> {
              TemplateLayoutException tle = (TemplateLayoutException) ex;
              assertThat(tle.code()).contains(DiagnosticCode.of("LAYOUT", "CYCLE_DETECTED"));
            });

    engine.close();
  }

  @Test
  void enforcesMaximumLayoutDepthLimit() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("l1.vm", "L1:$screen_content");
    repo.put("screen.vm", "Screen");

    // Resolver that always requests layout l1.vm
    LayoutResolver recursiveResolver = (screen, ctx) -> Optional.of(TemplateId.of("l1.vm"));

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder().resolver(recursiveResolver).maxLayoutDepth(2).build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    StringTemplateOutput out = new StringTemplateOutput();
    // A layout plan with maxDepth 2
    LayoutRenderPlan plan = engine.prepareLayoutPlan(TemplateId.of("l1.vm"), RenderContext.empty());

    assertThatThrownBy(() -> plan.render(RenderContext.empty(), out))
        .isInstanceOf(TemplateLayoutException.class)
        .satisfies(
            ex -> {
              TemplateLayoutException tle = (TemplateLayoutException) ex;
              assertThat(tle.code()).contains(DiagnosticCode.of("LAYOUT", "CYCLE_DETECTED"));
            });

    engine.close();
  }

  @Test
  void enforcesCharacterLimitDuringScreenCapture() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("layout.vm", "<html>$screen_content</html>");
    repo.put("huge-screen.vm", "#foreach($i in [1..1000])ABCDEFGHIJ#end");

    VtlInterpreterOptions limitsOptions =
        VtlInterpreterOptions.builder()
            .limits(ExecutionLimits.builder().maxOutputCharacters(100L).build())
            .build();

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("layout.vm")))
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .interpreterOptions(limitsOptions)
            .layoutConfiguration(layoutConfig)
            .build();

    StringTemplateOutput out = new StringTemplateOutput();

    assertThatThrownBy(
            () -> engine.render(TemplateId.of("huge-screen.vm"), RenderContext.empty(), out))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Screen output exceeded maximum character limit");

    engine.close();
  }
}
