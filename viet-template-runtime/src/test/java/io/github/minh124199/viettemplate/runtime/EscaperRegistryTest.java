package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EscaperRegistryTest {

  @Test
  @DisplayName("Default registry includes all standard escapers")
  void testDefaultRegistry() {
    EscaperRegistry registry = EscaperRegistry.standard();

    assertThat(registry.get(EscapeMode.HTML_TEXT)).isSameAs(StandardEscapers.htmlText());
    assertThat(registry.get(EscapeMode.HTML_ATTRIBUTE_QUOTED))
        .isSameAs(StandardEscapers.htmlAttribute());
    assertThat(registry.get(EscapeMode.URL_COMPONENT)).isSameAs(StandardEscapers.urlComponent());
    assertThat(registry.get(EscapeMode.JS_STRING)).isSameAs(StandardEscapers.jsString());
    assertThat(registry.get(EscapeMode.CSS_STRING)).isSameAs(StandardEscapers.cssString());
    assertThat(registry.get(EscapeMode.RAW)).isSameAs(StandardEscapers.raw());

    assertThat(registry.get("html_text")).isSameAs(StandardEscapers.htmlText());
    assertThat(registry.get("url_component")).isSameAs(StandardEscapers.urlComponent());
  }

  @Test
  @DisplayName("Custom escaper can be registered by mode and by name")
  void testCustomRegistration() {
    EscaperRegistry registry = new EscaperRegistry();

    Escaper custom =
        new Escaper() {
          @Override
          public EscapeMode mode() {
            return EscapeMode.RAW;
          }

          @Override
          public void escape(CharSequence input, TemplateOutput output) throws IOException {
            output.write("CUSTOM:" + input);
          }
        };

    registry.register(custom);
    assertThat(registry.get(EscapeMode.RAW)).isSameAs(custom);

    registry.register("custom_mode", custom);
    assertThat(registry.get("CUSTOM_MODE")).isSameAs(custom);
  }
}
