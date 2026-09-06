package io.github.minh124199.viettemplate.language.vtl.semantics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VtlSemanticAnalyzerTest {

  public record User(String name, int age, boolean active) {}

  public record Item(String title, double price) {}

  public record Catalog(List<Item> items) {}

  private static VtlParseResult parse(String content) {
    SourceText source = SourceText.from(content, TemplateId.of("test.vm"));
    return VtlParser.parse(source);
  }

  @Test
  @DisplayName("Typed template with valid properties analyzes cleanly and is AOT eligible")
  void typedTemplateSuccess() {
    VtlParseResult parsed = parse("Hello $user.name! Age: $user.age");
    assertThat(parsed.hasErrors()).isFalse();

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.capabilities().eligibleForStaticAot()).isTrue();
    assertThat(result.capabilities().usesUnknownModelTypes()).isFalse();
    assertThat(result.capabilities().requiresDynamicMemberResolution()).isFalse();
    assertThat(result.capabilities().requiresArbitraryMethodCalls()).isFalse();
  }

  @Test
  @DisplayName("Property typo produces VTLS2104 diagnostic with 'did you mean' suggestion")
  void propertyTypoSuggestion() {
    VtlParseResult parsed = parse("Hello $user.nmae!");
    assertThat(parsed.hasErrors()).isFalse();

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).hasSize(1);

    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.PROPERTY_NOT_FOUND);
    assertThat(diag.message())
        .contains("Property 'nmae' does not exist on type")
        .contains("Did you mean 'name'?");
    assertThat(result.capabilities().eligibleForStaticAot()).isFalse();
  }

  @Test
  @DisplayName("Root reference typo produces VTLS2101 diagnostic with suggestion")
  void rootModelTypoSuggestion() {
    VtlParseResult parsed = parse("Hello $usr.name!");

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.UNRESOLVED_ROOT);
    assertThat(diag.message())
        .contains("Root reference '$usr' is not declared")
        .contains("Did you mean '$user'?");
  }

  @Test
  @DisplayName("Method calls are denied with VTLSEC2401 under safe profile")
  void methodCallsDeniedInSafeProfile() {
    VtlParseResult parsed = parse("$user.name.trim()");

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_SAFE).modelSchema(schema).build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.SECURITY_DENIED);
    assertThat(diag.message()).contains("Method calls are disabled by VTL_SAFE policy");
    assertThat(result.capabilities().requiresArbitraryMethodCalls()).isTrue();
  }

  @Test
  @DisplayName("Security policy blocks dangerous reflection methods")
  void securityPolicyBlocksReflection() {
    VtlParseResult parsed = parse("$user.getClass()");

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder().profile(VtlProfile.VTL_DYNAMIC).modelSchema(schema).build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.SECURITY_DENIED);
    assertThat(diag.message()).contains("getClass");
  }

  @Test
  @DisplayName("#evaluate is rejected outside dynamic profile")
  void evaluateRejectedInCoreProfile() {
    VtlParseResult parsed = parse("#evaluate('1 + 1')");

    VtlSemanticOptions options = VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.SECURITY_DENIED);
    assertThat(diag.message()).contains("#evaluate directive is not permitted in profile VTL_CORE");
    assertThat(result.capabilities().requiresRuntimeEvaluation()).isTrue();
  }

  @Test
  @DisplayName("#foreach scopes loop variable and exposes $foreach metadata")
  void foreachLoopVariableAndMetadata() {
    VtlParseResult parsed =
        parse("#foreach($item in $catalog.items)$item.title $foreach.index $foreach.hasNext#end");

    ModelSchema schema =
        ModelSchema.builder()
            .add("catalog", VType.ClassType.of(Catalog.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.capabilities().eligibleForStaticAot()).isTrue();
  }

  @Test
  @DisplayName("Non-iterable in #foreach emits VTLS2106")
  void nonIterableInForeach() {
    VtlParseResult parsed = parse("#foreach($item in $user.age)$item#end");

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.INVALID_ITERABLE);
  }

  @Test
  @DisplayName("Root model mutation in strict mode emits VTLS2102")
  void rootModelMutationFailsInStrict() {
    VtlParseResult parsed = parse("#set($user = 'newUser')");

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isTrue();
    var diag = result.diagnostics().get(0);
    assertThat(diag.code()).isEqualTo(VtlSemanticDiagnosticCodes.INVALID_ASSIGNMENT);
    assertThat(diag.message()).contains("Cannot mutate declared root model parameter '$user'");
  }

  @Test
  @DisplayName("Quiet references flag accessesRawUnescapedOutput")
  void quietReferenceCapabilities() {
    VtlParseResult parsed = parse("$!user.name");

    ModelSchema schema =
        ModelSchema.builder()
            .add("user", VType.ClassType.of(User.class, Nullability.NON_NULL))
            .build();

    VtlSemanticOptions options =
        VtlSemanticOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .modelSchema(schema)
            .strictMode(true)
            .build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.hasErrors()).isFalse();
    assertThat(result.capabilities().accessesRawUnescapedOutput()).isTrue();
    assertThat(result.capabilities().eligibleForStaticAot()).isTrue();
  }

  @Test
  @DisplayName("Dynamic #parse flag requiresDynamicIncludeParse")
  void dynamicParseCapabilities() {
    VtlParseResult parsed = parse("#parse($dynamicPath)");

    VtlSemanticOptions options = VtlSemanticOptions.builder().profile(VtlProfile.VTL_CORE).build();

    SemanticAnalysisResult result = VtlSemanticAnalyzer.analyze(parsed.template(), options);

    assertThat(result.capabilities().requiresDynamicIncludeParse()).isTrue();
    assertThat(result.capabilities().eligibleForStaticAot()).isFalse();
  }
}
