package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeedResolutionTest {

  private static final long DEFAULT_SEED = 0xD1FF3871EL;

  @Test
  @DisplayName("Seed Parsing: parses decimal numbers correctly")
  void testParseDecimalSeed() {
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("12345", DEFAULT_SEED)).isEqualTo(12345L);
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed(" 9876543210 ", DEFAULT_SEED))
        .isEqualTo(9876543210L);
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("-42", DEFAULT_SEED)).isEqualTo(-42L);
  }

  @Test
  @DisplayName("Seed Parsing: parses 0x and 0X hex numbers correctly")
  void testParseHexSeed() {
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("0xD1FF3871E", DEFAULT_SEED))
        .isEqualTo(0xD1FF3871EL);
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("0XCAFEBABE", DEFAULT_SEED))
        .isEqualTo(0xCAFEBABEL);
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("0x0", DEFAULT_SEED)).isEqualTo(0L);
  }

  @Test
  @DisplayName("Seed Parsing: blank or null returns default seed")
  void testParseBlankOrNull() {
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed(null, DEFAULT_SEED)).isEqualTo(DEFAULT_SEED);
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("", DEFAULT_SEED)).isEqualTo(DEFAULT_SEED);
    assertThat(AstIrAotDifferentialFuzzTest.parseSeed("   ", DEFAULT_SEED)).isEqualTo(DEFAULT_SEED);
  }

  @Test
  @DisplayName("Seed Parsing: malformed or overflow throws NumberFormatException")
  void testParseMalformedThrows() {
    assertThatThrownBy(() -> AstIrAotDifferentialFuzzTest.parseSeed("invalid_seed", DEFAULT_SEED))
        .isInstanceOf(NumberFormatException.class);
    assertThatThrownBy(() -> AstIrAotDifferentialFuzzTest.parseSeed("0xZZZZ", DEFAULT_SEED))
        .isInstanceOf(NumberFormatException.class);
  }

  @Test
  @DisplayName("Seed Resolution: establishes precedence (property > environment > default)")
  void testSeedResolutionPrecedence() {
    // 1. Both property and env set -> property wins
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed("100", "200", DEFAULT_SEED))
        .isEqualTo(100L);
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed("0xAA", "0xBB", DEFAULT_SEED))
        .isEqualTo(0xAAL);

    // 2. Property set, env blank/null -> property wins
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed("100", null, DEFAULT_SEED)).isEqualTo(100L);
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed("100", "", DEFAULT_SEED)).isEqualTo(100L);

    // 3. Property blank/null, env set -> env wins
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed(null, "200", DEFAULT_SEED)).isEqualTo(200L);
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed("  ", "0xCC", DEFAULT_SEED))
        .isEqualTo(0xCCL);

    // 4. Neither set -> default wins
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed(null, null, DEFAULT_SEED))
        .isEqualTo(DEFAULT_SEED);
    assertThat(AstIrAotDifferentialFuzzTest.resolveSeed("", "   ", DEFAULT_SEED))
        .isEqualTo(DEFAULT_SEED);
  }
}
