package io.github.minh124199.viettemplate.language.vtl.qualification.m45a;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.CandidateSyntax;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.ParamDeclarationPrototypeParser;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.VtlParamDeclaration;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class M45AParameterParserQualificationTest {

  private static SourceText src(String content) {
    return SourceText.from(content, TemplateId.of("qualification.vtl"));
  }

  @Test
  @DisplayName("Candidate A: Parse single typed parameter declaration #param(Type $var)")
  void candidateAParseSingleParam() {
    SourceText source = src("#param(Employee $employee)\nHello $employee.name!");
    var res =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(1);
    VtlParamDeclaration decl = res.declarations().get(0);
    assertThat(decl.name()).isEqualTo("employee");
    assertThat(decl.typeText()).isEqualTo("Employee");
    assertThat(decl.nullable()).isFalse();
    assertThat(decl.defaultValue()).isEmpty();
  }

  @Test
  @DisplayName("Candidate A: Parse multiple #param lines in prologue")
  void candidateAParseMultipleLines() {
    String template =
        """
        ## Template header comment
        #param(Employee $employee)
        #param(Department $dept)
        #param(int $year)

        Welcome to $dept.name, $employee.name!
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(3);
    assertThat(res.declarations().get(0).name()).isEqualTo("employee");
    assertThat(res.declarations().get(1).name()).isEqualTo("dept");
    assertThat(res.declarations().get(2).name()).isEqualTo("year");
    assertThat(res.declarations().get(2).typeText()).isEqualTo("int");
  }

  @Test
  @DisplayName("Candidate A: Parse multiple comma-separated parameters in single #param(...)")
  void candidateAParseCommaSeparatedParams() {
    String template = "#param(Employee $employee, Department $dept, int $count)\nData";
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(3);
    assertThat(res.declarations().get(0).name()).isEqualTo("employee");
    assertThat(res.declarations().get(1).name()).isEqualTo("dept");
    assertThat(res.declarations().get(2).name()).isEqualTo("count");
  }

  @Test
  @DisplayName("Candidate A: Parse complex generic, nested, array, and nullable types")
  void candidateAParseComplexTypes() {
    String template =
        """
        #param(List<String> $roles)
        #param(Map<String, List<Employee>> $teams)
        #param(String[] $tags)
        #param(Manager? $manager)
        #param(int $page = 1)
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(5);

    assertThat(res.declarations().get(0).typeText()).isEqualTo("List<String>");
    assertThat(res.declarations().get(1).typeText()).isEqualTo("Map<String, List<Employee>>");
    assertThat(res.declarations().get(2).typeText()).isEqualTo("String[]");
    assertThat(res.declarations().get(3).typeText()).isEqualTo("Manager");
    assertThat(res.declarations().get(3).nullable()).isTrue();
    assertThat(res.declarations().get(4).name()).isEqualTo("page");
    assertThat(res.declarations().get(4).defaultValue()).contains("1");
  }

  @Test
  @DisplayName("Candidate B: Parse #param($var: Type) syntax")
  void candidateBParseSyntax() {
    String template =
        """
        #param($employee: Employee, $roles: List<String>)
        #param($manager: Manager? = null)
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_B_PAREN_VAR_COLON_TYPE);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(3);
    assertThat(res.declarations().get(0).name()).isEqualTo("employee");
    assertThat(res.declarations().get(0).typeText()).isEqualTo("Employee");
    assertThat(res.declarations().get(1).name()).isEqualTo("roles");
    assertThat(res.declarations().get(1).typeText()).isEqualTo("List<String>");
    assertThat(res.declarations().get(2).name()).isEqualTo("manager");
    assertThat(res.declarations().get(2).nullable()).isTrue();
  }

  @Test
  @DisplayName("Candidate C: Parse unparenthesized #param Type $var")
  void candidateCParseBareSyntax() {
    String template = "#param Employee $employee\nOutput";
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_C_BARE_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(1);
    assertThat(res.declarations().get(0).name()).isEqualTo("employee");
    assertThat(res.declarations().get(0).typeText()).isEqualTo("Employee");
  }

  @Test
  @DisplayName("Candidate F: Parse comment-style declaration #* @param Type $var *#")
  void candidateFParseCommentSyntax() {
    String template =
        """
        #* @param Employee $employee *#
        #* @param List<String> $skills *#
        Hello $employee.name!
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_F_COMMENT_METADATA);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(2);
    assertThat(res.declarations().get(0).name()).isEqualTo("employee");
    assertThat(res.declarations().get(1).name()).isEqualTo("skills");
  }

  @Test
  @DisplayName("Rejects duplicate parameter declaration with VTLS:2108")
  void rejectsDuplicateParameter() {
    String template =
        """
        #param(Employee $employee)
        #param(Department $employee)
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).hasSize(1);
    assertThat(res.diagnostics().get(0).code())
        .isEqualTo(ParamDeclarationPrototypeParser.CODE_DUPLICATE_PARAM);
    assertThat(res.diagnostics().get(0).message())
        .contains("Duplicate parameter declaration for '$employee'");
  }

  @Test
  @DisplayName("Rejects #param directive appearing after body content with VTLS:2111")
  void rejectsParamAfterPrologue() {
    String template =
        """
        #param(Employee $employee)
        <div>Rendered header content</div>
        #param(Department $dept)
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).hasSize(1);
    assertThat(res.diagnostics().get(0).code())
        .isEqualTo(ParamDeclarationPrototypeParser.CODE_PARAM_NOT_IN_PROLOGUE);
    assertThat(res.diagnostics().get(0).message()).contains("must appear in template prologue");
  }

  @Test
  @DisplayName("Rejects malformed syntax: missing '$' on variable name")
  void rejectsMissingDollar() {
    String template = "#param(Employee employee)\n";
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).hasSize(1);
    assertThat(res.diagnostics().get(0).code())
        .isEqualTo(ParamDeclarationPrototypeParser.CODE_INVALID_PARAM_SYNTAX);
    assertThat(res.diagnostics().get(0).message())
        .contains("Expected parameter variable starting with '$'");
  }

  @Test
  @DisplayName("Rejects malformed syntax: unclosed parenthesis")
  void rejectsUnclosedParen() {
    String template = "#param(Employee $employee\nHello";
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics().get(0).code())
        .isEqualTo(ParamDeclarationPrototypeParser.CODE_INVALID_PARAM_SYNTAX);
  }

  @Test
  @DisplayName("Differential parsing: untyped template parses identically with existing VtlParser")
  void differentialParsingUntypedTemplate() {
    String untypedTemplate =
        """
        #set($total = 0)
        #foreach($item in $cart.items)
          #if($item.inStock)
            #set($total = $total + $item.price)
            Item: $item.title - $$item.price
          #end
        #end
        Total: $$total
        """;

    // 1. Existing VTL parser
    VtlParseResult existingResult = VtlParser.parse(src(untypedTemplate));
    assertThat(existingResult.hasErrors()).isFalse();

    // 2. Prototype param parser on untyped template finds 0 declarations and 0 errors
    var protoResult =
        ParamDeclarationPrototypeParser.parse(
            src(untypedTemplate), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    assertThat(protoResult.hasErrors()).isFalse();
    assertThat(protoResult.declarations()).isEmpty();
  }

  @Test
  @DisplayName("Candidate D: Rejects inline #param declarations in companion schema-only mode")
  void candidateDRejectsInlineParamInSchemaOnlyMode() {
    String template = "#param(Employee $employee)\nOutput";
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_D_SCHEMA_ONLY);

    assertThat(res.hasErrors()).isTrue();
    assertThat(res.declarations()).isEmpty();
    assertThat(res.diagnostics().get(0).code())
        .isEqualTo(ParamDeclarationPrototypeParser.CODE_INVALID_PARAM_SYNTAX);
    assertThat(res.diagnostics().get(0).message())
        .contains("disabled in companion schema-only mode");
  }

  @Test
  @DisplayName(
      "Candidate A: Parse default values containing commas within strings and list literals")
  void candidateAParsesDefaultWithCommaInStringAndList() {
    String template =
        """
        #param(String $greeting = 'Hello, World!', List<String> $roles = ['admin', 'user'])
        #param(int $count = (1 + 2))
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(3);
    assertThat(res.declarations().get(0).name()).isEqualTo("greeting");
    assertThat(res.declarations().get(0).defaultValue()).contains("'Hello, World!'");
    assertThat(res.declarations().get(1).name()).isEqualTo("roles");
    assertThat(res.declarations().get(1).defaultValue()).contains("['admin', 'user']");
    assertThat(res.declarations().get(2).name()).isEqualTo("count");
    assertThat(res.declarations().get(2).defaultValue()).contains("(1 + 2)");
  }

  @Test
  @DisplayName("Rejects malformed syntax: empty parameter list #param()")
  void rejectsEmptyParamList() {
    String template = "#param()\nOutput";
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isTrue();
    assertThat(res.diagnostics()).hasSize(1);
    assertThat(res.diagnostics().get(0).code())
        .isEqualTo(ParamDeclarationPrototypeParser.CODE_INVALID_PARAM_SYNTAX);
    assertThat(res.diagnostics().get(0).message()).contains("Empty parameter list");
  }

  @Test
  @DisplayName("Candidate A: Parse complex wildcards and upper/lower bounds in generics")
  void candidateAParsesWildcardAndTypeVariables() {
    String template =
        """
        #param(List<? extends Employee> $staff)
        #param(Map<String, ? extends List<? extends Address>> $geo)
        #param(List<T> $items)
        """;
    var res =
        ParamDeclarationPrototypeParser.parse(
            src(template), CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(res.hasErrors()).isFalse();
    assertThat(res.declarations()).hasSize(3);
    assertThat(res.declarations().get(0).typeText()).isEqualTo("List<? extends Employee>");
    assertThat(res.declarations().get(1).typeText())
        .isEqualTo("Map<String, ? extends List<? extends Address>>");
    assertThat(res.declarations().get(2).typeText()).isEqualTo("List<T>");
  }
}
