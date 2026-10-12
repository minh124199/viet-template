package io.github.minh124199.viettemplate.language.vtl.qualification.m45a;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Address;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Department;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Employee;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.Manager;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.model.PayrollRecord;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.CandidateSyntax;
import io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser.ParamDeclarationPrototypeParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class M45ADiagnosticsQualificationTest {

  private static final Map<String, Class<?>> KNOWN_CLASSES =
      Map.of(
          "Employee", Employee.class,
          "Manager", Manager.class,
          "Department", Department.class,
          "Address", Address.class,
          "PayrollRecord", PayrollRecord.class);

  private static SourceText src(String content) {
    return SourceText.from(content, TemplateId.of("diagnostics_qualification.vtl"));
  }

  private static SemanticAnalysisResult analyze(String templateText) {
    SourceText source = src(templateText);
    var paramParse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    ModelSchema schema =
        ParamDeclarationPrototypeParser.toModelSchema(paramParse.declarations(), KNOWN_CLASSES);

    VtlParseResult parseResult = VtlParser.parse(source);

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .allowArbitraryMethods(true)
            .build();

    return VtlSemanticAnalyzer.analyze(parseResult.template(), options);
  }

  @Test
  @DisplayName("Diagnostics: Property not found diagnostic points to exact property span")
  void exactPropertySpan() {
    String text =
        """
        #param(Employee $employee)
        User name is $employee.naem.
        """;
    SemanticAnalysisResult result = analyze(text);

    assertThat(result.hasErrors()).isTrue();
    var diags =
        result.diagnostics().stream()
            .filter(d -> d.code().qualifiedCode().equals("VTLS:2104"))
            .toList();
    assertThat(diags).hasSize(1);

    Diagnostic d = diags.get(0);
    String errorSnippet =
        text.substring(d.primarySpan().startOffset(), d.primarySpan().endOffset());
    assertThat(errorSnippet).isEqualTo(".naem");
  }

  @Test
  @DisplayName("Diagnostics: Levenshtein distance provides accurate typo suggestions")
  void levenshteinTypoSuggestions() {
    String text =
        """
        #param(Employee $employee)
        $employee.naem
        """;
    SemanticAnalysisResult result = analyze(text);

    var d =
        result.diagnostics().stream()
            .filter(d0 -> d0.code().qualifiedCode().equals("VTLS:2104"))
            .findFirst()
            .orElseThrow();
    assertThat(d.message()).contains("Did you mean 'name'?");
  }

  @Test
  @DisplayName(
      "Diagnostics: Method not found diagnostic points to method call span with typo suggestion")
  void exactMethodSpanAndTypo() {
    String text =
        """
        #param(Employee $employee)
        $employee.frmat("test")
        """;
    SemanticAnalysisResult result = analyze(text);

    assertThat(result.hasErrors()).isTrue();
    var diags =
        result.diagnostics().stream()
            .filter(d -> d.code().qualifiedCode().equals("VTLS:2105"))
            .toList();
    assertThat(diags).hasSize(1);
    Diagnostic d = diags.get(0);
    assertThat(d.message()).contains("format");
  }

  @Test
  @DisplayName("Diagnostics: Cascade suppression on deep chained member access")
  void chainDereferenceCascadeSuppression() {
    String text =
        """
        #param(Employee $employee)
        $employee.unknownProp.nested.deep.leaf
        """;
    SemanticAnalysisResult result = analyze(text);

    assertThat(result.hasErrors()).isTrue();
    // Only 1 error for unknownProp, no secondary cascade errors for .nested, .deep, .leaf
    var errorDiags =
        result.diagnostics().stream()
            .filter(
                d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.ERROR)
            .toList();
    assertThat(errorDiags).hasSize(1);
    assertThat(errorDiags.get(0).code().qualifiedCode()).isEqualTo("VTLS:2104");
    assertThat(errorDiags.get(0).message()).contains("unknownProp");
  }

  @Test
  @DisplayName("Diagnostics: Cascade suppression on #set local variable assignment")
  void variableAssignmentCascadeSuppression() {
    String text =
        """
        #param(Employee $employee)
        #set($bad = $employee.unknownProp)
        $bad.foo.bar
        """;
    SemanticAnalysisResult result = analyze(text);

    assertThat(result.hasErrors()).isTrue();
    var errorDiags =
        result.diagnostics().stream()
            .filter(
                d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.ERROR)
            .toList();
    assertThat(errorDiags).hasSize(1);
    assertThat(errorDiags.get(0).code().qualifiedCode()).isEqualTo("VTLS:2104");
  }

  @Test
  @DisplayName("Diagnostics: Parameter declaration error points to exact duplicate variable span")
  void parameterDeclarationSpan() {
    String text =
        """
        #param(String $dup)
        #param(int $dup)
        """;
    SourceText source = src(text);
    var parse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    assertThat(parse.diagnostics()).hasSize(1);
    var d = parse.diagnostics().get(0);
    assertThat(d.code().qualifiedCode()).isEqualTo("VTLS:2108");
    String snippet = text.substring(d.primarySpan().startOffset(), d.primarySpan().endOffset());
    assertThat(snippet).isEqualTo("$dup");
  }
}
