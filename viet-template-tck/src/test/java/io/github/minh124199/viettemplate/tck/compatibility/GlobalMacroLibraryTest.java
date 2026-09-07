package io.github.minh124199.viettemplate.tck.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalMacroLibraryTest {

  @Test
  void globalMacrosAvailableAcrossMultipleTemplates() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("global-macros.vm", "#macro(badge $txt)<span class=\"badge\">$txt</span>#end");
    repo.put("page1.vm", "Page1: #badge('Active')");
    repo.put("page2.vm", "Page2: #badge('Pending')");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(TemplateId.of("global-macros.vm")))
            .build();

    Template t1 = engine.get("page1.vm");
    StringTemplateOutput out1 = new StringTemplateOutput();
    t1.render(RenderContext.empty(), out1);
    assertThat(out1.toString()).isEqualTo("Page1: <span class=\"badge\">Active</span>");

    Template t2 = engine.get("page2.vm");
    StringTemplateOutput out2 = new StringTemplateOutput();
    t2.render(RenderContext.empty(), out2);
    assertThat(out2.toString()).isEqualTo("Page2: <span class=\"badge\">Pending</span>");

    engine.close();
  }

  @Test
  void reloadingGlobalMacroLibraryUpdatesDependentTemplates() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("macros.vm", "#macro(btn $label)<button>$label</button>#end");
    repo.put("form.vm", "#btn('Submit')");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(TemplateId.of("macros.vm")))
            .build();

    Template form1 = engine.get("form.vm");
    StringTemplateOutput out1 = new StringTemplateOutput();
    form1.render(RenderContext.empty(), out1);
    assertThat(out1.toString()).isEqualTo("<button>Submit</button>");

    // Update global macro definition
    repo.put("macros.vm", "#macro(btn $label)<button class=\"primary\">$label</button>#end");
    engine.invalidateWithDependents(TemplateId.of("macros.vm"));

    Template form2 = engine.get("form.vm");
    StringTemplateOutput out2 = new StringTemplateOutput();
    form2.render(RenderContext.empty(), out2);
    assertThat(out2.toString()).isEqualTo("<button class=\"primary\">Submit</button>");

    engine.close();
  }
}
