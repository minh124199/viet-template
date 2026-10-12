package io.github.minh124199.viettemplate.language.vtl.parser;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlParamDeclarationNode;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.Test;

class VtlParamDirectiveParserTest {

  @Test
  void parsesSingleTypedParameter() {
    SourceText source =
        SourceText.from(
            "#param(com.example.Employee $employee)", TemplateId.of("single_param_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlTemplate template = result.template();
    assertEquals(1, template.parameters().size());
    assertTrue(template.children().isEmpty(), "Parameters must not be in template children");

    VtlParamDeclarationNode param = template.parameters().get(0);
    assertEquals("employee", param.name());
    assertEquals("com.example.Employee", param.typeText());
    assertFalse(param.nullable());
    assertTrue(param.defaultValue().isEmpty());
  }

  @Test
  void parsesMultipleSeparateParamDirectives() {
    SourceText source =
        SourceText.from(
            "#param(com.example.Employee $employee)\n#param(java.lang.String $title)",
            TemplateId.of("multi_param_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    VtlTemplate template = result.template();
    assertEquals(2, template.parameters().size());
    assertEquals("employee", template.parameters().get(0).name());
    assertEquals("com.example.Employee", template.parameters().get(0).typeText());
    assertEquals("title", template.parameters().get(1).name());
    assertEquals("java.lang.String", template.parameters().get(1).typeText());
  }

  @Test
  void parsesCommaSeparatedParametersInSingleDirective() {
    SourceText source =
        SourceText.from(
            "#param(Employee $employee, Department $dept, int $count)",
            TemplateId.of("comma_sep_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlParamDeclarationNode> params = result.template().parameters();
    assertEquals(3, params.size());
    assertEquals("employee", params.get(0).name());
    assertEquals("Employee", params.get(0).typeText());
    assertEquals("dept", params.get(1).name());
    assertEquals("Department", params.get(1).typeText());
    assertEquals("count", params.get(2).name());
    assertEquals("int", params.get(2).typeText());
  }

  @Test
  void parsesGenericAndWildcardTypes() {
    SourceText source =
        SourceText.from(
            "#param(java.util.List<com.example.Employee> $employees, java.util.Map<String,"
                + " List<Integer>> $lookup, List<? extends Employee> $wild)",
            TemplateId.of("generics_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlParamDeclarationNode> params = result.template().parameters();
    assertEquals(3, params.size());
    assertEquals("java.util.List<com.example.Employee>", params.get(0).typeText());
    assertEquals("java.util.Map<String, List<Integer>>", params.get(1).typeText());
    assertEquals("List<? extends Employee>", params.get(2).typeText());
  }

  @Test
  void parsesArrayTypes() {
    SourceText source =
        SourceText.from("#param(int[] $counts, String[][] $matrix)", TemplateId.of("array_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlParamDeclarationNode> params = result.template().parameters();
    assertEquals(2, params.size());
    assertEquals("int[]", params.get(0).typeText());
    assertEquals("String[][]", params.get(1).typeText());
  }

  @Test
  void parsesNullableMarkersOnTypeAndVariable() {
    SourceText source =
        SourceText.from("#param(Manager? $mgr1, Manager $mgr2?)", TemplateId.of("nullable_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlParamDeclarationNode> params = result.template().parameters();
    assertEquals(2, params.size());
    assertEquals("mgr1", params.get(0).name());
    assertEquals("Manager", params.get(0).typeText());
    assertTrue(params.get(0).nullable());

    assertEquals("mgr2", params.get(1).name());
    assertEquals("Manager", params.get(1).typeText());
    assertTrue(params.get(1).nullable());
  }

  @Test
  void parsesDefaultValues() {
    SourceText source =
        SourceText.from(
            "#param(int $page = 1, String $sort = 'desc', boolean $active = true)",
            TemplateId.of("defaults_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    List<VtlParamDeclarationNode> params = result.template().parameters();
    assertEquals(3, params.size());
    assertTrue(params.get(0).defaultValue().isPresent());
    assertTrue(params.get(1).defaultValue().isPresent());
    assertTrue(params.get(2).defaultValue().isPresent());
  }

  @Test
  void allowsLeadingWhitespaceAndCommentsBeforeParam() {
    SourceText source =
        SourceText.from(
            "## Template header comment\n\n   #param(String $title)\n<h1>$title</h1>",
            TemplateId.of("leading_ws_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertFalse(result.hasErrors(), () -> "Diagnostics: " + result.diagnostics());
    VtlAstInvariantWalker.assertInvariants(result.template(), source);

    assertEquals(1, result.template().parameters().size());
    assertEquals("title", result.template().parameters().get(0).name());
    assertFalse(result.template().children().isEmpty());
  }

  @Test
  void rejectsDuplicateParameters() {
    SourceText source =
        SourceText.from(
            "#param(Employee $employee)\n#param(Customer $employee)",
            TemplateId.of("duplicate_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> "VTLS:2108".equals(d.code().category() + ":" + d.code().id())));
  }

  @Test
  void rejectsParamAfterLiteralText() {
    SourceText source =
        SourceText.from(
            "<h1>Hello</h1>\n#param(Employee $employee)", TemplateId.of("param_after_text_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> "VTLS:2111".equals(d.code().category() + ":" + d.code().id())));
  }

  @Test
  void rejectsParamAfterReference() {
    SourceText source =
        SourceText.from(
            "$title\n#param(Employee $employee)", TemplateId.of("param_after_ref_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> "VTLS:2111".equals(d.code().category() + ":" + d.code().id())));
  }

  @Test
  void rejectsParamAfterDirective() {
    SourceText source =
        SourceText.from(
            "#set($x = 1)\n#param(Employee $employee)", TemplateId.of("param_after_dir_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> "VTLS:2111".equals(d.code().category() + ":" + d.code().id())));
  }

  @Test
  void rejectsParamNestedInBlocks() {
    SourceText source =
        SourceText.from(
            "#if(true)\n  #param(Employee $employee)\n#end", TemplateId.of("param_in_block_test"));
    VtlParseResult result = VtlParser.parse(source);

    assertTrue(result.hasErrors());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(d -> "VTLS:2111".equals(d.code().category() + ":" + d.code().id())));
  }

  @Test
  void rejectsMalformedParamDeclarations() {
    // Empty #param()
    VtlParseResult r1 =
        VtlParser.parse(SourceText.from("#param()", TemplateId.of("empty_param_test")));
    assertTrue(r1.hasErrors());
    assertTrue(
        r1.diagnostics().stream()
            .anyMatch(d -> "VTLS:2110".equals(d.code().category() + ":" + d.code().id())));

    // Missing $var
    VtlParseResult r2 =
        VtlParser.parse(SourceText.from("#param(Employee)", TemplateId.of("missing_var_test")));
    assertTrue(r2.hasErrors());
    assertTrue(
        r2.diagnostics().stream()
            .anyMatch(d -> "VTLS:2110".equals(d.code().category() + ":" + d.code().id())));

    // Missing closing paren
    VtlParseResult r3 =
        VtlParser.parse(
            SourceText.from("#param(Employee $e", TemplateId.of("unclosed_paren_test")));
    assertTrue(r3.hasErrors());
    assertTrue(
        r3.diagnostics().stream()
            .anyMatch(d -> "VTLS:2110".equals(d.code().category() + ":" + d.code().id())));
  }
}
