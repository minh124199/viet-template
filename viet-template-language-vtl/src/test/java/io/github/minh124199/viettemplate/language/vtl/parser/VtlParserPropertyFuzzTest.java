package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.minh124199.viettemplate.api.TemplateException;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlParserPropertyFuzzTest {

  private static final long SEED = 0x9A45E701L;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  private static final String[] MALFORMED_AND_EDGE_FRAGMENTS = {
    "$",
    "$$",
    "$!",
    "${",
    "$!{",
    "${}",
    "$foo.",
    "$foo..",
    "$foo[",
    "$foo[0",
    "$foo[]",
    "$foo(1,",
    "$foo(",
    "$foo(a, b,",
    "##",
    "## unclosed comment",
    "#*",
    "#* unclosed block *",
    "*#",
    "#if",
    "#if(",
    "#if()",
    "#if(true",
    "#if($x ==)",
    "#elseif",
    "#elseif(",
    "#else",
    "#end",
    "#foreach",
    "#foreach(",
    "#foreach($item",
    "#foreach($item in",
    "#foreach($item in [1..)",
    "#foreach($item in [])",
    "#macro",
    "#macro(",
    "#macro(fn",
    "#macro(fn $p",
    "#define",
    "#define($b",
    "#set",
    "#set(",
    "#set($ = 1)",
    "#set($x =)",
    "#set($x = 1 +)",
    "#parse",
    "#parse(",
    "#evaluate",
    "#evaluate(",
    "#break",
    "#stop",
    "\"",
    "\"unclosed string",
    "\"string with $unclosed",
    "'",
    "'unclosed single quote",
    "(",
    ")",
    "[",
    "]",
    "{",
    "}",
    "\\",
    "\\\\",
    "\\$",
    "\\\\$",
    "\\#",
    "+",
    "-",
    "*",
    "/",
    "%",
    "==",
    "!=",
    "<",
    "<=",
    ">",
    ">=",
    "&&",
    "||",
    "!",
    "0",
    "1",
    "-1",
    "1234567890",
    "null",
    "true",
    "false",
    " ",
    "\t",
    "\r\n",
    "\n",
    "\0"
  };

  @Test
  @DisplayName("P1: Arbitrary bounded parser input never produces unexpected runtime exceptions")
  void arbitraryParserInputNeverCrashes() {
    SplittableRandom rng = new SplittableRandom(SEED);
    int iterations = isDeepMode() ? 5000 : 800;

    VtlParserOptions options =
        VtlParserOptions.DEFAULT
            .withMaxSourceCharacters(50_000)
            .withMaxAstNodes(2_000)
            .withMaxExpressionDepth(50)
            .withMaxDirectiveNesting(50);

    for (int i = 0; i < iterations; i++) {
      int fragmentCount = 1 + rng.nextInt(12);
      StringBuilder sb = new StringBuilder();
      for (int f = 0; f < fragmentCount; f++) {
        sb.append(MALFORMED_AND_EDGE_FRAGMENTS[rng.nextInt(MALFORMED_AND_EDGE_FRAGMENTS.length)]);
      }

      String input = sb.toString();
      SourceText source = SourceText.of("fuzz_p1_" + i + ".vtl", input);

      try {
        VtlParseResult result = VtlParser.parse(source, options);
        assertThat(result).isNotNull();
        assertThat(result.template()).isNotNull();
      } catch (TemplateException expected) {
        // Controlled and documented template exceptions (parse/limit/syntax errors)
        assertThat(expected.getMessage()).isNotNull();
      } catch (Throwable unexpected) {
        fail(
            String.format(
                "Parser crashed unexpectedly on iteration %d!%n"
                    + "Seed: 0x%X%n"
                    + "Exception: %s%n"
                    + "Input:%n%s",
                i, SEED, unexpected.getClass().getName(), input),
            unexpected);
      }
    }
  }

  @Test
  @DisplayName("P1: Malformed and unclosed directive sequences fail cleanly")
  void malformedDirectivesFailCleanly() {
    String[] malformedTemplates = {
      "#if(true) Hello",
      "#if(true) Hello #else",
      "#if(true) #if(false) nested #end",
      "#foreach($i in [1..3]) body",
      "#macro(myMacro $a body #end",
      "#define($block) unclosed",
      "## comment without newline at EOF",
      "#* unclosed block comment",
      "#[[ unclosed raw block",
      "$foo.",
      "$foo[",
      "$foo[(1 + 2)",
      "$foo(arg1,",
      "${unclosed",
      "$!{unclosed",
      "\\$escaped but ${unclosed",
      "#set($var = )",
      "#set($ = 'empty_var')"
    };

    VtlParserOptions options = VtlParserOptions.DEFAULT;

    for (int i = 0; i < malformedTemplates.length; i++) {
      String t = malformedTemplates[i];
      SourceText source = SourceText.of("malformed_" + i + ".vtl", t);
      try {
        VtlParseResult result = VtlParser.parse(source, options);
        assertThat(result).isNotNull();
        // Either result contains diagnostics, or succeeds safely
      } catch (TemplateException expected) {
        assertThat(expected.getMessage()).isNotNull();
      } catch (Throwable unexpected) {
        fail("Unexpected exception on malformed template: " + t, unexpected);
      }
    }
  }
}
