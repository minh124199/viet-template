package io.github.minh124199.viettemplate.language.vtl.qualification.m45a;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticCode;
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

class M45ATypePropagationQualificationTest {

  private static final Map<String, Class<?>> KNOWN_CLASSES =
      Map.of(
          "Employee", Employee.class,
          "Manager", Manager.class,
          "Department", Department.class,
          "Address", Address.class,
          "PayrollRecord", PayrollRecord.class);

  private static SourceText src(String content) {
    return SourceText.from(content, TemplateId.of("type_propagation.vtl"));
  }

  private static SemanticAnalysisResult analyzeWithParams(String templateText) {
    SourceText source = src(templateText);

    // 1. Parse parameters using prototype parser
    var paramParse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    ModelSchema schema =
        ParamDeclarationPrototypeParser.toModelSchema(paramParse.declarations(), KNOWN_CLASSES);

    // 2. Parse AST using VtlParser
    VtlParseResult parseResult = VtlParser.parse(source);

    // 3. Analyze using VtlSemanticAnalyzer with strict type checking
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
  @DisplayName("Type propagation: Direct property access on declared #param(Employee $employee)")
  void directPropertyAccess() {
    String template =
        """
        #param(Employee $employee)
        Name: $employee.name, ID: $employee.id, Active: $employee.active
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.capabilities().eligibleForStaticAot()).isTrue();
  }

  @Test
  @DisplayName("Type propagation: Nested property access $employee.address.city")
  void nestedPropertyAccess() {
    String template =
        """
        #param(Employee $employee)
        City: $employee.address.city, Zip: $employee.address.zipCode
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Type propagation: Inheritance and covariant specialization on Manager")
  void inheritancePropertyAccess() {
    String template =
        """
        #param(Manager $manager)
        Manager: $manager.name (Reports: $manager.directReportsCount, Dept: $manager.departmentName)
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Type propagation: Collection element type inference in #foreach")
  void foreachCollectionElementInference() {
    String template =
        """
        #param(Employee $employee)
        #foreach($skill in $employee.skills)
          Skill: $skill.length()
        #end
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    // In strict mode, dereferencing nullable collection element emits advisory VTLS:2107 warning
    assertThat(result.diagnostics())
        .allMatch(
            d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.WARNING);
  }

  @Test
  @DisplayName("Type propagation: Foreach element typo detection on inferred element type")
  void foreachElementTypoDetection() {
    String template =
        """
        #param(Employee $employee)
        #foreach($skill in $employee.skills)
          Skill: $skill.nonExistentMethod()
        #end
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isTrue();
    Diagnostic errorDiag =
        result.diagnostics().stream()
            .filter(
                d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.ERROR)
            .findFirst()
            .orElseThrow();
    assertThat(errorDiag.code()).isEqualTo(DiagnosticCode.of("VTLS", "2105"));
    assertThat(errorDiag.message()).contains("nonExistentMethod").contains("java.lang.String");
  }

  @Test
  @DisplayName("Type propagation: Map value type inference in #foreach")
  void foreachMapValueInference() {
    String template =
        """
        #param(Employee $employee)
        #foreach($val in $employee.attributes)
          Attr value: $val.toUpperCase()
        #end
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics())
        .allMatch(
            d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.WARNING);
  }

  @Test
  @DisplayName("Type propagation: Local variable type flow via #set($addr = $employee.address)")
  void setAssignmentTypeFlow() {
    String template =
        """
        #param(Employee $employee)
        #set($addr = $employee.address)
        $addr.street, $addr.city
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Method overload resolution: Distinguishes format(String) and format(int)")
  void methodOverloadResolution() {
    String template =
        """
        #param(Employee $employee)
        $employee.format('Senior')
        $employee.format(101)
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName(
      "Dynamic fallback: Open template model keeps undeclared receivers dynamic with"
          + " TypeCheckingMode.ERROR")
  void dynamicFallbackForUndeclared() {
    String template =
        """
        #param(Employee $employee)
        Employee name: $employee.name
        Dynamic item: $dynamicModel.someProperty
        """;
    SourceText source = src(template);
    var paramParse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);

    // In M45B's open parameter model, declared parameters have static types,
    // while undeclared root variables resolve to VTypes.DYNAMIC.
    Map<String, io.github.minh124199.viettemplate.language.vtl.semantics.type.VType> openParams =
        new java.util.LinkedHashMap<>();
    for (var decl : paramParse.declarations()) {
      openParams.put(
          decl.name(),
          io.github.minh124199.viettemplate.language.vtl.semantics.type.VType.ClassType.of(
              KNOWN_CLASSES.get(decl.typeText()),
              io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability.NON_NULL));
    }
    openParams.put(
        "dynamicModel",
        io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes.DYNAMIC);
    ModelSchema openSchema = ModelSchema.of(openParams);

    VtlParseResult parseResult = VtlParser.parse(source);

    // Strict TypeCheckingMode.ERROR still enabled for declared properties
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(openSchema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .allowArbitraryMethods(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.capabilities().requiresDynamicMemberResolution()).isTrue();
  }

  @Test
  @DisplayName(
      "Dynamic fallback: Closed schema contract emits VTLS:2101 when undeclared root is"
          + " encountered")
  void closedSchemaRejectsUndeclaredRoots() {
    String template =
        """
        #param(Employee $employee)
        Employee name: $employee.name
        Undeclared: $unknownVar.prop
        """;
    SourceText source = src(template);
    var paramParse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_A_PAREN_TYPE_VAR);
    ModelSchema closedSchema =
        ParamDeclarationPrototypeParser.toModelSchema(paramParse.declarations(), KNOWN_CLASSES);
    VtlParseResult parseResult = VtlParser.parse(source);

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(closedSchema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .allowArbitraryMethods(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).anyMatch(d -> d.code().qualifiedCode().equals("VTLS:2101"));
  }

  @Test
  @DisplayName("Candidate E: Hybrid schema merges inline declarations overriding companion schema")
  void candidateEHybridSchemaPrecedence() {
    String template =
        """
        #param(Manager $employee)
        Manager direct reports: $employee.directReportsCount
        Department name: $dept.name
        """;
    SourceText source = src(template);
    var paramParse =
        ParamDeclarationPrototypeParser.parse(source, CandidateSyntax.CANDIDATE_E_HYBRID);

    // Baseline companion schema has $employee as Employee (not Manager) and $dept as Department
    ModelSchema companionSchema =
        ModelSchema.of(
            Map.of(
                "employee",
                    io.github.minh124199.viettemplate.language.vtl.semantics.type.VType.ClassType
                        .of(
                            Employee.class,
                            io.github.minh124199.viettemplate.language.vtl.semantics.type
                                .Nullability.NON_NULL),
                "dept",
                    io.github.minh124199.viettemplate.language.vtl.semantics.type.VType.ClassType
                        .of(
                            Department.class,
                            io.github.minh124199.viettemplate.language.vtl.semantics.type
                                .Nullability.NON_NULL)));

    // Hybrid merging: inline Manager overrides companion Employee
    ModelSchema hybridSchema =
        ParamDeclarationPrototypeParser.toHybridModelSchema(
            paramParse.declarations(), companionSchema, KNOWN_CLASSES);

    VtlParseResult parseResult = VtlParser.parse(source);
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(hybridSchema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .allowArbitraryMethods(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parseResult.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Type propagation: Generic wildcards resolve conservatively to upper bound")
  void wildcardUpperBoundPropagation() {
    String template =
        """
        #param(List<? extends Employee> $staff)
        #foreach($emp in $staff)
          Name: $emp.name, ID: $emp.id
        #end
        """;
    SemanticAnalysisResult result = analyzeWithParams(template);
    assertThat(result.hasErrors()).isFalse();
    // advisory warning for nullable foreach element dereference
    assertThat(result.diagnostics())
        .allMatch(
            d -> d.severity() == io.github.minh124199.viettemplate.api.DiagnosticSeverity.WARNING);
  }
}
