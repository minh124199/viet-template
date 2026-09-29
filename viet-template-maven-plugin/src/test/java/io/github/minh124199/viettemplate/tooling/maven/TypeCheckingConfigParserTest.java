package io.github.minh124199.viettemplate.tooling.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypeCheckingConfigParserTest {

  @Test
  @DisplayName("Null or blank string resolves to OFF")
  void testNullOrBlankResolvesToOff() {
    assertThat(TypeCheckingConfigParser.parse(null)).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingConfigParser.parse("")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingConfigParser.parse("   ")).isEqualTo(TypeCheckingMode.OFF);
    assertThat(TypeCheckingConfigParser.parse("\t\n")).isEqualTo(TypeCheckingMode.OFF);
  }

  @ParameterizedTest
  @ValueSource(strings = {"off", "OFF", "Off", "  off  "})
  @DisplayName("OFF variations parse correctly")
  void testOffVariations(String input) {
    assertThat(TypeCheckingConfigParser.parse(input)).isEqualTo(TypeCheckingMode.OFF);
  }

  @ParameterizedTest
  @ValueSource(strings = {"warn", "WARN", "Warn", "warning", "WARNING", "Warning", "  warn  "})
  @DisplayName("WARN variations parse correctly")
  void testWarnVariations(String input) {
    assertThat(TypeCheckingConfigParser.parse(input)).isEqualTo(TypeCheckingMode.WARN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"error", "ERROR", "Error", "  error  "})
  @DisplayName("ERROR variations parse correctly")
  void testErrorVariations(String input) {
    assertThat(TypeCheckingConfigParser.parse(input)).isEqualTo(TypeCheckingMode.ERROR);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false", "strict", "none", "aggressive", "invalid", "1", "0"})
  @DisplayName("Invalid or disallowed alias strings throw IllegalArgumentException")
  void testInvalidStringsThrow(String input) {
    assertThatThrownBy(() -> TypeCheckingConfigParser.parse(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid typeChecking configuration: '" + input + "'");
  }

  @Test
  @DisplayName("Parser is resilient to non-English default locales such as Turkish")
  void testLocaleResilience() {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertThat(TypeCheckingConfigParser.parse("warning")).isEqualTo(TypeCheckingMode.WARN);
      assertThat(TypeCheckingConfigParser.parse("warn")).isEqualTo(TypeCheckingMode.WARN);
      assertThat(TypeCheckingConfigParser.parse("error")).isEqualTo(TypeCheckingMode.ERROR);
      assertThat(TypeCheckingConfigParser.parse("off")).isEqualTo(TypeCheckingMode.OFF);
    } finally {
      Locale.setDefault(originalLocale);
    }
  }
}
