package io.github.minh124199.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AotMavenBasicTest {

  @Test
  @DisplayName("Loads and renders AOT precompiled templates without runtime compilation")
  void testRenderPrecompiledTemplates() throws Exception {
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(InMemoryTemplateRepository.create())
            .rejectRuntimeCompilation(true)
            .build();

    Template helloTemplate = engine.get(TemplateId.of("hello.vtl"));
    assertThat(helloTemplate).isNotNull();
    assertThat(helloTemplate.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");

    StringTemplateOutput out1 = new StringTemplateOutput();
    helloTemplate.render(
        MapRenderContext.of(Map.of("name", "World", "location", "Vietnam")), out1);
    assertThat(out1.toString().trim()).isEqualTo("Hello, World! Welcome to Vietnam.");

    Template cardTemplate = engine.get(TemplateId.of("user/card.vtl"));
    assertThat(cardTemplate).isNotNull();
    assertThat(cardTemplate.descriptor().executionTier()).isEqualTo("AOT_BYTECODE");

    StringTemplateOutput out2 = new StringTemplateOutput();
    cardTemplate.render(
        MapRenderContext.of(Map.of("active", true, "user", Map.of("name", "Alice"))), out2);
    assertThat(out2.toString().trim()).isEqualTo("User: Alice, Status: active");

    StringTemplateOutput out3 = new StringTemplateOutput();
    cardTemplate.render(
        MapRenderContext.of(Map.of("active", false, "user", Map.of("name", "Bob"))), out3);
    assertThat(out3.toString().trim()).isEqualTo("User: Bob, Status: inactive");
  }
}
