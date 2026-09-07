package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.LayoutConfiguration;
import io.github.minh124199.viettemplate.api.LayoutResolver;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class LayoutRenderingTest {

  @Test
  void rendersScreenInsideDefaultLayoutTemplate() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("default-layout.vm", "<html><body>$screen_content</body></html>");
    repo.put("hello.vm", "<h1>Hello $name</h1>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("default-layout.vm")))
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    RenderContext model = RenderContext.builder().put("name", "World").build();
    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("hello.vm"), model, out);

    assertThat(out.toString()).isEqualTo("<html><body><h1>Hello World</h1></body></html>");
    engine.close();
  }

  @Test
  void screenTemplateOverridesDefaultLayoutViaContextVariable() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("default.vm", "<default>$screen_content</default>");
    repo.put("admin.vm", "<admin>$screen_content</admin>");
    repo.put("screen.vm", "#set($layout = 'admin.vm')<h2>Admin Panel</h2>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.fromContextVariable("layout", TemplateId.of("default.vm")))
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("screen.vm"), RenderContext.empty(), out);

    assertThat(out.toString()).isEqualTo("<admin><h2>Admin Panel</h2></admin>");
    engine.close();
  }

  @Test
  void screenTemplateBypassesLayoutWhenSetToNone() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("default.vm", "<wrapper>$screen_content</wrapper>");
    repo.put("raw.vm", "#set($layout = 'none')<raw>Direct Output Only</raw>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.fromContextVariable("layout", TemplateId.of("default.vm")))
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("raw.vm"), RenderContext.empty(), out);

    assertThat(out.toString()).isEqualTo("<raw>Direct Output Only</raw>");
    engine.close();
  }

  @Test
  void supportsCustomScreenContentKey() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("layout.vm", "<main>$bodyPlaceholder</main>");
    repo.put("view.vm", "<p>Paragraph Content</p>");

    LayoutConfiguration layoutConfig =
        LayoutConfiguration.builder()
            .resolver(LayoutResolver.constant(TemplateId.of("layout.vm")))
            .screenContentKey("bodyPlaceholder")
            .build();

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).layoutConfiguration(layoutConfig).build();

    StringTemplateOutput out = new StringTemplateOutput();
    engine.render(TemplateId.of("view.vm"), RenderContext.empty(), out);

    assertThat(out.toString()).isEqualTo("<main><p>Paragraph Content</p></main>");
    engine.close();
  }
}
