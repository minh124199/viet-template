package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ProductionModeRejectionTest {

  @Test
  void rejectsRuntimeCompilationWhenHardenedProductionModeEnabled() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId prewarmedId = TemplateId.of("prewarmed.vm");
    TemplateId dynamicId = TemplateId.of("dynamic.vm");

    repo.put(prewarmedId, "Prewarmed: $msg");
    repo.put(dynamicId, "Dynamic: $msg");

    // Pre-warm with runtime compilation allowed
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).rejectRuntimeCompilation(false).build();

    Template prewarmed = engine.get(prewarmedId);
    StringTemplateOutput out = new StringTemplateOutput();
    prewarmed.render(RenderContext.builder().put("msg", "OK").build(), out);
    assertThat(out.toString()).isEqualTo("Prewarmed: OK");

    // Now instantiate a hardened engine with runtime compilation strictly rejected
    VtlTemplateEngine hardenedEngine =
        VtlTemplateEngine.builder().repository(repo).rejectRuntimeCompilation(true).build();

    assertThat(hardenedEngine.rejectRuntimeCompilation()).isTrue();

    // Attempting to request uncompiled dynamic template must throw TemplateSecurityException
    assertThatThrownBy(() -> hardenedEngine.get(dynamicId))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("Runtime template compilation is rejected in production mode");

    engine.close();
    hardenedEngine.close();
  }
}
