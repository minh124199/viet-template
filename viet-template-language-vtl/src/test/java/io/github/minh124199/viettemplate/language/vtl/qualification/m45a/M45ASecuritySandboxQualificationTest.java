package io.github.minh124199.viettemplate.language.vtl.qualification.m45a;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
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
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class M45ASecuritySandboxQualificationTest {

  private static final Map<String, Class<?>> KNOWN_CLASSES =
      Map.of(
          "Employee", Employee.class,
          "Manager", Manager.class,
          "Department", Department.class,
          "Address", Address.class,
          "PayrollRecord", PayrollRecord.class);

  private static SourceText src(String content) {
    return SourceText.from(content, TemplateId.of("security_qualification.vtl"));
  }

  private static SemanticAnalysisResult analyzeWithPolicy(
      String templateText, MemberAccessPolicy policy) {
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
            .memberAccessPolicy(policy)
            .build();

    return VtlSemanticAnalyzer.analyze(parseResult.template(), options);
  }

  @Test
  @DisplayName("Security sandbox: Accessing $employee.class emits VTLSEC:2401")
  void accessingClassPropertyEmitsSecurityDenied() {
    String template =
        """
        #param(Employee $employee)
        Class is: $employee.class
        """;
    SemanticAnalysisResult result = analyzeWithPolicy(template, MemberAccessPolicy.standard());

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLSEC:2401")
                    && d.severity() == DiagnosticSeverity.ERROR);
  }

  @Test
  @DisplayName("Security sandbox: Invoking $employee.getClass() emits VTLSEC:2401")
  void invokingGetClassEmitsSecurityDenied() {
    String template =
        """
        #param(Employee $employee)
        Class is: $employee.getClass()
        """;
    SemanticAnalysisResult result = analyzeWithPolicy(template, MemberAccessPolicy.standard());

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLSEC:2401")
                    && d.severity() == DiagnosticSeverity.ERROR);
  }

  @Test
  @DisplayName("Security sandbox: Invoking $employee.wait() emits VTLSEC:2401")
  void invokingWaitEmitsSecurityDenied() {
    String template =
        """
        #param(Employee $employee)
        $employee.wait()
        """;
    SemanticAnalysisResult result = analyzeWithPolicy(template, MemberAccessPolicy.standard());

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLSEC:2401")
                    && d.severity() == DiagnosticSeverity.ERROR);
  }

  @Test
  @DisplayName("Security sandbox: Custom MemberAccessPolicy denying secretPin emits VTLSEC:2401")
  void customPolicyDenyingSecretPinEmitsSecurityDenied() {
    MemberAccessPolicy restrictedPolicy =
        MemberAccessPolicy.builder().denyMethod("getSecretPin").build();

    String template =
        """
        #param(Employee $employee)
        Pin: $employee.secretPin
        """;
    SemanticAnalysisResult result = analyzeWithPolicy(template, restrictedPolicy);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLSEC:2401")
                    && d.severity() == DiagnosticSeverity.ERROR);
  }

  @Test
  @DisplayName(
      "Security sandbox: Permitted access under standard policy succeeds without diagnostics")
  void permittedAccessSucceedsUnderStandardPolicy() {
    String template =
        """
        #param(Employee $employee)
        Hello $employee.name!
        """;
    SemanticAnalysisResult result = analyzeWithPolicy(template, MemberAccessPolicy.standard());

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Security sandbox: Denied member is not leaked in typo suggestions")
  void deniedMemberNotLeakedInTypoSuggestion() {
    // If a user types $employee.clas, typo suggestion must not suggest 'class' or leak security
    // denied members
    String template =
        """
        #param(Employee $employee)
        $employee.clas
        """;
    SemanticAnalysisResult result = analyzeWithPolicy(template, MemberAccessPolicy.standard());

    assertThat(result.hasErrors()).isTrue();
    for (var diag : result.diagnostics()) {
      if (diag.message().contains("Did you mean")) {
        assertThat(diag.message()).doesNotContain("class");
      }
    }
  }

  @Test
  @DisplayName(
      "Security sandbox: Standard MemberAccessPolicy enforces strict class and method denials")
  void standardPolicyEnforcesStrictClassAndMethodDenials() throws NoSuchMethodException {
    MemberAccessPolicy policy = MemberAccessPolicy.standard();
    Method getClassMethod = Object.class.getMethod("getClass");
    Method waitMethod = Object.class.getMethod("wait");

    // MemberAccessPolicy.standard() must reject getClass and wait
    assertThat(policy.isMethodPermitted(Employee.class, getClassMethod)).isFalse();
    assertThat(policy.isMethodPermitted(Employee.class, waitMethod)).isFalse();

    // MemberAccessPolicy.standard() must permit normal getters
    Method getNameMethod = Employee.class.getMethod("getName");
    assertThat(policy.isMethodPermitted(Employee.class, getNameMethod)).isTrue();

    // High-risk classes are not permitted
    assertThat(policy.isClassPermitted(ClassLoader.class)).isFalse();
    assertThat(policy.isClassPermitted(Thread.class)).isFalse();
    assertThat(policy.isClassPermitted(Class.class)).isFalse();
  }
}
