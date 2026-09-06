package io.github.minh124199.viettemplate.tck.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateDescriptor;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FoundationIntegrationTest {

  @Test
  void verifiesTemplateExecutionWithRuntimeOutputAndContext() throws IOException {
    TemplateId id = TemplateId.of("greeting.vm");
    TemplateDescriptor descriptor = TemplateDescriptor.of(id, "INTERPRETER");

    Template template =
        new Template() {
          @Override
          public TemplateDescriptor descriptor() {
            return descriptor;
          }

          @Override
          public void render(RenderContext context, TemplateOutput output) throws IOException {
            output.write("Hello, ");
            Object user = context.get("user");
            output.write(user != null ? user.toString() : "Guest");
            output.write("! Total: ");
            output.writeInt(100);
          }
        };

    RenderContext context = MapRenderContext.of("user", "Viet");
    StringTemplateOutput output = new StringTemplateOutput();
    template.render(context, output);

    assertThat(output.toString()).isEqualTo("Hello, Viet! Total: 100");
  }
}
