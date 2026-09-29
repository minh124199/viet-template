package io.github.minh124199.viettemplate.language.vtl.semantics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StrictTypeCheckingSemanticTest {

  public record TestUserRecord(String name, int age) {}

  public static class TestUser {
    private final String name;

    public TestUser(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public int compute(int a, int b) {
      return a + b;
    }

    public String format(String s) {
      return "str:" + s;
    }

    public String format(int i) {
      return "int:" + i;
    }
  }

  private static VtlParseResult parse(String content) {
    SourceText source = SourceText.from(content, TemplateId.of("test.vm"));
    return VtlParser.parse(
        source,
        io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions.DEFAULT
            .withAllowBareNullLiteral(true));
  }

  @Test
  @DisplayName("TypeCheckingMode.OFF permits dynamic fallback without diagnostic warnings/errors")
  void typeCheckingOffPermitsDynamicFallback() {
    VtlParseResult parsed = parse("$user.nonExistentProp");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.OFF)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.capabilities().requiresDynamicMemberResolution()).isTrue();
  }

  @Test
  @DisplayName("TypeCheckingMode.WARN emits warning and allows compilation to proceed")
  void typeCheckingWarnEmitsWarning() {
    VtlParseResult parsed = parse("$user.nonExistentProp");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.WARNING);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND);
    assertThat(result.capabilities().requiresDynamicMemberResolution()).isTrue();
  }

  @Test
  @DisplayName("TypeCheckingMode.ERROR emits error and halts with hasErrors=true")
  void typeCheckingErrorEmitsError() {
    VtlParseResult parsed = parse("$user.nonExistentProp");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "Nullable reference dereference emits advisory VTLS2103 warning under WARN and ERROR")
  void nullableDereferenceAdvisoryWarning() {
    VtlParseResult parsed = parse("$user.name");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NULLABLE)));

    // Under WARN:
    VtlSemanticOptions warnOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();
    SemanticAnalysisResult warnResult = VtlSemanticAnalyzer.analyze(parsed.template(), warnOptions);
    assertThat(warnResult.hasErrors()).isFalse();
    assertThat(warnResult.diagnostics()).hasSize(1);
    assertThat(warnResult.diagnostics().get(0).severity()).isEqualTo(DiagnosticSeverity.WARNING);
    assertThat(warnResult.diagnostics().get(0).code())
        .isEqualTo(VtlSemanticDiagnosticCodes.TYPE_MISMATCH);
    assertThat(warnResult.diagnostics().get(0).message())
        .contains("Dereference of nullable target '$user'");

    // Under ERROR: advisory warning remains WARNING (does not fail compilation)
    VtlSemanticOptions errOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();
    SemanticAnalysisResult errResult = VtlSemanticAnalyzer.analyze(parsed.template(), errOptions);
    assertThat(errResult.hasErrors()).isFalse();
    assertThat(errResult.diagnostics()).hasSize(1);
    assertThat(errResult.diagnostics().get(0).severity()).isEqualTo(DiagnosticSeverity.WARNING);

    // Under OFF: suppressed
    VtlSemanticOptions offOptions =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.OFF)
            .build();
    SemanticAnalysisResult offResult = VtlSemanticAnalyzer.analyze(parsed.template(), offOptions);
    assertThat(offResult.hasErrors()).isFalse();
    assertThat(offResult.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Quiet reference suppresses nullable dereference warning")
  void quietReferenceSuppressesNullableWarning() {
    VtlParseResult parsed = parse("$!user.name");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NULLABLE)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Flow refinement via #if($user) refines nullability and suppresses warning")
  void flowRefinementSuppressesNullableWarning() {
    VtlParseResult parsed = parse("#if($user)$user.name#end");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NULLABLE)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Flow refinement via #if($user != null) refines nullability")
  void flowRefinementWithNotNullComparison() {
    VtlParseResult parsed = parse("#if($user != null)$user.name#end");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NULLABLE)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Flow refinement in else branch with #if(!$user)")
  void flowRefinementInElseBranch() {
    VtlParseResult parsed = parse("#if(!$user)None#else$user.name#end");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NULLABLE)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Method arity mismatch emits VTLS2105")
  void methodArityMismatch() {
    VtlParseResult parsed = parse("$user.compute(1)");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.METHOD_NOT_FOUND);
    assertThat(diag.message()).contains("expects [2] argument(s), but was called with 1");
  }

  @Test
  @DisplayName("Method incompatible arguments emits VTLS2103")
  void methodIncompatibleArguments() {
    VtlParseResult parsed = parse("$user.compute('string', true)");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.TYPE_MISMATCH);
    assertThat(diag.message()).contains("cannot be called with argument types");
  }

  @Test
  @DisplayName("Ambiguous method overload with dynamic argument falls back safely without error")
  void overloadAmbiguityFallsBackToDynamic() {
    VtlParseResult parsed = parse("$user.format($dyn)");
    ModelSchema schema =
        ModelSchema.of(
            Map.of(
                "user",
                VType.ClassType.of(TestUser.class, Nullability.NON_NULL),
                "dyn",
                VTypes.DYNAMIC));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Array length and size pseudo-properties resolve to int cleanly")
  void arrayPseudoProperties() {
    VtlParseResult parsed = parse("$items.length $items.size");
    ModelSchema schema =
        ModelSchema.of(
            Map.of(
                "items",
                new VType.ArrayType(
                    VType.ClassType.of(String.class, Nullability.NON_NULL), Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Indexing with non-integer on list emits VTLS2103 under ERROR")
  void listNonIntegerIndex() {
    VtlParseResult parsed = parse("$list['invalid']");
    ModelSchema schema =
        ModelSchema.of(
            Map.of(
                "list",
                VType.ClassType.of(
                    List.class,
                    List.of(VType.ClassType.of(String.class, Nullability.NON_NULL)),
                    Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.TYPE_MISMATCH);
    assertThat(diag.message()).contains("List index must be an integer");
  }

  @Test
  @DisplayName("Indexing on unsupported type emits VTLS2103 under ERROR")
  void unsupportedTypeIndexing() {
    VtlParseResult parsed = parse("$user[0]");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.TYPE_MISMATCH);
    assertThat(diag.message()).contains("does not support indexing");
  }

  @Test
  @DisplayName(
      "#foreach loop variable infers element type and allows valid property access cleanly")
  void loopPropagationValidClean() {
    VtlParseResult parsed = parse("#foreach($u in $users)$u.name#end");
    ModelSchema schema =
        ModelSchema.of(
            Map.of(
                "users",
                VType.ClassType.of(
                    List.class,
                    List.of(VType.ClassType.of(TestUser.class, Nullability.NON_NULL)),
                    Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("#foreach loop variable diagnoses property typo on inferred element type")
  void loopPropagationTypoDiagnosed() {
    VtlParseResult parsed = parse("#foreach($u in $users)$u.naem#end");
    ModelSchema schema =
        ModelSchema.of(
            Map.of(
                "users",
                VType.ClassType.of(
                    List.class,
                    List.of(VType.ClassType.of(TestUser.class, Nullability.NON_NULL)),
                    Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND);
    assertThat(diag.message()).contains("Property 'naem' does not exist on type");
    assertThat(diag.message()).contains("Did you mean 'name'?");
  }

  @Test
  @DisplayName("Security denied property access emits VTLSEC:2401 instead of PROPERTY_NOT_FOUND")
  void securityDeniedPropertyAccess() {
    VtlParseResult parsed = parse("$user.class");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.SECURITY_DENIED);
  }

  @Test
  @DisplayName("Upstream error in method argument suppresses secondary cascade diagnostics")
  void upstreamErrorSuppressesCascade() {
    VtlParseResult parsed = parse("$user.compute($user.nonExistentProp, 1)");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    // Only the property-not-found diagnostic should be present, not secondary incompatible-args
    assertThat(result.diagnostics()).hasSize(1);
    assertThat(result.diagnostics().get(0).code())
        .isEqualTo(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND);
  }

  @Test
  @DisplayName("Incompatible argument diagnostic points at argument expression span")
  void incompatibleArgumentPointsAtArgumentExpression() {
    String content = "$user.compute('bad', 1)";
    VtlParseResult parsed = parse(content);
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.TYPE_MISMATCH);
    assertThat(content.substring(diag.primarySpan().startOffset(), diag.primarySpan().endOffset()))
        .isEqualTo("'bad'");
  }

  @Test
  @DisplayName("Compound condition flow refinement refines nullability in then branch")
  void compoundConditionFlowRefinement() {
    VtlParseResult parsed = parse("#if($user != null && $user.name != null)$user.name#end");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NULLABLE)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Valid chained property and method on non-null root does not warn")
  void validChainedPropertyOnNonNullRootDoesNotWarn() {
    VtlParseResult parsed = parse("$user.name.length()");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Unresolved root parameter under OFF mode permits dynamic fallback")
  void unresolvedRootOffPermitsDynamicFallback() {
    VtlParseResult parsed = parse("$unknown");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.OFF)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Unresolved root parameter under WARN mode emits VTLS2101 warning")
  void unresolvedRootWarnEmitsWarning() {
    VtlParseResult parsed = parse("$unknown");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.WARNING);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.UNRESOLVED_ROOT);
    assertThat(diag.message())
        .contains("Root reference '$unknown' is not declared in the model schema");
  }

  @Test
  @DisplayName("Unresolved root parameter under ERROR mode emits VTLS2101 error and halts")
  void unresolvedRootErrorEmitsError() {
    VtlParseResult parsed = parse("$unknown");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.severity()).isEqualTo(DiagnosticSeverity.ERROR);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.UNRESOLVED_ROOT);
  }

  @Test
  @DisplayName("Unresolved root parameter with empty schema remains dynamic under ERROR mode")
  void unresolvedRootWithEmptySchemaRemainsDynamic() {
    VtlParseResult parsed = parse("$unknown");
    ModelSchema schema = ModelSchema.empty();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Record properties resolve valid component and diagnose typo under ERROR mode")
  void recordPropertyValidAndTypoDiagnosed() {
    ModelSchema schema =
        ModelSchema.of(
            Map.of("user", VType.ClassType.of(TestUserRecord.class, Nullability.NON_NULL)));
    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    // Valid record property
    VtlParseResult validParsed = parse("$user.name");
    SemanticAnalysisResult validResult =
        VtlSemanticAnalyzer.analyze(validParsed.template(), options);
    assertThat(validResult.hasErrors()).isFalse();
    assertThat(validResult.diagnostics()).isEmpty();

    // Typo record property
    VtlParseResult typoParsed = parse("$user.naem");
    SemanticAnalysisResult typoResult = VtlSemanticAnalyzer.analyze(typoParsed.template(), options);
    assertThat(typoResult.hasErrors()).isTrue();
    assertThat(typoResult.diagnostics()).hasSize(1);
    var diag = typoResult.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND);
    assertThat(diag.message()).contains("Did you mean 'name'?");
  }

  @Test
  @DisplayName("Map dynamic property access remains dynamic without false property-not-found error")
  void mapDynamicPropertyAccessRemainsClean() {
    VtlParseResult parsed = parse("$map.anyDynamicKey");
    ModelSchema schema =
        ModelSchema.of(Map.of("map", VType.ClassType.of(Map.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Macro parameter scope remains dynamic without emitting false diagnostics")
  void macroParameterScopeRemainsDynamic() {
    VtlParseResult parsed = parse("#macro(card $item)$item.unknownProp#end");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Root model parameter mutation via #set emits VTLS2102 under ERROR mode")
  void rootModelParameterMutationDiagnosed() {
    VtlParseResult parsed = parse("#set($user = 'newUser')");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.INVALID_ASSIGNMENT);
    assertThat(diag.message()).contains("Cannot mutate declared root model parameter '$user'");
  }

  @Test
  @DisplayName("Primitive widening argument is accepted without error under ERROR mode")
  void primitiveWideningArgumentAccepted() {
    VtlParseResult parsed = parse("$user.compute(1, $val)");
    ModelSchema schema =
        ModelSchema.of(
            Map.of(
                "user",
                VType.ClassType.of(TestUser.class, Nullability.NON_NULL),
                "val",
                VTypes.SHORT));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  @DisplayName("Primitive incompatible narrowing argument emits VTLS2103 error under ERROR mode")
  void primitiveIncompatibleNarrowingDiagnosed() {
    VtlParseResult parsed = parse("$user.compute(1, 1.5)");
    ModelSchema schema =
        ModelSchema.of(Map.of("user", VType.ClassType.of(TestUser.class, Nullability.NON_NULL)));

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_DYNAMIC)
            .modelSchema(schema)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.TYPE_MISMATCH);
    assertThat(diag.message()).contains("cannot be called with argument types");
  }
}
