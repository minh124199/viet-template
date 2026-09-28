package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlSecurityPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

  private String renderWithPolicy(
      String templateText, Map<String, Object> model, VtlSecurityPolicy policy) throws Exception {
    SourceText source = SourceText.of("policy_test.vtl", templateText);
    var parseResult = VtlParser.parse(source);
    var semOptions = VtlSemanticOptions.builder().allowArbitraryMethods(true).build();
    var analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    var ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    var options = BackendOptions.builder().securityPolicy(policy.toLinkerAccessPolicy()).build();
    var result = compiler.compile(ir, options);
    assertThat(result.isSuccess()).as("Compile failure: %s", result.diagnostics()).isTrue();
    StringTemplateOutput out = new StringTemplateOutput();
    result.templateInstance().render(MapRenderContext.of(model), out);
    return out.toString();
  }

  private String renderTypedWithPolicy(
      String templateText,
      Map<String, Object> model,
      TemplateContract contract,
      CustomOverloadSecurityPolicy policy)
      throws Exception {
    SourceText source = SourceText.of("policy_typed.vtl", templateText);
    var parseResult = VtlParser.parse(source);
    var semBuilder =
        VtlSemanticOptions.builder().allowArbitraryMethods(true).memberAccessPolicy(policy);
    if (contract != null) {
      semBuilder.modelSchema(ModelSchema.fromContract(contract));
    }
    var semOptions = semBuilder.build();
    var analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    var ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();
    var options = BackendOptions.builder().securityPolicy(policy.toLinkerAccessPolicy()).build();
    var result = compiler.compile(ir, options);
    assertThat(result.isSuccess()).as("Compile failure: %s", result.diagnostics()).isTrue();
    StringTemplateOutput out = new StringTemplateOutput();
    result.templateInstance().render(MapRenderContext.of(model), out);
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

  public static class ReferenceHierarchyService {
    public String foo(Object o) {
      return "object:" + (o == null ? "null" : o.toString());
    }

    public String foo(CharSequence cs) {
      return "charsequence:" + cs;
    }

    public String foo(String s) {
      return "string:" + s;
    }
  }

  public static class CustomCharSequence implements CharSequence {
    private final String content;

    public CustomCharSequence(String content) {
      this.content = content;
    }

    @Override
    public int length() {
      return content.length();
    }

    @Override
    public char charAt(int index) {
      return content.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return content.subSequence(start, end);
    }

    @Override
    public String toString() {
      return content;
    }
  }

  public static class NumberHierarchyService {
    public String foo(Number n) {
      return "number:" + n;
    }

    public String foo(Integer i) {
      return "integer:" + i;
    }

    public String foo(Long l) {
      return "long:" + l;
    }
  }

  public static class InterfaceHierarchyService {
    public String foo(Collection<?> c) {
      return "collection:" + c.size();
    }

    public String foo(List<?> l) {
      return "list:" + l.size();
    }

    public String foo(ArrayList<?> a) {
      return "arraylist:" + a.size();
    }
  }

  public static class Animal {
    public String speak() {
      return "sound";
    }
  }

  public static class Dog extends Animal {
    @Override
    public String speak() {
      return "bark";
    }
  }

  public static class UserHierarchyService {
    public String process(Animal a) {
      return "animal:" + a.speak();
    }

    public String process(Dog d) {
      return "dog:" + d.speak();
    }
  }

  public static class NullOverloadService {
    public String handle(String s) {
      return "string:" + s;
    }

    public String handle(Object o) {
      return "object:" + o;
    }
  }

  public static class BooleanOverloadService {
    public String check(boolean b) {
      return "boolean:" + b;
    }

    public String check(Boolean b) {
      return "boxed:" + b;
    }

    public String check(Object o) {
      return "object:" + o;
    }
  }

  public static class PolicySpecificService {
    public String foo(Object o) {
      return "object:" + o;
    }

    public String foo(String s) {
      return "string:" + s;
    }
  }

  public static class VarargsOverloadService {
    public String search(String s) {
      return "string:" + s;
    }

    public String search(Object... items) {
      return "varargs:" + items.length;
    }
  }

  public static class CharWideningService {
    public int takeInt(int x) {
      return x * 2;
    }

    public char echoChar(char c) {
      return c;
    }
  }

  public static class NestedTreeService {
    public int outer(int a, int b) {
      return a * 100 + b;
    }

    public int inner(int a, int b) {
      return a + b;
    }

    public int call(int a, int b) {
      return a + b;
    }

    public int foo(int x) {
      return x * 2;
    }

    public int bar() {
      return 5;
    }

    public int baz(int y) {
      return y + 10;
    }

    public int qux(int a, int b) {
      return a + b;
    }
  }

  public interface Greeter {
    String greet(String name);
  }

  public static class DivergedGreeter {
    public String greet(String name) {
      return "diverged:" + name;
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
    Map<String, Object> dynModel = new HashMap<>();
    dynModel.put("svc", null);
    dynModel.put("tracker", trackerDyn);
    String dynOut = renderDynamic(templateText, dynModel);

    Tracker trackerTyped = new Tracker();
    Map<String, Object> typedModel = new HashMap<>();
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

  @Test
  @DisplayName("11. Reference hierarchy: foo(Object) vs foo(CharSequence) vs foo(String)")
  void testReferenceHierarchyParity() throws Exception {
    String templateText = "[$svc.foo($val)]";
    ReferenceHierarchyService svc = new ReferenceHierarchyService();

    // 1. Static Object, runtime String
    TemplateContract contractObj =
        TemplateContract.builder(TemplateId.of("ref1.vtl"))
            .parameter("svc", ReferenceHierarchyService.class)
            .parameter("val", Object.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", "hello"), contractObj);
    assertParityWithDynamic(
        templateText, Map.of("svc", svc, "val", new StringBuilder("builder")), contractObj);
    assertParityWithDynamic(
        templateText, Map.of("svc", svc, "val", new CustomCharSequence("custom")), contractObj);

    // 2. Static CharSequence, runtime custom CharSequence
    TemplateContract contractCs =
        TemplateContract.builder(TemplateId.of("ref2.vtl"))
            .parameter("svc", ReferenceHierarchyService.class)
            .parameter("val", CharSequence.class)
            .build();
    assertParityWithDynamic(
        templateText, Map.of("svc", svc, "val", new CustomCharSequence("custom")), contractCs);
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", "str"), contractCs);

    // 3. Static String, runtime String
    TemplateContract contractStr =
        TemplateContract.builder(TemplateId.of("ref3.vtl"))
            .parameter("svc", ReferenceHierarchyService.class)
            .parameter("val", String.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", "hello"), contractStr);
  }

  @Test
  @DisplayName("12. Number hierarchy: foo(Number) vs foo(Integer) vs foo(Long)")
  void testNumberHierarchyParity() throws Exception {
    String templateText = "[$svc.foo($val)]";
    NumberHierarchyService svc = new NumberHierarchyService();

    // 1. Static Number, runtime Integer, Long, BigDecimal
    TemplateContract contractNum =
        TemplateContract.builder(TemplateId.of("num1.vtl"))
            .parameter("svc", NumberHierarchyService.class)
            .parameter("val", Number.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", 42), contractNum);
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", 100L), contractNum);
    assertParityWithDynamic(
        templateText, Map.of("svc", svc, "val", new BigDecimal("99.99")), contractNum);

    // 2. Dynamic (no contract), runtime Integer
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", 42), null);

    // 3. Static Integer, runtime Integer
    TemplateContract contractInt =
        TemplateContract.builder(TemplateId.of("num3.vtl"))
            .parameter("svc", NumberHierarchyService.class)
            .parameter("val", Integer.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", 42), contractInt);
  }

  @Test
  @DisplayName("13. Interface hierarchy: foo(Collection) vs foo(List) vs foo(ArrayList)")
  void testInterfaceHierarchyParity() throws Exception {
    String templateText = "[$svc.foo($val)]";
    InterfaceHierarchyService svc = new InterfaceHierarchyService();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("iface.vtl"))
            .parameter("svc", InterfaceHierarchyService.class)
            .parameter("val", Collection.class)
            .build();

    assertParityAcrossAllTiers(
        templateText, Map.of("svc", svc, "val", new ArrayList<>(List.of("a", "b"))), contract);
    assertParityAcrossAllTiers(
        templateText, Map.of("svc", svc, "val", new LinkedList<>(List.of("x"))), contract);
    assertParityAcrossAllTiers(
        templateText, Map.of("svc", svc, "val", new HashSet<>(List.of(1, 2, 3))), contract);
  }

  @Test
  @DisplayName("14. User-defined hierarchy: process(Animal) vs process(Dog)")
  void testUserDefinedHierarchyParity() throws Exception {
    String templateText = "[$svc.process($val)]";
    UserHierarchyService svc = new UserHierarchyService();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("user_hier.vtl"))
            .parameter("svc", UserHierarchyService.class)
            .parameter("val", Animal.class)
            .build();

    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", new Dog()), contract);
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", new Animal()), contract);
  }

  @Test
  @DisplayName("15. Null overload argument behavior matches dynamic linker")
  void testNullOverloadArgumentParity() throws Exception {
    String templateText = "[$svc.handle($val)]";
    NullOverloadService svc = new NullOverloadService();

    Map<String, Object> nullModel = new HashMap<>();
    nullModel.put("svc", svc);
    nullModel.put("val", null);

    TemplateContract contractStr =
        TemplateContract.builder(TemplateId.of("null_str.vtl"))
            .parameter("svc", NullOverloadService.class)
            .parameter("val", String.class)
            .build();
    assertParityWithDynamic(templateText, nullModel, contractStr);

    TemplateContract contractObj =
        TemplateContract.builder(TemplateId.of("null_obj.vtl"))
            .parameter("svc", NullOverloadService.class)
            .parameter("val", Object.class)
            .build();
    assertParityWithDynamic(templateText, nullModel, contractObj);
  }

  @Test
  @DisplayName("16. Literal-expression arguments on overloaded methods fall back safely")
  void testLiteralExpressionOverloadParity() throws Exception {
    String templateText = "[$svc.foo('hello')]";

    // ObjectFirstOverloadService
    ObjectFirstOverloadService svcObjFirst = new ObjectFirstOverloadService();
    TemplateContract contract1 =
        TemplateContract.builder(TemplateId.of("lit1.vtl"))
            .parameter("svc", ObjectFirstOverloadService.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svcObjFirst), contract1);

    // StringFirstOverloadService
    StringFirstOverloadService svcStrFirst = new StringFirstOverloadService();
    TemplateContract contract2 =
        TemplateContract.builder(TemplateId.of("lit2.vtl"))
            .parameter("svc", StringFirstOverloadService.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svcStrFirst), contract2);
  }

  @Test
  @DisplayName("17. Boolean vs Boolean vs Object overloads never enter numeric conversion")
  void testBooleanOverloadParity() throws Exception {
    String templateText = "[$svc.check($val)]";
    BooleanOverloadService svc = new BooleanOverloadService();

    TemplateContract contractBool =
        TemplateContract.builder(TemplateId.of("b1.vtl"))
            .parameter("svc", BooleanOverloadService.class)
            .parameter("val", boolean.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", true), contractBool);

    TemplateContract contractBoxed =
        TemplateContract.builder(TemplateId.of("b2.vtl"))
            .parameter("svc", BooleanOverloadService.class)
            .parameter("val", Boolean.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", Boolean.FALSE), contractBoxed);

    TemplateContract contractObj =
        TemplateContract.builder(TemplateId.of("b3.vtl"))
            .parameter("svc", BooleanOverloadService.class)
            .parameter("val", Object.class)
            .build();
    assertParityWithDynamic(templateText, Map.of("svc", svc, "val", "non-boolean"), contractObj);
  }

  @Test
  @DisplayName("18. Policy-specific overloads: permitted candidate selection under custom policy")
  void testPolicySpecificOverloadParity() throws Exception {
    String templateText = "[$svc.foo('hello')]";
    PolicySpecificService svc = new PolicySpecificService();
    Map<String, Object> model = Map.of("svc", svc);

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("pol.vtl"))
            .parameter("svc", PolicySpecificService.class)
            .build();

    // Policy A: allow foo(Object), deny foo(String)
    CustomOverloadSecurityPolicy allowObjectOnly =
        new CustomOverloadSecurityPolicy(Object.class, "allow-obj");
    String outCompiledA = renderWithPolicy(templateText, model, allowObjectOnly);
    String outTypedA = renderTypedWithPolicy(templateText, model, contract, allowObjectOnly);
    assertThat(outCompiledA).isEqualTo("[object:hello]");
    assertThat(outTypedA).isEqualTo(outCompiledA);

    // Policy B: allow foo(String), deny foo(Object)
    CustomOverloadSecurityPolicy allowStringOnly =
        new CustomOverloadSecurityPolicy(String.class, "allow-str");
    String outCompiledB = renderWithPolicy(templateText, model, allowStringOnly);
    String outTypedB = renderTypedWithPolicy(templateText, model, contract, allowStringOnly);
    assertThat(outCompiledB).isEqualTo("[string:hello]");
    assertThat(outTypedB).isEqualTo(outCompiledB);
  }

  public static class CustomOverloadSecurityPolicy
      implements VtlSecurityPolicy, MemberAccessPolicy {
    private final Class<?> allowedParamType;
    private final String id;

    public CustomOverloadSecurityPolicy(Class<?> allowedParamType, String id) {
      this.allowedParamType = allowedParamType;
      this.id = id;
    }

    @Override
    public boolean isClassPermitted(Class<?> clazz) {
      return true;
    }

    @Override
    public boolean isSafeProfile() {
      return false;
    }

    @Override
    public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
      if (method.getName().equals("foo") && method.getParameterTypes().length == 1) {
        return method.getParameterTypes()[0] == allowedParamType;
      }
      return true;
    }

    @Override
    public boolean isPropertyMethodPermitted(
        Class<?> receiverClass, Method method, String propertyName) {
      return isMethodPermitted(receiverClass, method);
    }

    @Override
    public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
      return true;
    }

    @Override
    public boolean isFieldPermitted(Class<?> receiverClass, java.lang.reflect.Field field) {
      return true;
    }

    @Override
    public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
      return true;
    }

    @Override
    public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
      return true;
    }

    @Override
    public boolean isIndexMutationPermitted(Class<?> receiverClass) {
      return true;
    }

    @Override
    public boolean isPropertyMutationPermitted(Class<?> receiverClass, String propertyName) {
      return true;
    }

    @Override
    public SecurityPolicyFingerprint fingerprint() {
      return SecurityPolicyFingerprint.of(id);
    }

    @Override
    public String policyFingerprint() {
      return id;
    }

    @Override
    public LinkerAccessPolicy toLinkerAccessPolicy() {
      return new LinkerAccessPolicy() {
        @Override
        public String policyId() {
          return id;
        }

        @Override
        public boolean isClassPermitted(Class<?> clazz) {
          return true;
        }

        @Override
        public boolean isMethodPermitted(Class<?> receiverClass, Method method) {
          if (method.getName().equals("foo") && method.getParameterTypes().length == 1) {
            return method.getParameterTypes()[0] == allowedParamType;
          }
          return true;
        }

        @Override
        public boolean isFieldPermitted(Class<?> receiverClass, java.lang.reflect.Field field) {
          return true;
        }

        @Override
        public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
          return true;
        }
      };
    }
  }

  @Test
  @DisplayName("19. Varargs overload multiplicity prevents static lock-in")
  void testVarargsOverloadMultiplicityParity() throws Exception {
    String templateText = "[$svc.search($val)]";
    VarargsOverloadService svc = new VarargsOverloadService();

    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("vargs.vtl"))
            .parameter("svc", VarargsOverloadService.class)
            .parameter("val", String.class)
            .build();

    assertParityAcrossAllTiers(templateText, Map.of("svc", svc, "val", "test"), contract);
  }

  @Test
  @DisplayName("20. Char widening: char to char exact vs Character to int dynamic fallback")
  void testCharWideningParity() throws Exception {
    CharWideningService svc = new CharWideningService();

    // 1. Exact char on echoChar(char)
    String templateChar = "[$svc.echoChar($c)]";
    TemplateContract contractChar =
        TemplateContract.builder(TemplateId.of("c1.vtl"))
            .parameter("svc", CharWideningService.class)
            .parameter("c", char.class)
            .build();
    assertParityAcrossAllTiers(templateChar, Map.of("svc", svc, "c", 'Z'), contractChar);

    // 2. Character to int on takeInt(int): deliberately dynamic fallback
    String templateInt = "[$svc.takeInt($c)]";
    TemplateContract contractInt =
        TemplateContract.builder(TemplateId.of("c2.vtl"))
            .parameter("svc", CharWideningService.class)
            .parameter("c", Character.class)
            .build();
    // Because Character cannot be unboxed via Number.intValue() in direct bytecode,
    // MethodResolver marks char-to-int widening as dynamic fallback. At runtime, both
    // Typed AOT and Dynamic Bytecode execute via dynamic call site and maintain 100% parity.
    Map<String, Object> charModel = Map.of("svc", svc, "c", 'A');
    assertParityWithDynamic(templateInt, charModel, contractInt);
    assertThat(renderTypedAot(templateInt, charModel, contractInt)).isEqualTo("[130]");

    // 3. Valid primitive int on takeInt(int) succeeds across all tiers
    TemplateContract contractIntVal =
        TemplateContract.builder(TemplateId.of("c3.vtl"))
            .parameter("svc", CharWideningService.class)
            .parameter("c", int.class)
            .build();
    assertParityAcrossAllTiers(templateInt, Map.of("svc", svc, "c", 21), contractIntVal);
  }

  @Test
  @DisplayName(
      "21. Branching nested calls verify scratch local isolation and StackMapTable correctness")
  void testBranchingNestedCallsParity() throws Exception {
    NestedTreeService svc = new NestedTreeService();

    // 1. outer(inner(1, 2), inner(3, 4))
    String template1 = "[$svc.outer($svc.inner(1, 2), $svc.inner(3, 4))]";
    TemplateContract contract1 =
        TemplateContract.builder(TemplateId.of("tree1.vtl"))
            .parameter("svc", NestedTreeService.class)
            .build();
    assertParityAcrossAllTiers(template1, Map.of("svc", svc), contract1);

    // 2. call(foo(bar()), baz(qux(1, 2)))
    String template2 = "[$svc.call($svc.foo($svc.bar()), $svc.baz($svc.qux(1, 2)))]";
    TemplateContract contract2 =
        TemplateContract.builder(TemplateId.of("tree2.vtl"))
            .parameter("svc", NestedTreeService.class)
            .build();
    assertParityAcrossAllTiers(template2, Map.of("svc", svc), contract2);
  }

  @Test
  @DisplayName(
      "22. Deliberately deep nesting (depth 16) verifies no verifier or stack overflow errors")
  void testDeepNestingDepth16Parity() throws Exception {
    NestedTreeService svc = new NestedTreeService();
    StringBuilder sb = new StringBuilder("Result: ");
    for (int i = 0; i < 16; i++) {
      sb.append("$svc.inner(1, ");
    }
    sb.append("0");
    for (int i = 0; i < 16; i++) {
      sb.append(")");
    }
    String templateDeep = sb.toString();
    TemplateContract contractDeep =
        TemplateContract.builder(TemplateId.of("deep16.vtl"))
            .parameter("svc", NestedTreeService.class)
            .build();

    assertParityAcrossAllTiers(templateDeep, Map.of("svc", svc), contractDeep);
  }

  @Test
  @DisplayName("23. Framework dynamic proxy receiver invokes cleanly via interface contract")
  void testFrameworkProxyReceiverParity() throws Exception {
    Greeter proxy =
        (Greeter)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Greeter.class},
                (p, method, args) -> "proxy:" + args[0]);

    String templateText = "[$svc.greet('World')]";
    TemplateContract contract =
        TemplateContract.builder(TemplateId.of("proxy.vtl"))
            .parameter("svc", Greeter.class)
            .build();

    assertParityAcrossAllTiers(templateText, Map.of("svc", proxy), contract);
  }

  // --- Parameterized Differential Matrix ---

  @ParameterizedTest(name = "{index} ==> Category: {0}")
  @MethodSource("overloadMatrixScenarios")
  @DisplayName("24. Parameterized Overload Differential Matrix (Section 21)")
  void testParameterizedOverloadMatrix(
      String category, String templateText, Map<String, Object> model, TemplateContract contract)
      throws Exception {
    assertParityWithDynamic(templateText, model, contract);
  }

  static Stream<Arguments> overloadMatrixScenarios() {
    UniqueSpecializationService uniqueSvc = new UniqueSpecializationService();
    StringFirstOverloadService strFirstSvc = new StringFirstOverloadService();
    UserHierarchyService userSvc = new UserHierarchyService();
    InterfaceHierarchyService ifaceSvc = new InterfaceHierarchyService();
    NullOverloadService nullSvc = new NullOverloadService();
    BoxedVsPrimitiveService boxedSvc = new BoxedVsPrimitiveService();
    MultiPrimitiveOverloadService multiPrimSvc = new MultiPrimitiveOverloadService();
    ObjectFirstOverloadService objFirstSvc = new ObjectFirstOverloadService();

    Map<String, Object> nullModel = new HashMap<>();
    nullModel.put("svc", nullSvc);
    nullModel.put("val", null);

    return Stream.of(
        // 1. unique overload
        Arguments.of(
            "unique overload",
            "[$svc.arity1('test')]",
            Map.of("svc", uniqueSvc),
            TemplateContract.builder(TemplateId.of("m1.vtl"))
                .parameter("svc", UniqueSpecializationService.class)
                .build()),
        // 2. exact overload
        Arguments.of(
            "exact overload",
            "[$svc.foo($val)]",
            Map.of("svc", strFirstSvc, "val", "hello"),
            TemplateContract.builder(TemplateId.of("m2.vtl"))
                .parameter("svc", StringFirstOverloadService.class)
                .parameter("val", String.class)
                .build()),
        // 3. base/static + subtype runtime
        Arguments.of(
            "base/static + subtype runtime",
            "[$svc.process($val)]",
            Map.of("svc", userSvc, "val", new Dog()),
            TemplateContract.builder(TemplateId.of("m3.vtl"))
                .parameter("svc", UserHierarchyService.class)
                .parameter("val", Animal.class)
                .build()),
        // 4. interface/static + concrete runtime
        Arguments.of(
            "interface/static + concrete runtime",
            "[$svc.foo($val)]",
            Map.of("svc", ifaceSvc, "val", new ArrayList<>(List.of("x", "y"))),
            TemplateContract.builder(TemplateId.of("m4.vtl"))
                .parameter("svc", InterfaceHierarchyService.class)
                .parameter("val", Collection.class)
                .build()),
        // 5. null runtime
        Arguments.of(
            "null runtime",
            "[$svc.handle($val)]",
            nullModel,
            TemplateContract.builder(TemplateId.of("m5.vtl"))
                .parameter("svc", NullOverloadService.class)
                .parameter("val", String.class)
                .build()),
        // 6. primitive exact
        Arguments.of(
            "primitive exact",
            "[$svc.takeInt($val)]",
            Map.of("svc", uniqueSvc, "val", 21),
            TemplateContract.builder(TemplateId.of("m6.vtl"))
                .parameter("svc", UniqueSpecializationService.class)
                .parameter("val", Integer.class)
                .build()),
        // 7. primitive widening
        Arguments.of(
            "primitive widening",
            "[$svc.widen2($a, $b)]",
            Map.of("svc", uniqueSvc, "a", 10, "b", 20),
            TemplateContract.builder(TemplateId.of("m7.vtl"))
                .parameter("svc", UniqueSpecializationService.class)
                .parameter("a", Integer.class)
                .parameter("b", Integer.class)
                .build()),
        // 8. boxed primitive
        Arguments.of(
            "boxed primitive",
            "[$svc.process($val)]",
            Map.of("svc", boxedSvc, "val", 42),
            TemplateContract.builder(TemplateId.of("m8.vtl"))
                .parameter("svc", BoxedVsPrimitiveService.class)
                .parameter("val", Integer.class)
                .build()),
        // 9. ambiguous static overload
        Arguments.of(
            "ambiguous static overload",
            "[$svc.compute(10, 20)]",
            Map.of("svc", multiPrimSvc),
            TemplateContract.builder(TemplateId.of("m9.vtl"))
                .parameter("svc", MultiPrimitiveOverloadService.class)
                .build()),
        // 10. dynamic argument
        Arguments.of(
            "dynamic argument",
            "[$svc.foo($val)]",
            Map.of("svc", objFirstSvc, "val", "dynamic_hello"),
            null),
        // 11. receiver runtime divergence
        Arguments.of(
            "receiver runtime divergence",
            "[$svc.greet('world')]",
            Map.of("svc", new DivergedGreeter()),
            TemplateContract.builder(TemplateId.of("m11.vtl"))
                .parameter("svc", Greeter.class)
                .build()));
  }
}
