package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InterpreterEquivalenceTest {

  record EquivalenceScenario(String name, String template, Map<String, Object> context) {
    @Override
    public String toString() {
      return name;
    }
  }

  static List<EquivalenceScenario> scenarios() {
    List<EquivalenceScenario> list = new ArrayList<>();

    list.add(
        new EquivalenceScenario(
            "plain text and comments",
            "Hello ## single-line\nWorld! #* multi-line *# Welcome.",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "simple, formal, and quiet references",
            "$name ${formal} $!quiet [${missing|'default'}]",
            Map.of("name", "Alice", "formal", "Bob")));

    list.add(
        new EquivalenceScenario(
            "arithmetic operators and ranges",
            "#set($sum = 10 + 5 * 2)\n"
                + "#set($sub = 20 - 4)\n"
                + "#set($div = 20 / 4)\n"
                + "#set($mod = 17 % 5)\n"
                + "#set($neg = -7)\n"
                + "Results: $sum, $sub, $div, $mod, $neg\n"
                + "Range: #foreach($i in [1..4])$i #end",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "relational and logical operations",
            "#if(10 > 5 && 2 < 3)yes1#end\n"
                + "#if(5 == 5 || 1 == 2)yes2#end\n"
                + "#if(!false)yes3#end\n"
                + "#if(4 >= 4 && 3 <= 5)yes4#end\n"
                + "#if(1 != 2)yes5#end",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "conditionals if-elseif-else",
            "#set($score = 85)\n"
                + "#if($score >= 90)Grade A"
                + "#{elseif}($score >= 80)Grade B"
                + "#{else}Grade C#end",
            Map.of()));

    Map<String, Object> loopCtx = new LinkedHashMap<>();
    loopCtx.put("items", List.of("alpha", "beta", "gamma"));
    list.add(
        new EquivalenceScenario(
            "foreach loop with metadata",
            "#foreach($item in $items)[idx=$foreach.index, count=$foreach.count, val=$item,"
                + " first=$foreach.first, last=$foreach.last]#end",
            loopCtx));

    list.add(
        new EquivalenceScenario(
            "nested foreach loops with parent metadata",
            "#foreach($outer in [1..2])"
                + "#foreach($inner in [1..2])"
                + "[$foreach.parent.count:$outer-$foreach.count:$inner]"
                + "#end"
                + "#end",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "foreach with break directive",
            "#foreach($i in [1..10])#if($i == 4)#break#end$i #end",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "macro definition and call with params",
            "#macro(greet $who $greeting)\n"
                + "$greeting, $who!\n"
                + "#end\n"
                + "#greet('Alice', 'Good morning')\n"
                + "#greet('Bob', 'Hello')",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "block macro with body content",
            "#macro(card $title)\n"
                + "<div class=\"card\"><header>$title</header><section>$bodyContent</section></div>\n"
                + "#end\n"
                + "#@card('Notice')This is important info.#end",
            Map.of()));

    list.add(
        new EquivalenceScenario(
            "define directive block",
            "#define($block)\n"
                + "Generated for $user at generation 1.\n"
                + "#end\n"
                + "#set($user = 'Diana')$block\n"
                + "#set($user = 'Edward')$block",
            Map.of()));

    Map<String, Object> mapCtx = new HashMap<>();
    Map<String, Object> user = new HashMap<>();
    user.put("firstName", "John");
    user.put("scores", List.of(100, 95, 88));
    mapCtx.put("user", user);

    list.add(
        new EquivalenceScenario(
            "property and index access and mutation",
            "Name: $user.firstName\n"
                + "First score: $user.scores[0]\n"
                + "#set($user.firstName = 'Jane')\n"
                + "Updated: $user.firstName",
            mapCtx));

    return list;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deepCopy(Map<String, Object> map) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object val = entry.getValue();
      if (val instanceof Map<?, ?> m) {
        copy.put(entry.getKey(), deepCopy((Map<String, Object>) m));
      } else if (val instanceof List<?> l) {
        copy.put(entry.getKey(), new ArrayList<>(l));
      } else {
        copy.put(entry.getKey(), val);
      }
    }
    return copy;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  @DisplayName("AST and IR interpreters produce identical output")
  void verifyAstAndIrEquivalence(EquivalenceScenario scenario) throws IOException {
    SourceText source = SourceText.of(scenario.name() + ".vm", scenario.template());
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreter astInterpreter =
        new VtlInterpreter(
            VtlInterpreterOptions.builder().executionTier(ExecutionTier.AST).build());
    StringTemplateOutput astOutput = new StringTemplateOutput();
    astInterpreter.interpret(
        ast, source, MapRenderContext.of(deepCopy(scenario.context())), astOutput);

    VtlInterpreter irInterpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build());
    StringTemplateOutput irOutput = new StringTemplateOutput();
    irInterpreter.interpret(
        ast, source, MapRenderContext.of(deepCopy(scenario.context())), irOutput);

    assertThat(irOutput.toString())
        .as(
            "Execution output must match identically between AST and IR tiers for %s",
            scenario.name())
        .isEqualTo(astOutput.toString());
  }
}
