package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import org.junit.jupiter.api.Test;

class VtlParserNestingLimitTest {

  @Test
  void protectsAgainstStackOverflowOnDeeplyNestedBlocks() {
    int depth = 300;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append("#if(true)\n");
    }
    sb.append("deep\n");
    for (int i = 0; i < depth; i++) {
      sb.append("#end\n");
    }

    SourceText source = SourceText.from(sb.toString(), TemplateId.of("deep_blocks"));
    VtlParseResult result = assertDoesNotThrow(() -> VtlParser.parse(source));

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream().anyMatch(d -> d.code().id().equals("MAX_NESTING_EXCEEDED")),
        "Must emit MAX_NESTING_EXCEEDED diagnostic");
  }

  @Test
  void honorsConfiguredCustomNestingLimit() {
    int depth = 20;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append("#if(true)\n");
    }
    sb.append("payload\n");
    for (int i = 0; i < depth; i++) {
      sb.append("#end\n");
    }

    SourceText source = SourceText.from(sb.toString(), TemplateId.of("custom_depth"));
    VtlParserOptions options = VtlParserOptions.ofDefaults().withMaxNestingDepth(10);
    VtlParseResult result = VtlParser.parse(source, options);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream().anyMatch(d -> d.code().id().equals("MAX_NESTING_EXCEEDED")));
  }

  @Test
  void protectsAgainstStackOverflowOnDeeplyNestedExpressions() {
    int depth = 300;
    StringBuilder sb = new StringBuilder("#set($x = ");
    for (int i = 0; i < depth; i++) {
      sb.append("(");
    }
    sb.append("1");
    for (int i = 0; i < depth; i++) {
      sb.append(")");
    }
    sb.append(")");

    SourceText source = SourceText.from(sb.toString(), TemplateId.of("deep_expr"));
    VtlParseResult result = assertDoesNotThrow(() -> VtlParser.parse(source));

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream().anyMatch(d -> d.code().id().equals("MAX_NESTING_EXCEEDED")));
  }
}
