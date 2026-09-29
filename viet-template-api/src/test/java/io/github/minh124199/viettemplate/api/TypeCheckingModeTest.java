package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeCheckingModeTest {

  @Test
  @DisplayName("Default state and isEnabled checks")
  void isEnabledState() {
    assertThat(TypeCheckingMode.OFF.isEnabled()).isFalse();
    assertThat(TypeCheckingMode.WARN.isEnabled()).isTrue();
    assertThat(TypeCheckingMode.ERROR.isEnabled()).isTrue();
  }

  @Test
  @DisplayName("Parsing standard and alias values")
  void parsingValues() {
    assertThat(TypeCheckingMode.parse(null)).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingMode.parse("")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingMode.parse("   ")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingMode.parse("off")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingMode.parse("OFF")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingMode.parse("false")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingMode.parse("none")).isEqualTo(TypeCheckingMode.OFF);

    assertThat(TypeCheckingMode.parse("warn")).isEqualTo(TypeCheckingMode.WARN);
    assertThat(TypeCheckingMode.parse("WARN")).isEqualTo(TypeCheckingMode.WARN);
    assertThat(TypeCheckingMode.parse("warning")).isEqualTo(TypeCheckingMode.WARN);

    assertThat(TypeCheckingMode.parse("error")).isEqualTo(TypeCheckingMode.ERROR);
    assertThat(TypeCheckingMode.parse("ERROR")).isEqualTo(TypeCheckingMode.ERROR);
    assertThat(TypeCheckingMode.parse("strict")).isEqualTo(TypeCheckingMode.ERROR);
    assertThat(TypeCheckingMode.parse("true")).isEqualTo(TypeCheckingMode.ERROR);
  }

  @Test
  @DisplayName("Parsing invalid string throws IllegalArgumentException")
  void parsingInvalidThrows() {
    assertThatThrownBy(() -> TypeCheckingMode.parse("invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown TypeCheckingMode: invalid");
  }
}
