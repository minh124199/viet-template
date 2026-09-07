package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.GlobalMacroPrecedence;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalMacroPrecedenceTest {

  @Test
  void enforcesLastWinsPrecedenceBetweenLibraries() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("lib1.vm", "#macro(renderItem $item)[1:$item]#end");
    repo.put("lib2.vm", "#macro(renderItem $item)[2:$item]#end");
    repo.put("client.vm", "#renderItem('widget')");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(TemplateId.of("lib1.vm"), TemplateId.of("lib2.vm")))
            .globalMacroPrecedence(GlobalMacroPrecedence.LAST_WINS)
            .build();

    Template t = engine.get("client.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    t.render(RenderContext.empty(), out);
    assertThat(out.toString()).isEqualTo("[2:widget]");

    engine.close();
  }

  @Test
  void enforcesFirstWinsPrecedenceBetweenLibraries() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("lib1.vm", "#macro(renderItem $item)[1:$item]#end");
    repo.put("lib2.vm", "#macro(renderItem $item)[2:$item]#end");
    repo.put("client.vm", "#renderItem('widget')");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(TemplateId.of("lib1.vm"), TemplateId.of("lib2.vm")))
            .globalMacroPrecedence(GlobalMacroPrecedence.FIRST_WINS)
            .build();

    Template t = engine.get("client.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    t.render(RenderContext.empty(), out);
    assertThat(out.toString()).isEqualTo("[1:widget]");

    engine.close();
  }

  @Test
  void localTemplateMacroShadowsGlobalMacroRegardlessOfPrecedence() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("global.vm", "#macro(renderItem $item)[global:$item]#end");
    repo.put("custom.vm", "#macro(renderItem $item)[local:$item]#end#renderItem('custom-item')");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(TemplateId.of("global.vm")))
            .build();

    Template t = engine.get("custom.vm");
    StringTemplateOutput out = new StringTemplateOutput();
    t.render(RenderContext.empty(), out);
    assertThat(out.toString()).isEqualTo("[local:custom-item]");

    engine.close();
  }
}
