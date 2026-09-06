package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering arithmetic operators (+, -, *, /, %, unary -). */
public final class ArithmeticCorpus {

  private ArithmeticCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Basic operations
    list.add(
        CompatibilityScenario.simple(
            "arithmetic.add.integer", ScenarioCategory.EXPRESSION, "#set($x = 10 + 20)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.subtract.integer", ScenarioCategory.EXPRESSION, "#set($x = 50 - 18)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.multiply.integer", ScenarioCategory.EXPRESSION, "#set($x = 6 * 7)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.divide.integer", ScenarioCategory.EXPRESSION, "#set($x = 42 / 7)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.modulo.integer", ScenarioCategory.EXPRESSION, "#set($x = 17 % 5)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.unary.negative", ScenarioCategory.EXPRESSION, "#set($x = -42)[$x]"));

    // 2. Precedence and grouping
    list.add(
        CompatibilityScenario.simple(
            "arithmetic.precedence.multiply-before-add",
            ScenarioCategory.EXPRESSION,
            "#set($x = 2 + 3 * 4)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.precedence.parentheses",
            ScenarioCategory.EXPRESSION,
            "#set($x = (2 + 3) * 4)[$x]"));

    // 3. Floating point arithmetic
    list.add(
        CompatibilityScenario.simple(
            "arithmetic.float.add", ScenarioCategory.EXPRESSION, "#set($x = 1.5 + 2.25)[$x]"));

    // 4. BigDecimal and BigInteger
    list.add(
        CompatibilityScenario.of(
            "arithmetic.bigdecimal.add",
            ScenarioCategory.EXPRESSION,
            "#set($x = $a + $b)[$x]",
            () -> Map.of("a", new BigDecimal("123.45"), "b", new BigDecimal("67.89"))));

    list.add(
        CompatibilityScenario.of(
            "arithmetic.biginteger.multiply",
            ScenarioCategory.EXPRESSION,
            "#set($x = $a * $b)[$x]",
            () -> Map.of("a", new BigInteger("1000000"), "b", new BigInteger("2000000"))));

    // 5. String concatenation with arithmetic
    list.add(
        CompatibilityScenario.simple(
            "arithmetic.string.concat-number-right",
            ScenarioCategory.EXPRESSION,
            "#set($x = 'Count: ' + 5)[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.string.concat-number-left",
            ScenarioCategory.EXPRESSION,
            "#set($x = 5 + ' items')[$x]"));

    list.add(
        CompatibilityScenario.simple(
            "arithmetic.string.concat-strings",
            ScenarioCategory.EXPRESSION,
            "#set($x = 'Hello ' + 'World')[$x]"));

    // 6. Division by zero
    list.add(
        CompatibilityScenario.simple(
            "arithmetic.divide-by-zero", ScenarioCategory.ERROR, "#set($x = 10 / 0)[$x]"));

    return list;
  }
}
