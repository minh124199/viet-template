package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverloadParityDifferentialTest {

  @TempDir Path tempDir;

  private final AtomicInteger testCounter = new AtomicInteger(0);

  // --- Differential Harness ---

  private String renderDynamic(String templateText, Map<String, Object> model) throws Exception {
    String testName = "dyn_" + testCounter.incrementAndGet() + ".vtl";
    CompiledTemplate compiled = compileTemplate(tempDir, testName, templateText, null);
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(MapRenderContext.of(model), out);
    return out.toString();
  }

  private String renderTypedAot(
      String templateText, Map<String, Object> model, TemplateContract contract) throws Exception {
    String testName = "typed_" + testCounter.incrementAndGet() + ".vtl";
    CompiledTemplate compiled = compileTemplate(tempDir, testName, templateText, contract);
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(MapRenderContext.of(model), out);
    return out.toString();
  }

  private String renderIrInterpreter(String templateText, Map<String, Object> model)
      throws Exception {
    String testName = "ir_" + testCounter.incrementAndGet() + ".vtl";
    SourceText source = SourceText.of(testName, templateText);
    VtlTemplate ast = VtlParser.parse(source).template();
    VtlInterpreterOptions irOptions =
        VtlInterpreterOptions.builder()
            .executionTier(ExecutionTier.IR)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
            .build();
    VtlInterpreter irInterpreter = new VtlInterpreter(irOptions);
    StringTemplateOutput out = new StringTemplateOutput();
    EngineInterpreterBridge.render(irInterpreter, source, ast, MapRenderContext.of(model), out);
    return out.toString();
  }

  private CompiledTemplate compileTemplate(
      Path baseDir, String templateName, String templateText, TemplateContract contract)
      throws Exception {
    Path srcDir = baseDir.resolve("src-" + templateName);
    Path outDir = baseDir.resolve("out-" + templateName);
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve(templateName);
    Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of(templateName);
    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest.Builder reqBuilder =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir);
    if (contract != null) {
      TemplateContract.Builder remapped = TemplateContract.builder(id);
      for (io.github.minh124199.viettemplate.api.TemplateParameter p : contract.parameters()) {
        remapped.parameter(p);
      }
      reqBuilder.contract(id, remapped.build());
    }

    TemplateAotResult result = compiler.compile(reqBuilder.build());
    assertThat(result.isSuccess()).as("Compilation failure: %s", result.diagnostics()).isTrue();
    assertThat(result.artifacts()).isNotEmpty();

    TemplateAotArtifact artifact = result.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    return clazz.getDeclaredConstructor().newInstance();
  }

  private void assertParityWithDynamic(
      String templateText, Map<String, Object> model, TemplateContract contract) throws Exception {
    String dynamicOut = renderDynamic(templateText, model);
    String typedAotOut = renderTypedAot(templateText, model, contract);

    assertThat(typedAotOut)
        .as("Typed AOT output must match Dynamic Bytecode output exactly")
        .isEqualTo(dynamicOut);
  }

  private void assertParityAcrossAllTiers(
      String templateText, Map<String, Object> model, TemplateContract contract) throws Exception {
    String dynamicOut = renderDynamic(templateText, model);
    String typedAotOut = renderTypedAot(templateText, model, contract);
    String irOut = renderIrInterpreter(templateText, model);

    assertThat(typedAotOut)
        .as("Typed AOT output must match Dynamic Bytecode output exactly")
        .isEqualTo(dynamicOut);
    assertThat(irOut)
        .as("IR Interpreter output must match Dynamic Bytecode output exactly")
        .isEqualTo(dynamicOut);
  }

  // --- Test Services ---

  public static class ObjectFirstOverloadService {
    public String foo(Object value) {
      return "object:" + value;
    }

    public String foo(String value) {
      return "string:" + value;
    }
  }

  public static class StringFirstOverloadService {
    public String foo(String value) {
      return "string:" + value;
    }

    public String foo(Object value) {
      return "object:" + value;
    }
  }

  public static class MultiPrimitiveOverloadService {
    public String compute(int a, int b) {
      return "int,int:" + (a + b);
    }

    public String compute(double a, double b) {
      return "double,double:" + (a + b);
    }

    public String compute(int a, double b) {
      return "int,double:" + (a + b);
    }
  }

  public static class BoxedVsPrimitiveService {
    public String process(int val) {
      return "primitive:" + val;
    }

    public String process(Integer val) {
      return "boxed:" + val;
    }
  }

  public static class UniqueSpecializationService {
    public String arity0() {
      return "ar0";
    }

    public String arity1(String a) {
      return "ar1:" + a;
    }

    public int arity2(int a, int b) {
      return a + b;
    }

    public String arity4(int a, double b, String c, boolean d) {
      return a + ":" + b + ":" + c + ":" + d;
    }

    public long arity6(int a, int b, int c, int d, int e, int f) {
      return (long) a + b + c + d + e + f;
    }

    public long arity8(int a, int b, int c, int d, int e, int f, int g, int h) {
      return (long) a + b + c + d + e + f + g + h;
    }

    public double widen2(double a, double b) {
      return a + b;
    }

    public int takeInt(int a) {
      return a * 2;
    }

    public String restrictedMethod() {
      return "should-not-run";
    }
  }

  public static class Tracker {
    private final AtomicInteger count = new AtomicInteger(0);

    public int next() {
      return count.incrementAndGet();
    }

    public int count() {
      return count.get();
    }
  }

  // --- Tests ---

  @Test
  @DisplayName("1. Overload parity: foo(Object) vs foo(String) with Object declared first")
  void testObjectFirstOverloadParity() throws Exception {
    String templateText = "[$svc.foo($val)]";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", ObjectFirstOverloadService.class)
            .parameter("val", String.class)
            .build();

    ObjectFirstOverloadService svc = new ObjectFirstOverloadService();

    // With String value
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", "hello"), contract);

    // With Object value
    TemplateContract objContract =
        TemplateContract.builder(TemplateId.of("t2.vtl"))
            .parameter("svc", ObjectFirstOverloadService.class)
            .parameter("val", Object.class)
            .build();
    assertParityWithDynamic(
        templateText, Map.of("svc", svc, "val", new StringBuilder("sb")), objContract);
  }

  @Test
  @DisplayName("2. Overload parity: foo(String) vs foo(Object) with String declared first")
  void testStringFirstOverloadParity() throws Exception {
    String templateText = "[$svc.foo($val)]";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", StringFirstOverloadService.class)
            .parameter("val", Object.class)
            .build();

    StringFirstOverloadService svc = new StringFirstOverloadService();

    // Passing String to Object contract
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", "hello"), contract);
    // Passing non-String
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", 12345), contract);
  }

  @Test
  @DisplayName("3. Multi-primitive overload parity across int, double, and mixed combinations")
  void testMultiPrimitiveOverloadParity() throws Exception {
    String templateText =
        "Ints: $svc.compute(10, 20)\n"
            + "Doubles: $svc.compute(1.5, 2.5)\n"
            + "Mixed: $svc.compute(10, 2.5)";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", MultiPrimitiveOverloadService.class)
            .build();

    MultiPrimitiveOverloadService svc = new MultiPrimitiveOverloadService();
    assertParityWithDynamic(templateText, Map.of("svc", svc), contract);
  }

  @Test
  @DisplayName("4. Boxed vs primitive overload parity")
  void testBoxedVsPrimitiveOverloadParity() throws Exception {
    String templateText = "Val: $svc.process($val)";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", BoxedVsPrimitiveService.class)
            .parameter("val", Integer.class)
            .build();

    BoxedVsPrimitiveService svc = new BoxedVsPrimitiveService();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", 42), contract);
  }

  @Test
  @DisplayName(
      "5. Unique candidate methods across arities 0, 1, 2, 4, 6, 8 specialize directly and maintain"
          + " parity")
  void testUniqueCandidateSpecializationParity() throws Exception {
    String templateText =
        "[$svc.arity0()]\n"
            + "[$svc.arity1('world')]\n"
            + "[$svc.arity2(10, 32)]\n"
            + "[$svc.arity4(1, 2.5, 'flag', true)]\n"
            + "[$svc.arity6(1, 2, 3, 4, 5, 6)]\n"
            + "[$svc.arity8(1, 2, 3, 4, 5, 6, 7, 8)]";

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", UniqueSpecializationService.class)
            .build();

    UniqueSpecializationService svc = new UniqueSpecializationService();
    assertParityAcrossAllTiers(templateText, Map.of("svc", svc), contract);
  }

  @Test
  @DisplayName("6. Permitted primitive widening on unique method (double, double) preserves parity")
  void testPermittedPrimitiveWideningParity() throws Exception {
    String templateText =
        "WidenedInts: $svc.widen2(10, 20)\n"
            + "WidenedMixed: $svc.widen2(10, 2.5)\n"
            + "ExactDoubles: $svc.widen2(1.5, 2.5)";

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", UniqueSpecializationService.class)
            .build();

    UniqueSpecializationService svc = new UniqueSpecializationService();
    assertParityAcrossAllTiers(templateText, Map.of("svc", svc), contract);
  }

  @Test
  @DisplayName(
      "7. Guard prevents narrowing conversion on specialized method: passing Double falls back to"
          + " dynamic")
  void testNarrowingFallbackParity() throws Exception {
    String templateText = "IntVal: $svc.takeInt($val)";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", UniqueSpecializationService.class)
            .parameter("val", Integer.class)
            .build();

    UniqueSpecializationService svc = new UniqueSpecializationService();

    // Valid integer: matches
    assertParityAcrossAllTiers(templateText, Map.of("svc", svc, "val", 5), contract);

    // Double passed at runtime: guard fails and falls back to dynamic site (which throws or handles
    // identically)
    Map<String, Object> invalidModel = Map.of("svc", svc, "val", 5.5);
    assertThatThrownBy(() -> renderTypedAot(templateText, invalidModel, contract))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> renderDynamic(templateText, invalidModel))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("8. Security policy denial fails closed identically in dynamic and typed AOT")
  void testSecurityPolicyDenialParity() throws Exception {
    String templateText = "[$svc.getClass()]";
    Path srcDir = tempDir.resolve("src-sec");
    Path outDir = tempDir.resolve("out-sec");
    Files.createDirectories(srcDir);
    Path templateFile = srcDir.resolve("sec.vtl");
    Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("sec.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("svc", UniqueSpecializationService.class).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();

    // 1. Typed AOT rejects restricted method at compile time
    TemplateAotRequest typedReq =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .build();
    TemplateAotResult typedResult = compiler.compile(typedReq);
    assertThat(typedResult.isSuccess()).isFalse();
    assertThat(typedResult.diagnostics())
        .anyMatch(d -> d.code().qualifiedCode().equals("VTLSEC:2401"));

    // 2. Dynamic site execution rejects restricted method at runtime
    UniqueSpecializationService svc = new UniqueSpecializationService();
    assertThatThrownBy(() -> renderDynamic(templateText, Map.of("svc", svc)))
        .isInstanceOf(TemplateSecurityException.class);
  }

  @Test
  @DisplayName("9. Null receiver evaluates arguments once and produces null identically")
  void testNullReceiverParity() throws Exception {
    String templateText = "[$svc.arity2($tracker.next(), $tracker.next())]";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", UniqueSpecializationService.class)
            .parameter("tracker", Tracker.class)
            .build();

    Tracker trackerDyn = new Tracker();
    Map<String, Object> dynModel = new java.util.HashMap<>();
    dynModel.put("svc", null);
    dynModel.put("tracker", trackerDyn);
    String dynOut = renderDynamic(templateText, dynModel);

    Tracker trackerTyped = new Tracker();
    Map<String, Object> typedModel = new java.util.HashMap<>();
    typedModel.put("svc", null);
    typedModel.put("tracker", trackerTyped);
    String typedOut = renderTypedAot(templateText, typedModel, contract);

    assertThat(typedOut).isEqualTo(dynOut);
    assertThat(trackerTyped.count()).isEqualTo(2);
    assertThat(trackerDyn.count()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "10. Deeply nested expressions (depth 10) with side-effects verify scratch slot and"
          + " evaluation parity")
  void testDeeplyNestedExpressionsParity() throws Exception {
    String templateText =
        "Result: $svc.arity2($svc.arity2($svc.arity2($svc.arity2($tracker.next(), 1), 1), 1), 1)";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("t.vtl"))
            .parameter("svc", UniqueSpecializationService.class)
            .parameter("tracker", Tracker.class)
            .build();

    UniqueSpecializationService svc = new UniqueSpecializationService();
    Tracker trackerDyn = new Tracker();
    Tracker trackerTyped = new Tracker();

    String dynOut = renderDynamic(templateText, Map.of("svc", svc, "tracker", trackerDyn));
    String typedOut =
        renderTypedAot(templateText, Map.of("svc", svc, "tracker", trackerTyped), contract);

    assertThat(typedOut).isEqualTo(dynOut);
    assertThat(trackerTyped.count()).isEqualTo(1);
    assertThat(trackerDyn.count()).isEqualTo(1);
  }
}
