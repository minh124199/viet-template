package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateIdTest {

  @Test
  void createsValidTemplateId() {
    TemplateId id = TemplateId.of("users/list.vm");
    assertThat(id.value()).isEqualTo("users/list.vm");
    assertThat(id.toString()).isEqualTo("users/list.vm");
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> TemplateId.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("value must not be null");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void rejectsBlank(String invalid) {
    assertThatThrownBy(() -> TemplateId.of(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void rejectsLeadingSlash() {
    assertThatThrownBy(() -> TemplateId.of("/users/list.vm"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not start with '/'");
  }

  @Test
  void rejectsBackslashes() {
    assertThatThrownBy(() -> TemplateId.of("users\\list.vm"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain backslashes");
  }

  @Test
  void rejectsPathTraversal() {
    assertThatThrownBy(() -> TemplateId.of("users/../list.vm"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain path traversal");
  }

  @Test
  void comparesLexicographically() {
    TemplateId a = TemplateId.of("a.vm");
    TemplateId b = TemplateId.of("b.vm");
    assertThat(a.compareTo(b)).isLessThan(0);
    assertThat(b.compareTo(a)).isGreaterThan(0);
    assertThat(a.compareTo(TemplateId.of("a.vm"))).isEqualTo(0);
  }
}
