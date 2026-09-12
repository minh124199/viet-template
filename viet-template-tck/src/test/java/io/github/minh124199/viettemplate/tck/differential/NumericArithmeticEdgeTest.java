package io.github.minh124199.viettemplate.tck.differential;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NumericArithmeticEdgeTest {

  private final VtlInterpreterOptions options =
      VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build();

  @Test
  @DisplayName(
      "P11: Integer boundaries (MIN_VALUE, MAX_VALUE, 0, 1, -1) maintain cross-tier parity")
  void testIntegerBoundaries() {
    List<Object> boundaryValues =
        List.of(
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            0,
            1,
            -1,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)));

    for (Object val1 : boundaryValues) {
      for (Object val2 : boundaryValues) {
        Map<String, Object> context = Map.of("a", val1, "b", val2);

        // Test addition, subtraction, multiplication
        checkParity("$a + $b", context);
        checkParity("$a - $b", context);
        checkParity("$a * $b", context);

        // Comparisons
        checkParity("#if($a > $b)gt#{else}le#end", context);
        checkParity("#if($a < $b)lt#{else}ge#end", context);
        checkParity("#if($a == $b)eq#{else}ne#end", context);
        checkParity("#if($a != $b)ne#{else}eq#end", context);

        // Test division and remainder (handles division by zero deterministically)
        checkParity("$a / $b", context);
        checkParity("$a % $b", context);
      }
    }
  }

  @Test
  @DisplayName(
      "P11: Floating-point boundaries (0.0, -0.0, Double.MIN_VALUE, MAX_VALUE) maintain cross-tier"
          + " parity")
  void testFloatingPointBoundaries() {
    List<Double> doubles =
        List.of(0.0, -0.0, 1.0, -1.0, Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_NORMAL);

    for (Double d1 : doubles) {
      for (Double d2 : doubles) {
        Map<String, Object> context = Map.of("a", d1, "b", d2);

        checkParity("$a + $b", context);
        checkParity("$a - $b", context);
        checkParity("$a * $b", context);
        checkParity("$a / $b", context);
        checkParity("$a % $b", context);
        checkParity("#if($a == $b)eq#{else}ne#end", context);
        checkParity("#if($a > $b)gt#{else}le#end", context);
      }
    }
  }

  @Test
  @DisplayName("P11: Special float values (NaN, +Infinity, -Infinity) behavior across tiers")
  void testSpecialFloatValues() {
    List<Double> specials = List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

    for (Double special : specials) {
      Map<String, Object> context = Map.of("a", special, "b", 10.0);
      // Let's test if operations either succeed identically or throw semantically equivalent errors
      checkParity("$a", context);
      checkParity("#if($a)truthy#{else}falsy#end", context);
      checkParity("$a + $b", context);
      checkParity("$a - $b", context);
      checkParity("$a * $b", context);
      checkParity("$a / $b", context);
      checkParity("#if($a == $b)eq#{else}ne#end", context);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "#set($x = 10 / 0)",
        "#set($x = 10 % 0)",
        "#set($x = 10.0 / 0.0)",
        "#set($x = 10.0 % 0.0)",
        "#set($x = -10 / 0)"
      })
  @DisplayName("P11: Division by zero across tiers throws consistent diagnostic errors")
  void testDivisionByZeroParity(String template) {
    TierDifferentialHarness.DifferentialResult result =
        TierDifferentialHarness.runAcrossTiers(template, Map.of(), options);
    TierDifferentialHarness.assertTierParity(result);
  }

  private void checkParity(String template, Map<String, Object> context) {
    TierDifferentialHarness.DifferentialResult result =
        TierDifferentialHarness.runAcrossTiers(template, context, options);
    TierDifferentialHarness.assertTierParity(result);
  }
}
