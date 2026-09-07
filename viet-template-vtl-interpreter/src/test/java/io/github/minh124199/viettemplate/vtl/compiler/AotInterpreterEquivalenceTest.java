package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AotInterpreterEquivalenceTest {

  record EquivalenceCase(String name, String template, Map<String, Object> context) {
    @Override
    public String toString() {
      return name;
    }
  }

  public record Person(String name, int age) {}

  public static class Employee {
    private final String role;

    public Employee(String role) {
      this.role = role;
    }

    public String getRole() {
      return role;
    }
  }

  public static class PublicItem {
    public final String label;

    public PublicItem(String label) {
      this.label = label;
    }
  }

  static List<EquivalenceCase> cases() {
    List<EquivalenceCase> list = new ArrayList<>();

    list.add(
        new EquivalenceCase(
            "static text with utf8",
            "Hello World! Chào bạn: Tiếng Việt có dấu à á ả ã ạ.",
            Map.of()));

    list.add(
        new EquivalenceCase(
            "variables and arithmetic",
            "#set($x = 10)#set($y = 20)Result: #( $x + $y * 2 )",
            Map.of()));

    Map<String, Object> model = new LinkedHashMap<>();
    model.put("person", new Person("Alice", 28));
    model.put("employee", new Employee("Engineer"));
    model.put("item", new PublicItem("Gadget"));
    model.put("map", Map.of("alpha", "1", "beta", "2"));
    model.put("items", List.of("One", "Two", "Three"));
    model.put("rawHtml", SafeHtml.of("<b>Safe HTML</b>"));
    model.put("untrusted", "<script>alert('xss')</script>");

    list.add(
        new EquivalenceCase(
            "members records beans fields maps",
            "Person: $person.name ($person.age) | Role: $employee.role | Item: $item.label | Map: $map.alpha,$map.beta",
            model));

    list.add(
        new EquivalenceCase(
            "conditionals and truthiness",
            "#if($person)Has Person: $person.name#{else}No Person#end | "
                + "#if($missing)Has Missing#{else}No Missing#end",
            model));

    list.add(
        new EquivalenceCase(
            "loops with collections and break",
            "#foreach($it in $items)#if($it == 'Three')#break#end[$it]#end",
            model));

    list.add(
        new EquivalenceCase(
            "safe content and contextual escaping",
            "Safe: $rawHtml | Escaped: $untrusted",
            model));

    list.add(
        new EquivalenceCase(
            "nested loop and conditional",
            "#set($total = 0)"
                + "#foreach($i in [1..5])"
                + "  #if($i > 2)"
                + "    [High: $i]"
                + "  #else"
                + "    [Low: $i]"
                + "  #end"
                + "#end",
            Map.of()));

    return list;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("AOT Bytecode matches IR and AST interpreters across O0, O1, O2, O3")
  void verifyAotEquivalence(EquivalenceCase scenario) throws IOException {
    SourceText source = SourceText.of(scenario.name() + ".vtl", scenario.template());
    VtlTemplate ast = VtlParser.parse(source).template();

    // 1. Run AST Tier as ground truth
    VtlInterpreterOptions astOptions =
        VtlInterpreterOptions.builder()
            .executionTier(ExecutionTier.AST)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
            .build();
    VtlInterpreter astInterpreter = new VtlInterpreter(astOptions);
    StringTemplateOutput astOut = new StringTemplateOutput();
    astInterpreter.render(source, ast, MapRenderContext.of(scenario.context()), astOut);
    String expected = astOut.toString();

    // Test across optimization levels
    List<IrOptimizationOptions> optLevels =
        List.of(
            IrOptimizationOptions.o0(),
            IrOptimizationOptions.o1(),
            IrOptimizationOptions.o2(),
            IrOptimizationOptions.o3());

    for (IrOptimizationOptions optOptions : optLevels) {
      // 2. Run IR Tier
      VtlInterpreterOptions irOptions =
          VtlInterpreterOptions.builder()
              .executionTier(ExecutionTier.IR)
              .optimizationOptions(optOptions)
              .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
              .build();
      VtlInterpreter irInterpreter = new VtlInterpreter(irOptions);
      StringTemplateOutput irOut = new StringTemplateOutput();
      irInterpreter.render(source, ast, MapRenderContext.of(scenario.context()), irOut);

      assertThat(irOut.toString())
          .as("IR output with %s must match AST for %s", optOptions.level(), scenario.name())
          .isEqualTo(expected);

      // 3. Run AOT Bytecode Tier
      VtlInterpreterOptions aotOptions =
          VtlInterpreterOptions.builder()
              .executionTier(ExecutionTier.AOT_BYTECODE)
              .optimizationOptions(optOptions)
              .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
              .build();
      VtlInterpreter aotInterpreter = new VtlInterpreter(aotOptions);
      StringTemplateOutput aotOut = new StringTemplateOutput();
      aotInterpreter.render(source, ast, MapRenderContext.of(scenario.context()), aotOut);

      assertThat(aotOut.toString())
          .as("AOT Bytecode output with %s must match AST for %s", optOptions.level(), scenario.name())
          .isEqualTo(expected);
    }
  }
}
