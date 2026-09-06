package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class TemplateExceptionTest {

  @Test
  void preservesExceptionMetadata() {
    TemplateId id = TemplateId.of("views/home.vm");
    SourceSpan span = SourceSpan.of(12, 18, 2, 5, 2, 11);
    DiagnosticCode code = DiagnosticCode.of("SECURITY", "DENIED_METHOD");
    IOException cause = new IOException("Connection reset");

    TemplateSecurityException ex =
        new TemplateSecurityException("Method invocation denied", id, span, code, cause);

    assertThat(ex.templateId()).contains(id);
    assertThat(ex.span()).isEqualTo(span);
    assertThat(ex.code()).contains(code);
    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(ex.getMessage())
        .contains("[SECURITY:DENIED_METHOD]")
        .contains("views/home.vm:2:5")
        .contains("Method invocation denied");
  }

  @Test
  void supportsUnknownSpanAndMissingCode() {
    TemplateSyntaxException ex = new TemplateSyntaxException("Syntax error", null, null, null);
    assertThat(ex.templateId()).isEmpty();
    assertThat(ex.span()).isEqualTo(SourceSpan.UNKNOWN);
    assertThat(ex.code()).isEmpty();
    assertThat(ex.getMessage()).isEqualTo("Syntax error");
  }
}
