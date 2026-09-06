package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DeterministicFuzzTest {

  private static final long SEED = 42842079L;

  private static final String[] SNIPPETS = {
    "Hello $var world",
    "#if($condition) branch 1 #elseif($other) branch 2 #else fallback #end",
    "#foreach($item in $items) #if($item == 'break') #break #end $item #else none #end",
    "#set($val = 1 + 2 * 3)",
    "#set($nested.prop = 'updated')",
    "#macro(fn $p1 $p2='default') val: $p1, $p2 #end #fn('call')",
    "#define($block) block content $var #end $block",
    "\\$escaped and \\\\$doubleEscaped",
    "${var|'fallback'}",
    "$!quiet and $!{quietFormal}",
    "#stop('done')",
    "#[[ unparsed $raw #end ]]#",
    "$list[0] and $map['k']",
    "String concat: $str + ' tail'"
  };

  @Test
  void fuzzedTemplatesExecuteDeterministicallyWithoutJvmCrashes() {
    Random random = new Random(SEED);
    VtlInterpreter interpreter = new VtlInterpreter();

    Map<String, Object> ctx =
        Map.of(
            "var",
            "Value",
            "condition",
            true,
            "other",
            false,
            "items",
            List.of("A", "B", "break", "C"),
            "nested",
            new java.util.HashMap<>(Map.of("prop", "initial")),
            "list",
            List.of("one", "two"),
            "map",
            Map.of("k", "v"),
            "str",
            "head");

    for (int i = 0; i < 200; i++) {
      StringBuilder templateBuilder = new StringBuilder();
      int snippetCount = 1 + random.nextInt(6);
      for (int s = 0; s < snippetCount; s++) {
        templateBuilder.append(SNIPPETS[random.nextInt(SNIPPETS.length)]).append("\n");
      }

      String templateContent = templateBuilder.toString();
      SourceText source = SourceText.of("fuzz_" + i + ".vm", templateContent);
      VtlParseResult parseResult = VtlParser.parse(source);

      if (!parseResult.hasErrors()) {
        try {
          StringTemplateOutput output = new StringTemplateOutput();
          interpreter.interpret(parseResult.template(), source, MapRenderContext.of(ctx), output);
          // If execution succeeds, output should be non-null
          assertThat(output.toString()).isNotNull();
        } catch (TemplateException expected) {
          // Expected controlled template exceptions (limits, syntax errors, etc.)
          assertThat(expected.getMessage()).isNotNull();
        } catch (Exception unexpected) {
          throw new AssertionError(
              "Unexpected non-template exception on iteration " + i + ": " + unexpected,
              unexpected);
        }
      }
    }
  }
}
