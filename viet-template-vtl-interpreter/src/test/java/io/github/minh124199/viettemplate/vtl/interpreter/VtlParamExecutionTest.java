package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class VtlParamExecutionTest {

  public record Person(String name, int age) {}

  public record Department(String title, List<Person> members) {}

  private String renderWithTier(String template, Map<String, Object> context, ExecutionTier tier)
      throws IOException {
    SourceText source = SourceText.of("test.vm", template);
    VtlParseResult parseResult = VtlParser.parse(source, VtlParserOptions.ofDefaults());
    if (parseResult.hasErrors()) {
      throw new AssertionError("Parse errors: " + parseResult.diagnostics());
    }
    VtlTemplate ast = parseResult.template();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().executionTier(tier).build());
    StringTemplateOutput output = new StringTemplateOutput();
    interpreter.render(source, ast, MapRenderContext.of(context), output);
    return output.toString();
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Zero bytes rendered for standalone #param declarations across all tiers")
  void testZeroOutputForParamDeclarations(ExecutionTier tier) throws IOException {
    String template = "#param(String $name)\nHello, ${name}!";
    String result = renderWithTier(template, Map.of("name", "World"), tier);
    assertThat(result).isEqualTo("Hello, World!");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Multiple prologue #param declarations with comments and whitespace produce zero extra bytes")
  void testMultiplePrologueParamDeclarations(ExecutionTier tier) throws IOException {
    String template =
        "## Prologue comment\n"
            + "#param(String $first)\n"
            + "#param(String $last)\n"
            + "$first $last";
    String result = renderWithTier(template, Map.of("first", "John", "last", "Doe"), tier);
    assertThat(result).isEqualTo("John Doe");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Partial typing: declared parameter is typed while undeclared variable remains dynamic")
  void testPartialTypingExecution(ExecutionTier tier) throws IOException {
    String template =
        "#param(io.github.minh124199.viettemplate.vtl.interpreter.VtlParamExecutionTest.Person"
            + " $person)\n"
            + "Name: $person.name, Dynamic: $dynamicObj.value";
    Map<String, Object> context =
        Map.of("person", new Person("Alice", 30), "dynamicObj", Map.of("value", "untyped-ok"));
    String result = renderWithTier(template, context, tier);
    assertThat(result).isEqualTo("Name: Alice, Dynamic: untyped-ok");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Default parameter value is used when context parameter is omitted")
  void testDefaultValueWhenParameterOmitted(ExecutionTier tier) throws IOException {
    String template = "#param(String $title = 'Untitled')\nTitle: $title";
    String result = renderWithTier(template, Map.of(), tier);
    assertThat(result).isEqualTo("Title: Untitled");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Context parameter overrides default parameter value")
  void testContextOverridesDefaultValue(ExecutionTier tier) throws IOException {
    String template = "#param(String $title = 'Untitled')\nTitle: $title";
    String result = renderWithTier(template, Map.of("title", "Custom Title"), tier);
    assertThat(result).isEqualTo("Title: Custom Title");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Multiple parameters with mixed provided and default values")
  void testMixedProvidedAndDefaultParameters(ExecutionTier tier) throws IOException {
    String template =
        "#param(String $greeting)\n" + "#param(String $target = 'World')\n" + "$greeting, $target!";
    String result1 = renderWithTier(template, Map.of("greeting", "Hello"), tier);
    assertThat(result1).isEqualTo("Hello, World!");

    String result2 =
        renderWithTier(
            template, Map.of("greeting", "Good morning", "target", "VietTemplate"), tier);
    assertThat(result2).isEqualTo("Good morning, VietTemplate!");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Numeric default value expressions evaluated correctly")
  void testNumericDefaultValueExpression(ExecutionTier tier) throws IOException {
    String template = "#param(int $count = 10 + 5)\nCount: $count";
    String result = renderWithTier(template, Map.of(), tier);
    assertThat(result).isEqualTo("Count: 15");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Nested property chain and record access across execution tiers")
  void testNestedPropertyChain(ExecutionTier tier) throws IOException {
    String template =
        "#param(io.github.minh124199.viettemplate.vtl.interpreter.VtlParamExecutionTest.Department"
            + " $dept)\n"
            + "$dept.title: #foreach($m in $dept.members)${m.name} (${m.age}) #end";
    Department dept =
        new Department("Engineering", List.of(new Person("Alice", 28), new Person("Bob", 35)));
    String result = renderWithTier(template, Map.of("dept", dept), tier);
    assertThat(result).isEqualTo("Engineering: Alice (28) Bob (35) ");
  }

  @Test
  @DisplayName("Untyped template executes identically without any #param declarations")
  void testUntypedTemplateEquivalence() throws IOException {
    String template = "Hello $name, age $age!";
    Map<String, Object> ctx = Map.of("name", "Charlie", "age", 40);

    String astRes = renderWithTier(template, ctx, ExecutionTier.AST);
    String irRes = renderWithTier(template, ctx, ExecutionTier.IR);
    String aotRes = renderWithTier(template, ctx, ExecutionTier.AOT_BYTECODE);

    assertThat(astRes).isEqualTo("Hello Charlie, age 40!");
    assertThat(irRes).isEqualTo(astRes);
    assertThat(aotRes).isEqualTo(astRes);
  }

  public static class Level1 {
    public static class Level2 {
      public record ValueHolder(String val) {}
    }
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Multi-dimensional primitive array execution across all execution tiers")
  void testMultiDimensionalArray(ExecutionTier tier) throws IOException {
    String template = "#param(int[][] $matrix)\nVal: $matrix[0][1], Rows: $matrix.length";
    int[][] matrix = new int[][] {{10, 20}, {30, 40}};
    String result = renderWithTier(template, Map.of("matrix", matrix), tier);
    assertThat(result).isEqualTo("Val: 20, Rows: 2");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Deeply nested generic parameter execution across all execution tiers")
  void testDeeplyNestedGenerics(ExecutionTier tier) throws IOException {
    String template =
        "#param(java.util.Map<java.lang.String, java.util.List<java.util.Map<java.lang.String,"
            + " java.util.Set<java.lang.Integer>>>> $deep)\n"
            + "$deep['root'][0]['inner'].size()";
    Map<String, Object> deep = Map.of("root", List.of(Map.of("inner", java.util.Set.of(1, 2, 3))));
    String result = renderWithTier(template, Map.of("deep", deep), tier);
    assertThat(result).isEqualTo("3");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Doubly-nested static inner class resolution in #param")
  void testDoublyNestedClassResolution(ExecutionTier tier) throws IOException {
    String template =
        "#param(io.github.minh124199.viettemplate.vtl.interpreter.VtlParamExecutionTest.Level1.Level2.ValueHolder"
            + " $holder)\n"
            + "Value: $holder.val";
    String result =
        renderWithTier(
            template, Map.of("holder", new Level1.Level2.ValueHolder("nested-success")), tier);
    assertThat(result).isEqualTo("Value: nested-success");
  }

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName("Wildcard parameter execution across tiers")
  void testWildcardParameter(ExecutionTier tier) throws IOException {
    String template = "#param(Comparable<? super String> $comp)\nResult: $comp.compareTo('hello')";
    String result = renderWithTier(template, Map.of("comp", "hello"), tier);
    assertThat(result).isEqualTo("Result: 0");
  }
}
