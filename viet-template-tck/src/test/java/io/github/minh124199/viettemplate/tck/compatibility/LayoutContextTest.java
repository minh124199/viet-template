package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutContextScope;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class LayoutContextTest {

  @Test
  void sharedScopeAllowsScreenMutationsToPropagateToLayout() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "layout.vm",
        "<head><title>$pageTitle</title></head><body><nav>$activeTab</nav>$screen_content</body>");
    repo.put(
        "screen.vm",
        "#set($pageTitle = 'Overview')\n#set($activeTab = 'dashboard')\n<p>Dashboard Content</p>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("layout.vm")))
            .contextScope(LayoutContextScope.SHARED_COMPATIBILITY_SCOPE)
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("screen.vm"), RenderContext.empty(), out);

    assertThat(out.toString())
        .isEqualTo(
            "<head><title>Overview</title></head><body><nav>dashboard</nav><p>Dashboard"
                + " Content</p></body>");
    engine.close();
  }

  @Test
  void isolatedScopePreventsScreenMutationsFromPollutingLayout() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(
        "layout.vm",
        "<layout>title=[$!pageTitle] secret=[$!internalVal] body=[$screen_content]</layout>");
    repo.put(
        "screen.vm",
        "#set($pageTitle = 'ScreenTitle')\n"
            + "#set($internalVal = 'ScreenPrivate')\n"
            + "<span>Screen</span>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("layout.vm")))
            .contextScope(LayoutContextScope.ISOLATED_SCREEN_SCOPE)
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    RenderContext model = RenderContext.builder().put("pageTitle", "ModelTitle").build();
    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("screen.vm"), model, out);

    // Layout should preserve model title and should NOT have received internalVal
    assertThat(out.toString())
        .isEqualTo("<layout>title=[ModelTitle] secret=[] body=[<span>Screen</span>]</layout>");
    engine.close();
  }
}
