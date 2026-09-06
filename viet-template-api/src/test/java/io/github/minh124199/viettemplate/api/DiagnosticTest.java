package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiagnosticTest {

  @Test
  void createsDiagnosticCode() {
    DiagnosticCode code = DiagnosticCode.of("SYNTAX", "UNTERMINATED_STRING");
    assertThat(code.category()).isEqualTo("SYNTAX");
    assertThat(code.id()).isEqualTo("UNTERMINATED_STRING");
    assertThat(code.qualifiedCode()).isEqualTo("SYNTAX:UNTERMINATED_STRING");
    assertThat(code.toString()).isEqualTo("SYNTAX:UNTERMINATED_STRING");
  }

  @Test
  void rejectsInvalidDiagnosticCode() {
    assertThatThrownBy(() -> DiagnosticCode.of("", "ID"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiagnosticCode.of("CAT", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createsErrorDiagnostic() {
    DiagnosticCode code = DiagnosticCode.of("SYNTAX", "UNEXPECTED_TOKEN");
    SourceSpan span = SourceSpan.of(5, 10, 1, 6, 1, 11);
    Diagnostic diag = Diagnostic.error(code, "Unexpected token '#end'", span);

    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(diag.code()).isEqualTo(code);
    assertThat(diag.message()).isEqualTo("Unexpected token '#end'");
    assertThat(diag.primarySpan()).isEqualTo(span);
  }
}
