package io.github.minh124199.viettemplate.explanation;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyKind;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TypeCheckingMode;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateExplainerTest {

  @TempDir Path tempDir;

  private TemplateExplainer explainer;

  public record AddressRecord(String city, String zip) {}

  public record UserRecord(
      String name, int primitiveAge, Integer boxedAge, AddressRecord address, List<String> tags) {}

  public static class UserBean {
    private final String name;
    private final int age;

    public UserBean(String name, int age) {
      this.name = name;
      this.age = age;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }
  }

  public static class UserField {
    public String name = "test";
    public int age = 30;
  }

  public static class CalculatorService {
    public int add(int a, int b) {
      return a + b;
    }

    public String greet(String name) {
      return "Hello, " + name;
    }
  }

  public static class ExplosiveBean {
    public String boom() {
      throw new AssertionError("Should never be executed during compiler explanation!");
    }
  }

  @BeforeEach
  void setUp() {
    explainer = TemplateExplainer.create();
  }

  @Test
  void testTypedStringAndIntegerAndPrimitiveInt() throws IOException {
    Path templateFile = tempDir.resolve("user.vtl");
    Files.writeString(templateFile, "$user.name\n$user.primitiveAge\n$user.boxedAge\n");

    TemplateId templateId = TemplateId.of("user.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .typeCheckingMode(TypeCheckingMode.ERROR)
            .build();

    TemplateExplanation explanation = explainer.explain(request);

    assertTrue(explanation.success());
    assertEquals(1, explanation.totalTemplates());
    assertEquals(3, explanation.totalExpressions());

    SingleTemplateExplanation single = explanation.templates().get(0);
    assertTrue(single.typed());
    assertTrue(single.aotEligible());
    assertEquals("AOT_OK", single.compilationStatus());

    List<ExpressionExplanation> exprs = single.expressions();

    // 1. $user.name -> typed String
    ExpressionExplanation nameExpr = exprs.get(0);
    assertEquals("$user.name", nameExpr.sourceText());
    assertEquals("PROPERTY_ACCESS", nameExpr.expressionKind());
    assertEquals("java.lang.String", nameExpr.inferredType());
    assertEquals("EXACT", nameExpr.typeConfidence());
    assertEquals("ROOT_MODEL", nameExpr.symbolOrigin());
    assertEquals(Optional.of("name"), nameExpr.resolvedMember());
    assertEquals("DIRECT_RECORD", nameExpr.resolutionStrategy());
    assertTrue(nameExpr.directAccess());
    assertTrue(nameExpr.aotEligible());
    assertEquals(Optional.of("WRITE_STRING_SPECIALIZED"), nameExpr.outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeString"), nameExpr.outputMethod());
    assertTrue(nameExpr.optimizationRejections().isEmpty());

    // 2. $user.primitiveAge -> primitive int
    ExpressionExplanation primAgeExpr = exprs.get(1);
    assertEquals("$user.primitiveAge", primAgeExpr.sourceText());
    assertEquals("int", primAgeExpr.inferredType());
    assertEquals("NON_NULL", primAgeExpr.nullability());
    assertTrue(primAgeExpr.directAccess());
    assertTrue(primAgeExpr.aotEligible());
    assertEquals(Optional.of("WRITE_INTEGER_SPECIALIZED"), primAgeExpr.outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeInteger"), primAgeExpr.outputMethod());
    assertTrue(primAgeExpr.optimizationRejections().isEmpty());

    // 3. $user.boxedAge -> boxed Integer
    ExpressionExplanation boxedAgeExpr = exprs.get(2);
    assertEquals("$user.boxedAge", boxedAgeExpr.sourceText());
    assertEquals("java.lang.Integer", boxedAgeExpr.inferredType());
    assertTrue(boxedAgeExpr.directAccess());
    assertTrue(boxedAgeExpr.aotEligible());
    assertEquals(Optional.of("WRITE_INTEGER_SPECIALIZED"), boxedAgeExpr.outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeInteger"), boxedAgeExpr.outputMethod());
  }

  @Test
  void testRecordAccessorBeanGetterPublicFieldMethodCall() throws IOException {
    Path templateFile = tempDir.resolve("accessors.vtl");
    Files.writeString(
        templateFile, "$user.name\n$bean.name\n$field.name\n$calc.greet(\"world\")\n");

    TemplateId templateId = TemplateId.of("accessors.vtl");
    TemplateContract contract =
        TemplateContract.builder(templateId)
            .parameter("user", UserRecord.class)
            .parameter("bean", UserBean.class)
            .parameter("field", UserField.class)
            .parameter("calc", CalculatorService.class)
            .build();

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());

    List<ExpressionExplanation> exprs = explanation.templates().get(0).expressions();
    assertEquals(4, exprs.size());

    // Record
    assertEquals("DIRECT_RECORD", exprs.get(0).resolutionStrategy());
    assertTrue(exprs.get(0).directAccess());

    // Bean getter
    assertEquals("DIRECT_GETTER", exprs.get(1).resolutionStrategy());
    assertTrue(exprs.get(1).directAccess());

    // Public field
    assertEquals("DIRECT_FIELD", exprs.get(2).resolutionStrategy());
    assertTrue(exprs.get(2).directAccess());

    // Method call
    assertEquals("METHOD_CALL", exprs.get(3).expressionKind());
    assertEquals("DIRECT_METHOD", exprs.get(3).resolutionStrategy());
    assertEquals(Optional.of("greet"), exprs.get(3).resolvedMember());
    assertTrue(exprs.get(3).directAccess());
  }

  @Test
  void testLocalVariableAndForeachVariable() throws IOException {
    Path templateFile = tempDir.resolve("vars.vtl");
    Files.writeString(
        templateFile,
        "#set($local = $user.name)\n$local\n#foreach($tag in $user.tags)\n$tag\n#end\n");

    TemplateId templateId = TemplateId.of("vars.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());

    List<ExpressionExplanation> exprs = explanation.templates().get(0).expressions();

    ExpressionExplanation localExpr =
        exprs.stream().filter(e -> "$local".equals(e.sourceText())).findFirst().orElseThrow();
    assertEquals("LOCAL", localExpr.symbolOrigin());
    assertEquals("LOCAL_VARIABLE", localExpr.resolutionStrategy());
    assertTrue(localExpr.directAccess());

    ExpressionExplanation loopExpr =
        exprs.stream().filter(e -> "$tag".equals(e.sourceText())).findFirst().orElseThrow();
    assertEquals("LOOP", loopExpr.symbolOrigin());
    assertEquals("LOOP_VARIABLE", loopExpr.resolutionStrategy());
  }

  @Test
  void testDynamicUntypedFallback() throws IOException {
    Path templateFile = tempDir.resolve("dynamic.vtl");
    Files.writeString(templateFile, "$dynamicUser.name\n");

    TemplateExplainRequest request =
        TemplateExplainRequest.builder().sourceDirectory(tempDir).build();

    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());

    SingleTemplateExplanation single = explanation.templates().get(0);
    assertFalse(single.typed());
    assertEquals("AOT_OK_WITH_DYNAMIC_SITES", single.compilationStatus());
    assertTrue(single.aotRejectionReasons().contains("UNTYPED_TEMPLATE"));

    ExpressionExplanation expr = single.expressions().get(0);
    assertEquals("DYNAMIC", expr.resolutionStrategy());
    assertFalse(expr.directAccess());
    assertFalse(expr.aotEligible());
    assertEquals(Optional.of("GENERIC_WRITE_VALUE"), expr.outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeValue"), expr.outputMethod());
    assertTrue(expr.optimizationRejections().contains("UNTYPED_TEMPLATE"));
  }

  @Test
  void testNestedChain() throws IOException {
    Path templateFile = tempDir.resolve("nested.vtl");
    Files.writeString(templateFile, "$user.address.city\n");

    TemplateId templateId = TemplateId.of("nested.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());

    ExpressionExplanation expr = explanation.templates().get(0).expressions().get(0);
    assertEquals("$user.address.city", expr.sourceText());
    assertEquals("java.lang.String", expr.inferredType());
    assertTrue(expr.receiverType().isPresent());
    assertTrue(expr.receiverType().get().contains("AddressRecord"));
    assertEquals(Optional.of("city"), expr.resolvedMember());
    assertEquals("DIRECT_RECORD", expr.resolutionStrategy());
    assertTrue(expr.directAccess());
    assertEquals(Optional.of("WRITE_STRING_SPECIALIZED"), expr.outputDispatch());
  }

  @Test
  void testMissingUnknownProperty() throws IOException {
    Path templateFile = tempDir.resolve("missing.vtl");
    Files.writeString(templateFile, "$user.nonExistent\n");

    TemplateId templateId = TemplateId.of("missing.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .typeCheckingMode(TypeCheckingMode.WARN)
            .build();

    TemplateExplanation explanation = explainer.explain(request);

    ExpressionExplanation expr = explanation.templates().get(0).expressions().get(0);
    assertEquals("NOT_FOUND", expr.resolutionStrategy());
    assertFalse(expr.directAccess());
    assertFalse(expr.aotEligible());
    assertTrue(expr.aotRejectionReasons().contains("PROPERTY_NOT_FOUND"));
    assertEquals(Optional.empty(), expr.resolvedMember());
  }

  @Test
  void testStrictReferenceModeAndSafeProfile() throws IOException {
    Path templateFile = tempDir.resolve("modes.vtl");
    Files.writeString(templateFile, "$user.name\n");

    TemplateId templateId = TemplateId.of("modes.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    // 1. Strict references
    TemplateExplainRequest strictReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .strictReferences(true)
            .build();

    TemplateExplanation strictExpl = explainer.explain(strictReq);
    ExpressionExplanation strictExpr = strictExpl.templates().get(0).expressions().get(0);
    assertEquals(Optional.of("GENERIC_WRITE_VALUE"), strictExpr.outputDispatch());
    assertTrue(
        strictExpr.optimizationRejections().contains("STRICT_REFERENCES_OR_ERROR_NULL_HANDLING"));

    // 2. VTL_SAFE profile
    TemplateExplainRequest safeReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .profile(VtlProfile.VTL_SAFE)
            .build();

    TemplateExplanation safeExpl = explainer.explain(safeReq);
    ExpressionExplanation safeExpr = safeExpl.templates().get(0).expressions().get(0);
    assertEquals(Optional.of("GENERIC_WRITE_VALUE"), safeExpr.outputDispatch());
    assertEquals(Optional.of("HTML_TEXT"), safeExpr.escaping());
    assertTrue(safeExpr.optimizationRejections().contains("VTL_SAFE_PROFILE"));
  }

  @Test
  void testNullModeBehavior() throws IOException {
    Path templateFile = tempDir.resolve("nulls.vtl");
    Files.writeString(templateFile, "$user.name\n");

    TemplateId templateId = TemplateId.of("nulls.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    // Default mode
    TemplateExplainRequest defaultReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();
    TemplateExplanation defExpl = explainer.explain(defaultReq);
    assertEquals("LITERAL_EXPRESSION", defExpl.templates().get(0).nullRenderMode());

    // Strict mode
    TemplateExplainRequest strictReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .strictReferences(true)
            .build();
    TemplateExplanation strictExpl = explainer.explain(strictReq);
    assertEquals("THROW_ERROR", strictExpl.templates().get(0).nullRenderMode());
  }

  @Test
  void testOutputSpecializationVersusGenericWriteValue() throws IOException {
    Path templateFile = tempDir.resolve("dispatch.vtl");
    Files.writeString(templateFile, "$user.name\n$user.primitiveAge\n$user.tags\n");

    TemplateId templateId = TemplateId.of("dispatch.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    TemplateExplanation explanation = explainer.explain(request);
    List<ExpressionExplanation> exprs = explanation.templates().get(0).expressions();

    assertEquals(Optional.of("WRITE_STRING_SPECIALIZED"), exprs.get(0).outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeString"), exprs.get(0).outputMethod());

    assertEquals(Optional.of("WRITE_INTEGER_SPECIALIZED"), exprs.get(1).outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeInteger"), exprs.get(1).outputMethod());

    assertEquals(Optional.of("GENERIC_WRITE_VALUE"), exprs.get(2).outputDispatch());
    assertEquals(Optional.of("BytecodeRuntimeBridge.writeValue"), exprs.get(2).outputMethod());
  }

  @Test
  void testStaticDependencies() throws IOException {
    Path templateFile = tempDir.resolve("main.vtl");
    Files.writeString(templateFile, "#parse('header.vm')\n#include('footer.html')\nHello\n");

    TemplateId templateId = TemplateId.of("main.vtl");
    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .globalMacroLibrary(TemplateId.of("macros.vm"))
            .layoutId(TemplateId.of("layout.vm"))
            .build();

    TemplateExplanation explanation = explainer.explain(request);
    List<TemplateDependency> deps = explanation.templates().get(0).dependencies();
    assertEquals(4, deps.size());
    assertTrue(
        deps.stream()
            .anyMatch(
                d ->
                    "header.vm".equals(d.target().value())
                        && d.kind() == TemplateDependencyKind.STATIC_PARSE));
    assertTrue(
        deps.stream()
            .anyMatch(
                d ->
                    "footer.html".equals(d.target().value())
                        && d.kind() == TemplateDependencyKind.STATIC_INCLUDE));
    assertTrue(
        deps.stream()
            .anyMatch(
                d ->
                    "macros.vm".equals(d.target().value())
                        && d.kind() == TemplateDependencyKind.GLOBAL_MACRO_LIBRARY));
    assertTrue(
        deps.stream()
            .anyMatch(
                d ->
                    "layout.vm".equals(d.target().value())
                        && d.kind() == TemplateDependencyKind.LAYOUT));
  }

  @Test
  void testDeterministicOrderingAndRepeatedRuns() throws IOException {
    Files.writeString(tempDir.resolve("c.vtl"), "$x\n");
    Files.writeString(tempDir.resolve("a.vtl"), "$y\n");
    Files.writeString(tempDir.resolve("b.vtl"), "$z\n");

    TemplateExplainRequest request =
        TemplateExplainRequest.builder().sourceDirectory(tempDir).build();

    TemplateExplanation rep1 = explainer.explain(request);
    TemplateExplanation rep2 = explainer.explain(request);

    assertEquals(3, rep1.totalTemplates());
    assertEquals("a.vtl", rep1.templates().get(0).templateId().value());
    assertEquals("b.vtl", rep1.templates().get(1).templateId().value());
    assertEquals("c.vtl", rep1.templates().get(2).templateId().value());

    assertEquals(rep1.asText(), rep2.asText());
    assertEquals(rep1.asJson(), rep2.asJson());
  }

  @Test
  void testNoRenderingSideEffects() throws IOException {
    Path templateFile = tempDir.resolve("safe.vtl");
    Files.writeString(templateFile, "$explosive.boom()\n");

    TemplateId templateId = TemplateId.of("safe.vtl");
    TemplateContract contract =
        TemplateContract.fromRoot(templateId, "explosive", ExplosiveBean.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    // Must not throw AssertionError during explain
    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());
    assertEquals(1, explanation.totalExpressions());
  }

  @Test
  void testJsonAndTextFormatStability() throws IOException {
    Path templateFile = tempDir.resolve("test.vtl");
    Files.writeString(templateFile, "$user.name\n");

    TemplateId templateId = TemplateId.of("test.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .build();

    TemplateExplanation explanation = explainer.explain(request);

    String text = explanation.asText();
    assertTrue(text.contains("=== Viet Template Compiler Explanation ==="));
    assertTrue(text.contains("Total Templates: 1"));
    assertTrue(text.contains("Total Expressions: 1"));
    assertTrue(text.contains("Output Dispatch: WRITE_STRING_SPECIALIZED"));

    String json = explanation.asJson();
    assertTrue(json.contains("\"formatVersion\": 1"));
    assertTrue(json.contains("\"success\": true"));
    assertTrue(json.contains("\"WRITE_STRING_SPECIALIZED\""));
  }

  @Test
  void testLineAndColumnFiltering() throws IOException {
    Path templateFile = tempDir.resolve("lines.vtl");
    Files.writeString(templateFile, "$user.name\n$user.primitiveAge\n$user.boxedAge\n");

    TemplateId templateId = TemplateId.of("lines.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    // Line 2 filter
    TemplateExplainRequest line2Req =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .line(2)
            .build();

    TemplateExplanation line2Expl = explainer.explain(line2Req);
    assertEquals(1, line2Expl.totalExpressions());
    assertEquals(
        "$user.primitiveAge", line2Expl.templates().get(0).expressions().get(0).sourceText());

    // Line 1, Column 1 filter
    TemplateExplainRequest colReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .line(1)
            .column(1)
            .build();

    TemplateExplanation colExpl = explainer.explain(colReq);
    assertEquals(1, colExpl.totalExpressions());
    assertEquals("$user.name", colExpl.templates().get(0).expressions().get(0).sourceText());
  }

  @Test
  void testMissingSourceDirectory() {
    Path missingDir = tempDir.resolve("does_not_exist");
    TemplateExplainRequest request =
        TemplateExplainRequest.builder().sourceDirectory(missingDir).build();

    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());
    assertEquals(0, explanation.totalTemplates());
    assertEquals(0, explanation.totalExpressions());
    assertTrue(explanation.templates().isEmpty());
    assertTrue(explanation.diagnostics().isEmpty());
  }

  @Test
  void testOutputFileWriting() throws IOException {
    Path templateFile = tempDir.resolve("file.vtl");
    Files.writeString(templateFile, "$user.name\n");

    TemplateId templateId = TemplateId.of("file.vtl");
    TemplateContract contract = TemplateContract.fromRoot(templateId, "user", UserRecord.class);

    Path textOut = tempDir.resolve("output.txt");
    TemplateExplainRequest textReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .format("text")
            .outputFile(textOut)
            .build();

    TemplateExplanation textExpl = explainer.explain(textReq);
    assertTrue(Files.isRegularFile(textOut));
    assertEquals(textExpl.asText(), Files.readString(textOut));

    Path jsonOut = tempDir.resolve("output.json");
    TemplateExplainRequest jsonReq =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .contract(templateId, contract)
            .format("json")
            .outputFile(jsonOut)
            .build();

    TemplateExplanation jsonExpl = explainer.explain(jsonReq);
    assertTrue(Files.isRegularFile(jsonOut));
    assertEquals(jsonExpl.asJson(), Files.readString(jsonOut));
  }

  @Test
  void testFailOnDynamicFallback() throws IOException {
    Path templateFile = tempDir.resolve("dyn.vtl");
    Files.writeString(templateFile, "$dynamic.prop\n");

    TemplateExplainRequest request =
        TemplateExplainRequest.builder()
            .sourceDirectory(tempDir)
            .failOnDynamicFallback(true)
            .build();

    TemplateExplanation explanation = explainer.explain(request);
    assertFalse(explanation.success());
    assertTrue(
        explanation.diagnostics().stream()
            .anyMatch(d -> "VTLAOT:1102".equals(d.code().qualifiedCode())));
  }

  @Test
  void testCompanionContractDiscovery() throws IOException {
    Path templateFile = tempDir.resolve("companion.vtl");
    Files.writeString(templateFile, "$user.name\n");

    Path contractFile = tempDir.resolve("companion.vtl.contract");
    Files.writeString(
        contractFile,
        "class=io.github.minh124199.viettemplate.explanation.TemplateExplainerTest$UserRecord\n");

    TemplateExplainRequest request =
        TemplateExplainRequest.builder().sourceDirectory(tempDir).build();

    TemplateExplanation explanation = explainer.explain(request);
    assertTrue(explanation.success());
    assertTrue(explanation.templates().get(0).typed());
  }
}
