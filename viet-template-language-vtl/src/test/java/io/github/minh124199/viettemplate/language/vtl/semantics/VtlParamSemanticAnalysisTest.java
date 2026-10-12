package io.github.minh124199.viettemplate.language.vtl.semantics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlParamSemanticAnalysisTest {

  public record Address(String city, String zip) {}

  public record Department(String name, Address address) {}

  public static class Employee {
    private final String name;
    private final Department department;
    private final List<String> skills;
    private final Employee manager;

    public Employee(String name, Department department, List<String> skills, Employee manager) {
      this.name = name;
      this.department = department;
      this.skills = skills;
      this.manager = manager;
    }

    public String getName() {
      return name;
    }

    public Department getDepartment() {
      return department;
    }

    public List<String> getSkills() {
      return skills;
    }

    public Employee getManager() {
      return manager;
    }

    public boolean isActive() {
      return true;
    }

    public int calculateBonus(int percent) {
      return percent * 10;
    }
  }

  private static final String EMPLOYEE_CLASS = Employee.class.getName().replace('$', '.');

  private static VtlParseResult parse(String content) {
    SourceText source = SourceText.from(content, TemplateId.of("param_semantic_test.vtl"));
    return VtlParser.parse(
        source,
        io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions.DEFAULT
            .withAllowBareNullLiteral(true));
  }

  private static SemanticAnalysisResult analyze(String content) {
    return analyze(content, ModelSchema.empty());
  }

  private static SemanticAnalysisResult analyze(String content, ModelSchema schema) {
    VtlParseResult parsed = parse(content);
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .allowArbitraryMethods(true)
            .build();
    return VtlSemanticAnalyzer.analyze(parsed.template(), options);
  }

  @Test
  @DisplayName("Inline #param: Property access and method calls on declared type")
  void paramDeclarationAndPropertyAccess() {
    String template =
        """
        #param(%s $employee)
        $employee.name
        $employee.active
        $employee.calculateBonus(15)
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Inline #param: Nested property access chains")
  void nestedPropertyPropagation() {
    String template =
        """
        #param(%s $employee)
        $employee.department.name
        $employee.department.address.city
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Inline #param: Property typo triggers VTLS:2104 with suggestion")
  void propertyTypoSuggestion() {
    String template =
        """
        #param(%s $employee)
        $employee.nmae
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code().qualifiedCode()).isEqualTo("VTLS:2104");
    assertThat(diag.message()).contains("'nmae'").contains("Did you mean 'name'?");
  }

  @Test
  @DisplayName("Inline #param: Collection element type propagation in #foreach")
  void collectionInferenceInForeach() {
    String template =
        """
        #param(%s $employee)
        #foreach($skill in $employee.skills)
          $skill.length()
        #end
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    // Element dereference may emit advisory warning if nullable, but no errors
    assertThat(result.diagnostics()).allMatch(d -> d.severity() == DiagnosticSeverity.WARNING);
  }

  @Test
  @DisplayName("Inline #param: Collection element typo in #foreach triggers VTLS:2105")
  void collectionElementTypoInForeach() {
    String template =
        """
        #param(%s $employee)
        #foreach($skill in $employee.skills)
          $skill.nonExistentMethod()
        #end
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).anyMatch(d -> d.code().qualifiedCode().equals("VTLS:2105"));
  }

  @Test
  @DisplayName("Inline #param: Array type element propagation and length property")
  void arrayPropagation() {
    String template =
        """
        #param(int[] $numbers)
        $numbers.length
        #foreach($n in $numbers)
          $n
        #end
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Inline #param: Map type value propagation and indexing")
  void mapPropagation() {
    String template =
        """
        #param(Map<String, String> $config)
        $config["app.title"]
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Inline #param: Unknown declared type triggers VTLS:2109")
  void unknownDeclaredTypeTriggersDiagnostic() {
    String template =
        """
        #param(com.example.NonExistentClass $var)
        $var.something
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLS:2109")
                    && d.message().contains("com.example.NonExistentClass"));
  }

  @Test
  @DisplayName("Inline #param: Incompatible default value triggers VTLS:2103")
  void defaultValueTypeMismatchTriggersDiagnostic() {
    String template =
        """
        #param(int $page = "string_default")
        $page
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLS:2103")
                    && d.message().contains("Default value of type"));
  }

  @Test
  @DisplayName("Inline #param: Compatible default value compiles cleanly")
  void compatibleDefaultValueCompilesCleanly() {
    String template =
        """
        #param(int $page = 1)
        #param(String $title = "Default Title")
        $title: $page
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName(
      "Hybrid precedence: Inline declaration overrides companion schema and emits VTLS:2103 on"
          + " conflict")
  void hybridPrecedenceInlineOverridesSchemaWithConflictWarning() {
    ModelSchema companion = ModelSchema.builder().add("user", VTypes.STRING).build();

    String template =
        """
        #param(int $user)
        $user
        """;
    SemanticAnalysisResult result = analyze(template, companion);

    // Conflict between int and String emits VTLS:2103
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLS:2103")
                    && d.message().contains("conflicts with companion schema type"));

    // Inline wins: $user is resolved as int
    assertThat(result.symbolTable().resolve("user")).isPresent();
    assertThat(result.symbolTable().resolve("user").get().type()).isEqualTo(VTypes.INT);
  }

  @Test
  @DisplayName(
      "Open schema: Inline #param activates open schema, undeclared roots remain dynamic without"
          + " VTLS:2101")
  void openSchemaAllowsUndeclaredRootsDynamically() {
    String template =
        """
        #param(int $count)
        Count: $count
        Other: $undeclaredPlugin.anything.deep
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Closed schema: Undeclared roots trigger VTLS:2101 when no inline #param is present")
  void closedSchemaRejectsUndeclaredRoots() {
    ModelSchema companion = ModelSchema.builder().add("count", VTypes.INT).open(false).build();

    String template =
        """
        Count: $count
        Other: $undeclaredPlugin.anything
        """;
    SemanticAnalysisResult result = analyze(template, companion);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLS:2101")
                    && d.message().contains("$undeclaredPlugin"));
  }

  @Test
  @DisplayName("Security: Access to restricted methods like getClass() triggers VTLSEC:2401")
  void securityDeniedTriggersVTLSEC2401() {
    String template =
        """
        #param(%s $employee)
        $employee.getClass()
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).anyMatch(d -> d.code().qualifiedCode().equals("VTLSEC:2401"));
  }

  @Test
  @DisplayName("Nullable dereference: Emits advisory VTLS:2107 warning for nullable receiver")
  void nullableDereferenceEmitsAdvisoryWarning() {
    String template =
        """
        #param(%s? $employee)
        $employee.name
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().qualifiedCode().equals("VTLS:2107")
                    && d.severity() == DiagnosticSeverity.WARNING);
  }

  @Test
  @DisplayName("Quiet reference: Suppresses VTLS:2107 warning for nullable receiver")
  void quietReferenceSuppressesNullableWarning() {
    String template =
        """
        #param(%s? $employee)
        $!employee.name
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Multi-dimensional primitive array: Propagates component types through index chain")
  void multiDimensionalArraySemanticPropagation() {
    String template =
        """
        #param(int[][] $matrix)
        $matrix[0][1]
        Length: $matrix.length
        """;
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Wildcard bounds: Resolves upper bounds in generics correctly")
  void wildcardBoundsResolution() {
    String template =
        """
        #param(java.util.List<? extends %s> $employees)
        #foreach($emp in $employees)
          $emp.name
        #end
        """
            .formatted(EMPLOYEE_CLASS);
    SemanticAnalysisResult result = analyze(template);

    assertThat(result.hasErrors()).isFalse();
  }
}
