package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VtlParserFuzzTest {

  private static final long FUZZ_SEED = 0xCAFEBABE;
  private static final int ITERATIONS = 1200;

  private static final String[] FRAGMENTS = {
    "Hello ",
    "world",
    "$foo",
    "$!bar",
    "${baz}",
    "$!{qux}",
    "$user.name",
    "$list[0]",
    "$map['key']",
    "$service.call($a, 1)",
    "${user|'guest'}",
    "#set($x = 10)",
    "#if($cond)",
    "#elseif($other)",
    "#else",
    "#end",
    "#{if}($c)",
    "#{end}",
    "#foreach($item in $items)",
    "#include('a.vtl')",
    "#parse('b.vtl')",
    "#macro(m $p)",
    "#@panel('title')",
    "#break",
    "#stop",
    "#evaluate('x')",
    "#define($b)",
    "\\$",
    "\\\\$",
    "\\#",
    "## single comment\n",
    "#* block comment *#",
    "#[[ raw block ]]#",
    "(",
    ")",
    "[",
    "]",
    "{",
    "}",
    "\"",
    "'",
    "==",
    "!=",
    "<=",
    ">=",
    "&&",
    "||",
    "+",
    "-",
    "*",
    "/",
    "%",
    "123",
    "3.14",
    "true",
    "false",
    "null",
    " ",
    "\n",
    "\r\n",
    "\t",
    "xin chào tiếng Việt",
    "🍕🎉🚀",
    "invalid #123 $99"
  };

  @Test
  void deterministicFuzzTest() {
    Random rng = new Random(FUZZ_SEED);

    for (int i = 0; i < ITERATIONS; i++) {
      int numFragments = 1 + rng.nextInt(15);
      StringBuilder sb = new StringBuilder();
      for (int f = 0; f < numFragments; f++) {
        sb.append(FRAGMENTS[rng.nextInt(FRAGMENTS.length)]);
      }

      String content = sb.toString();
      SourceText source = SourceText.from(content, TemplateId.of("fuzz_" + i));

      try {
        VtlParseResult result = VtlParser.parse(source);
        assertNotNull(result);
        assertNotNull(result.template());
        VtlAstInvariantWalker.assertInvariants(result.template(), source);
      } catch (Exception e) {
        fail("Parser threw exception on iteration " + i + " for input:\n" + content, e);
      }
    }
  }
}
