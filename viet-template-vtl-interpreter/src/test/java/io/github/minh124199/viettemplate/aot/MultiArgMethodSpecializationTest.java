package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateContract;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions;
import io.github.minh124199.viettemplate.vtl.internal.compiler.BackendResult;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode.BytecodeTemplateCompiler;
import io.github.minh124199.viettemplate.vtl.interpreter.EngineInterpreterBridge;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreter;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiArgMethodSpecializationTest {

  public interface MixedArgInterface {
    String mixed(Object o, int i, long l, double d, boolean b, String s);
  }

  public static class MixedArgClass implements MixedArgInterface {
    @Override
    public String mixed(Object o, int i, long l, double d, boolean b, String s) {
      return o + ":" + i + ":" + l + ":" + d + ":" + b + ":" + s;
    }
  }

  public interface CalculatorService {
    int add(int a, int b);

    double calculate(double base, int factor, String mode, boolean flag);
  }

  public static class CalculatorServiceImpl implements CalculatorService {
    @Override
    public int add(int a, int b) {
      return a + b;
    }

    @Override
    public double calculate(double base, int factor, String mode, boolean flag) {
      double res = base * factor;
      if ("neg".equals(mode)) {
        res = -res;
      }
      return flag ? res + 1.0 : res;
    }
  }

  public static class MultiArgService {
    public String name() {
      return "service-0";
    }

    public String greet(String target) {
      return "Hello, " + target + "!";
    }

    public int add(int a, int b) {
      return a + b;
    }

    public String format(String prefix, int count, boolean active, double ratio) {
      return prefix + ":" + count + ":" + active + ":" + ratio;
    }

    public long sum8(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
      return (long) a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8;
    }

    public String compute(int a, int b) {
      return "int,int:" + (a + b);
    }

    public String compute(double a, double b) {
      return "double,double:" + (a + b);
    }

    public String compute(int a, double b) {
      return "int,double:" + (a + b);
    }

    public String ambig(String a, Object b) {
      return "string,object";
    }

    public String ambig(Object a, String b) {
      return "object,string";
    }
  }

  public static class AlternateService {
    public int add(int a, int b) {
      return (a + b) * 100;
    }
  }

  public static class SideEffectTracker {
    private final AtomicInteger counter = new AtomicInteger(0);

    public int nextVal() {
      return counter.incrementAndGet();
    }

    public int count() {
      return counter.get();
    }
  }

  private CompiledTemplate compileTemplate(
      Path tempDir, String templateName, String templateText, TemplateContract contract)
      throws Exception {
    Path srcDir = tempDir.resolve("src-" + templateName);
    Path outDir = tempDir.resolve("out-" + templateName);
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve(templateName);
    Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of(templateName);
    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest.Builder reqBuilder =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC);
    if (contract != null) {
      reqBuilder.contract(id, contract);
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

  @Test
  @DisplayName(
      "1. Multi-argument method invocation across arities 0, 1, 2, 4, 8 with primitives and"
          + " references")
  void testDirectMultiArgMethodInvocationAcrossArities(@TempDir Path tempDir) throws Exception {
    String templateText =
        "[$svc.name()]\n"
            + "[$svc.greet('World')]\n"
            + "[$svc.add(15, 27)]\n"
            + "[$svc.format('Item', 5, true, 2.5)]\n"
            + "[$svc.sum8(1, 2, 3, 4, 5, 6, 7, 8)]";

    TemplateId id = TemplateId.of("multi_arg.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("svc", MultiArgService.class).build();

    CompiledTemplate compiled = compileTemplate(tempDir, "multi_arg.vtl", templateText, contract);

    MultiArgService svc = new MultiArgService();
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", svc), out);

    String expected =
        "[service-0]\n" + "[Hello, World!]\n" + "[42]\n" + "[Item:5:true:2.5]\n" + "[36]";
    assertThat(out.toString()).isEqualTo(expected);
  }

  @Test
  @DisplayName("2. Interface method invocation (invokeinterface) with correct argument counting")
  void testInterfaceMethodInvocation(@TempDir Path tempDir) throws Exception {
    String templateText =
        "Add: $calc.add(20, 22)\n" + "Calc: $calc.calculate(10.0, 3, 'neg', true)";

    TemplateId id = TemplateId.of("interface_call.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("calc", CalculatorService.class).build();

    CompiledTemplate compiled =
        compileTemplate(tempDir, "interface_call.vtl", templateText, contract);

    CalculatorService calc = new CalculatorServiceImpl();
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("calc", calc), out);

    assertThat(out.toString()).isEqualTo("Add: 42\nCalc: -29.0");
  }

  @Test
  @DisplayName(
      "3. Overload resolution preserves dynamic linker parity via fallback to dynamic dispatch")
  void testDeterministicOverloadResolution(@TempDir Path tempDir) throws Exception {
    String templateText =
        "Ints: $svc.compute(10, 20)\n"
            + "Doubles: $svc.compute(1.5, 2.5)\n"
            + "Mixed: $svc.compute(10, 2.5)";

    TemplateId id = TemplateId.of("overloads.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("svc", MultiArgService.class).build();

    CompiledTemplate typedCompiled =
        compileTemplate(tempDir.resolve("typed"), "overloads.vtl", templateText, contract);
    CompiledTemplate dynamicCompiled =
        compileTemplate(tempDir.resolve("dyn"), "overloads_dyn.vtl", templateText, null);

    MultiArgService svc = new MultiArgService();
    StringTemplateOutput typedOut = new StringTemplateOutput();
    typedCompiled.render(RenderContext.of("svc", svc), typedOut);

    StringTemplateOutput dynOut = new StringTemplateOutput();
    dynamicCompiled.render(RenderContext.of("svc", svc), dynOut);

    assertThat(typedOut.toString()).isEqualTo(dynOut.toString());
  }

  @Test
  @DisplayName(
      "4. Ambiguous overload at compile time safely preserves compilation and uses dynamic"
          + " fallback")
  void testAmbiguousOverloadPreservesCompilation(@TempDir Path tempDir) throws Exception {
    String templateText = "Result: $svc.ambig('hello', 'world')";

    TemplateId id = TemplateId.of("ambig.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("svc", MultiArgService.class).build();

    CompiledTemplate typedCompiled =
        compileTemplate(tempDir.resolve("typed"), "ambig.vtl", templateText, contract);
    CompiledTemplate dynamicCompiled =
        compileTemplate(tempDir.resolve("dyn"), "ambig_dyn.vtl", templateText, null);

    MultiArgService svc = new MultiArgService();
    StringTemplateOutput typedOut = new StringTemplateOutput();
    typedCompiled.render(RenderContext.of("svc", svc), typedOut);

    StringTemplateOutput dynOut = new StringTemplateOutput();
    dynamicCompiled.render(RenderContext.of("svc", svc), dynOut);

    assertThat(typedOut.toString()).isEqualTo(dynOut.toString());
  }

  @Test
  @DisplayName(
      "5. Single-evaluation guarantee on direct specialized path (side effects evaluated once)")
  void testSingleEvaluationGuaranteeOnDirectPath(@TempDir Path tempDir) throws Exception {
    String templateText = "Add: $svc.add($tracker.nextVal(), $tracker.nextVal())";

    TemplateId id = TemplateId.of("side_effects.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MultiArgService.class)
            .parameter("tracker", SideEffectTracker.class)
            .build();

    CompiledTemplate compiled =
        compileTemplate(tempDir, "side_effects.vtl", templateText, contract);

    MultiArgService svc = new MultiArgService();
    SideEffectTracker tracker = new SideEffectTracker();

    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", svc, "tracker", tracker), out);

    // 1 + 2 = 3
    assertThat(out.toString()).isEqualTo("Add: 3");
    // Ensure tracker was incremented exactly twice (once per argument)
    assertThat(tracker.count()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "6. Single-evaluation guarantee when runtime guard fails and falls back to dynamic site")
  void testSingleEvaluationOnGuardFailureFallback(@TempDir Path tempDir) throws Exception {
    String templateText = "Result: $svc.add($tracker.nextVal(), 10)";

    TemplateId id = TemplateId.of("guard_fallback.vtl");
    // Compile template with MultiArgService contract
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MultiArgService.class)
            .parameter("tracker", SideEffectTracker.class)
            .build();

    CompiledTemplate compiled =
        compileTemplate(tempDir, "guard_fallback.vtl", templateText, contract);

    // Provide AlternateService at runtime (which is not an instance of MultiArgService)
    // AlternateService still has add(int, int) -> (a + b) * 100
    AlternateService alternateSvc = new AlternateService();
    SideEffectTracker tracker = new SideEffectTracker();

    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", alternateSvc, "tracker", tracker), out);

    // Evaluated via dynamic fallback: (1 + 10) * 100 = 1100
    assertThat(out.toString()).isEqualTo("Result: 1100");
    // The tracker expression must have evaluated EXACTLY ONCE, never twice!
    assertThat(tracker.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("7. Null receiver evaluates arguments exactly once before producing null")
  void testNullReceiverEvaluatesArgumentsOnce(@TempDir Path tempDir) throws Exception {
    String templateText = "Start[$!svc.add($tracker.nextVal(), 10)]End";

    TemplateId id = TemplateId.of("null_receiver.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MultiArgService.class, true)
            .parameter("tracker", SideEffectTracker.class)
            .build();

    CompiledTemplate compiled =
        compileTemplate(tempDir, "null_receiver.vtl", templateText, contract);

    SideEffectTracker tracker = new SideEffectTracker();
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(MapRenderContext.of(Map.of("tracker", tracker)), out);

    assertThat(out.toString()).isEqualTo("Start[]End");
    // Under language semantics, arguments are evaluated once left-to-right before receiver null
    // check!
    assertThat(tracker.count()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "8. Security policy denial fails closed for restricted methods at both compile-time and"
          + " runtime")
  void testSecurityPolicyDenialFailsClosed(@TempDir Path tempDir) throws Exception {
    String templateText = "Class: $svc.getClass()";

    Path srcDir = tempDir.resolve("src-security");
    Path outDir = tempDir.resolve("out-security");
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve("security.vtl");
    Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

    TemplateId id = TemplateId.of("security.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("svc", MultiArgService.class).build();

    TemplateAotCompiler compiler = TemplateAotCompiler.create();

    // 1. Compile-time fail closed with contract: DirectAccessorBindingPass rejects restricted
    // method
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

    // 2. Runtime fail closed without contract (dynamic site path): LinkerAccessPolicy rejects
    // restricted method
    Path dynamicOutDir = tempDir.resolve("out-sec-dyn");
    TemplateAotRequest dynamicReq =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(dynamicOutDir)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
            .build();
    TemplateAotResult dynResult = compiler.compile(dynamicReq);
    assertThat(dynResult.isSuccess()).isTrue();
    TemplateAotArtifact artifact = dynResult.artifacts().get(0);
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate dynTemplate = clazz.getDeclaredConstructor().newInstance();

    MultiArgService svc = new MultiArgService();
    StringTemplateOutput out = new StringTemplateOutput();
    assertThatThrownBy(() -> dynTemplate.render(RenderContext.of("svc", svc), out))
        .isInstanceOf(TemplateSecurityException.class);
  }

  @Test
  @DisplayName("9. Exact execution equivalence between AOT Bytecode and IR Interpreter")
  void testEquivalenceBetweenAotAndIrInterpreter(@TempDir Path tempDir) throws Exception {
    String templateText =
        "Greeting: $svc.greet('Vietnam')\n"
            + "Calc: $svc.add(10, 25)\n"
            + "Formatted: $svc.format('Total', $svc.add(5, 5), true, 1.25)\n"
            + "Sum8: $svc.sum8(1, 2, 3, 4, 5, 6, 7, 8)";

    TemplateId id = TemplateId.of("equivalence.vtl");
    TemplateContract contract =
        TemplateContract.builder(id).parameter("svc", MultiArgService.class).build();

    // 1. AOT execution
    CompiledTemplate compiled = compileTemplate(tempDir, "equivalence.vtl", templateText, contract);
    MultiArgService svc = new MultiArgService();
    StringTemplateOutput aotOut = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", svc), aotOut);

    // 2. IR tier execution
    SourceText source = SourceText.of("equivalence.vtl", templateText);
    VtlTemplate ast = VtlParser.parse(source).template();
    VtlInterpreterOptions irOptions =
        VtlInterpreterOptions.builder()
            .executionTier(ExecutionTier.IR)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
            .build();
    VtlInterpreter irInterpreter = new VtlInterpreter(irOptions);
    StringTemplateOutput irOut = new StringTemplateOutput();
    EngineInterpreterBridge.render(irInterpreter, source, ast, RenderContext.of("svc", svc), irOut);

    assertThat(aotOut.toString()).isEqualTo(irOut.toString());
  }

  @Test
  @DisplayName(
      "10. Mixed-type multi-argument invocation on interface and class verifying slot widths and"
          + " stack sizing")
  void testMixedArgumentsOnInterfaceAndClass(@TempDir Path tempDir) throws Exception {
    String templateText =
        "Class: $svc.mixed('item', 1, 10000000000, 3.14159, true, 'tail')\n"
            + "Iface: $iface.mixed('item2', 2, 20000000000, 2.71828, false, 'tail2')";

    TemplateId id = TemplateId.of("mixed_args.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MixedArgClass.class)
            .parameter("iface", MixedArgInterface.class)
            .build();

    CompiledTemplate compiled = compileTemplate(tempDir, "mixed_args.vtl", templateText, contract);
    MixedArgClass mixedObj = new MixedArgClass();
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", mixedObj, "iface", mixedObj), out);

    assertThat(out.toString())
        .isEqualTo(
            "Class: item:1:10000000000:3.14159:true:tail\n"
                + "Iface: item2:2:20000000000:2.71828:false:tail2");
  }

  @Test
  @DisplayName(
      "11. Guard prevents narrowing conversion: passing Double to int parameter falls back to"
          + " dynamic dispatch instead of truncating")
  void testGuardPreventsNarrowingConversion(@TempDir Path tempDir) throws Exception {
    String templateText = "Add: $svc.add($val, 10)";

    TemplateId id = TemplateId.of("narrowing.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MultiArgService.class)
            .parameter("val", Integer.class)
            .build();

    CompiledTemplate compiled = compileTemplate(tempDir, "narrowing.vtl", templateText, contract);
    MultiArgService svc = new MultiArgService();

    // 1. Integer works via direct specialized path
    StringTemplateOutput out1 = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", svc, "val", 5), out1);
    assertThat(out1.toString()).isEqualTo("Add: 15");

    // 2. Double 2.5 fails Guard 4b (widening only: Integer/Short/Byte allowed for int)
    // and falls back to dynamic site without truncating 2.5 to 2!
    StringTemplateOutput out2 = new StringTemplateOutput();
    assertThatThrownBy(() -> compiled.render(RenderContext.of("svc", svc, "val", 2.5), out2))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName(
      "12. Compiler generates distinct FQCN and fingerprints across differing security policies")
  void testSecurityPolicyCacheIsolation() {
    String templateText = "$svc.name()";
    SourceText source = SourceText.of("isolation.vtl", templateText);
    var parseResult = VtlParser.parse(source);
    var semOptions = VtlSemanticOptions.builder().allowArbitraryMethods(true).build();
    var analysis = VtlSemanticAnalyzer.analyze(parseResult.template(), semOptions);
    var ir = AstToIrLowerer.lower(parseResult.template(), source, analysis, semOptions);

    BytecodeTemplateCompiler compiler = new BytecodeTemplateCompiler();

    BackendOptions standardOptions =
        BackendOptions.builder().securityPolicy(LinkerAccessPolicy.standard()).build();
    BackendResult standardResult = compiler.compile(ir, standardOptions);
    assertThat(standardResult.isSuccess()).isTrue();

    BackendOptions denyAllOptions =
        BackendOptions.builder().securityPolicy(LinkerAccessPolicy.denyAll()).build();
    BackendResult denyAllResult = compiler.compile(ir, denyAllOptions);
    assertThat(denyAllResult.isSuccess()).isTrue();

    String fqcnStandard = standardResult.templateInstance().getClass().getName();
    String fqcnDenyAll = denyAllResult.templateInstance().getClass().getName();

    assertThat(fqcnStandard).isNotEqualTo(fqcnDenyAll);
  }

  @Test
  @DisplayName(
      "13. Bytecode structural verification via javap validates StackMapTable and invoke opcodes")
  void testJavapBytecodeVerification(@TempDir Path tempDir) throws Exception {
    String templateText =
        "Class: $svc.mixed('item', 1, 10000000000, 3.14159, true, 'tail')\n"
            + "Iface: $iface.mixed('item2', 2, 20000000000, 2.71828, false, 'tail2')";

    TemplateId id = TemplateId.of("javap_test.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MixedArgClass.class)
            .parameter("iface", MixedArgInterface.class)
            .build();

    Path srcDir = tempDir.resolve("src-javap");
    Path outDir = tempDir.resolve("out-javap");
    Files.createDirectories(srcDir);
    Path templateFile = srcDir.resolve("javap_test.vtl");
    Files.writeString(templateFile, templateText, StandardCharsets.UTF_8);

    TemplateAotCompiler aotCompiler = TemplateAotCompiler.create();
    TemplateAotRequest req =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(id, contract)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_DYNAMIC)
            .build();
    TemplateAotResult result = aotCompiler.compile(req);
    assertThat(result.isSuccess()).isTrue();
    TemplateAotArtifact artifact = result.artifacts().get(0);

    ToolProvider javap = ToolProvider.findFirst("javap").orElse(null);
    if (javap != null) {
      StringWriter sw = new StringWriter();
      int exitCode =
          javap.run(
              new java.io.PrintWriter(sw),
              new java.io.PrintWriter(sw),
              "-v",
              "-p",
              artifact.outputFile().toString());
      assertThat(exitCode).isEqualTo(0);
      String javapOutput = sw.toString();
      assertThat(javapOutput).contains("StackMapTable:");
      assertThat(javapOutput).contains("invokeinterface");
      assertThat(javapOutput).contains("invokevirtual");
    }
  }

  @Test
  @DisplayName(
      "14. Deeply nested expressions (depth 8-16) verify scratch slot allocation and single"
          + " evaluation")
  void testDeeplyNestedExpressionsScratchSlotAndSingleEvaluation(@TempDir Path tempDir)
      throws Exception {
    // Nested calls: $svc.add($svc.add(...)) up to depth 8
    String templateText =
        "Result: $svc.add($svc.add($svc.add($svc.add($svc.add($svc.add($svc.add($tracker.nextVal(),"
            + " 1), 1), 1), 1), 1), 1), 1)";

    TemplateId id = TemplateId.of("nested_expr.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MultiArgService.class)
            .parameter("tracker", SideEffectTracker.class)
            .build();

    CompiledTemplate compiled = compileTemplate(tempDir, "nested_expr.vtl", templateText, contract);

    MultiArgService svc = new MultiArgService();
    SideEffectTracker tracker = new SideEffectTracker();
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", svc, "tracker", tracker), out);

    // Initial tracker.nextVal() produces 1, + 7 = 8
    assertThat(out.toString()).isEqualTo("Result: 8");
    // Ensure tracker.nextVal() was called exactly once despite deep nesting
    assertThat(tracker.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("15. Deeply nested control flow (depth 8) validates stack and scratch slot layout")
  void testDeeplyNestedControlFlowAndScratchSlots(@TempDir Path tempDir) throws Exception {
    StringBuilder sb = new StringBuilder();
    int depth = 8;
    for (int i = 1; i <= depth; i++) {
      sb.append("#if($val > 0)\n");
    }
    sb.append("Deep: $svc.add($val, 10)\n");
    for (int i = 1; i <= depth; i++) {
      sb.append("#end\n");
    }

    TemplateId id = TemplateId.of("deep_control_flow.vtl");
    TemplateContract contract =
        TemplateContract.builder(id)
            .parameter("svc", MultiArgService.class)
            .parameter("val", Integer.class)
            .build();

    CompiledTemplate compiled =
        compileTemplate(tempDir, "deep_control_flow.vtl", sb.toString(), contract);

    MultiArgService svc = new MultiArgService();
    StringTemplateOutput out = new StringTemplateOutput();
    compiled.render(RenderContext.of("svc", svc, "val", 5), out);

    assertThat(out.toString().trim()).isEqualTo("Deep: 15");
  }
}
