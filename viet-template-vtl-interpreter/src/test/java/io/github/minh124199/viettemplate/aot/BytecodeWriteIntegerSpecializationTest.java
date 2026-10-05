package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.internal.compiler.TemplateClassLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BytecodeWriteIntegerSpecializationTest {

  public static class CounterBean {
    private final int count;

    public CounterBean(int count) {
      this.count = count;
    }

    public int getCount() {
      return count;
    }
  }

  public record ItemRecord(int id) {}

  @Test
  @DisplayName("Typed primitive int parameter emits BytecodeRuntimeBridge.writeInteger")
  void testTypedPrimitiveIntEmitsWriteInteger(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("prim.vtl"), "$count", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("prim.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                templateId,
                TemplateContract.of(templateId, List.of(TemplateParameter.of("count", int.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeInteger:(Ljava/lang/Integer;");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeValue:");

    // Verify execution
    byte[] classBytes = Files.readAllBytes(result.artifacts().get(0).outputFile());
    Class<? extends CompiledTemplate> clazz =
        new TemplateClassLoader(getClass().getClassLoader())
            .defineTemplateClass(result.artifacts().get(0).className(), classBytes);
    CompiledTemplate template = clazz.getDeclaredConstructor().newInstance();
    var out = new StringTemplateOutput();
    template.render(RenderContext.of(Map.of("count", 42)), out);
    assertThat(out.toString()).isEqualTo("42");
  }

  @Test
  @DisplayName("Typed boxed Integer parameter emits BytecodeRuntimeBridge.writeInteger")
  void testTypedBoxedIntegerEmitsWriteInteger(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("boxed.vtl"), "$count", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("boxed.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                templateId,
                TemplateContract.of(
                    templateId, List.of(TemplateParameter.of("count", Integer.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeInteger:(Ljava/lang/Integer;");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeValue:");

    // Verify execution
    byte[] classBytes = Files.readAllBytes(result.artifacts().get(0).outputFile());
    Class<? extends CompiledTemplate> clazz =
        new TemplateClassLoader(getClass().getClassLoader())
            .defineTemplateClass(result.artifacts().get(0).className(), classBytes);
    CompiledTemplate template = clazz.getDeclaredConstructor().newInstance();
    var out = new StringTemplateOutput();
    template.render(RenderContext.of(Map.of("count", 12345)), out);
    assertThat(out.toString()).isEqualTo("12345");
  }

  @Test
  @DisplayName("Typed bean getter returning int emits BytecodeRuntimeBridge.writeInteger")
  void testTypedBeanGetterEmitsWriteInteger(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("bean.vtl"), "$bean.count", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("bean.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                templateId,
                TemplateContract.of(
                    templateId, List.of(TemplateParameter.of("bean", CounterBean.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeInteger:(Ljava/lang/Integer;");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeValue:");

    // Verify execution
    byte[] classBytes = Files.readAllBytes(result.artifacts().get(0).outputFile());
    Class<? extends CompiledTemplate> clazz =
        new TemplateClassLoader(getClass().getClassLoader())
            .defineTemplateClass(result.artifacts().get(0).className(), classBytes);
    CompiledTemplate template = clazz.getDeclaredConstructor().newInstance();
    var out = new StringTemplateOutput();
    template.render(RenderContext.of(Map.of("bean", new CounterBean(99))), out);
    assertThat(out.toString()).isEqualTo("99");
  }

  @Test
  @DisplayName("Typed record component returning int emits BytecodeRuntimeBridge.writeInteger")
  void testTypedRecordPropertyEmitsWriteInteger(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("record.vtl"), "$item.id", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("record.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                templateId,
                TemplateContract.of(
                    templateId, List.of(TemplateParameter.of("item", ItemRecord.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeInteger:(Ljava/lang/Integer;");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeValue:");

    // Verify execution
    byte[] classBytes = Files.readAllBytes(result.artifacts().get(0).outputFile());
    Class<? extends CompiledTemplate> clazz =
        new TemplateClassLoader(getClass().getClassLoader())
            .defineTemplateClass(result.artifacts().get(0).className(), classBytes);
    CompiledTemplate template = clazz.getDeclaredConstructor().newInstance();
    var out = new StringTemplateOutput();
    template.render(RenderContext.of(Map.of("item", new ItemRecord(777))), out);
    assertThat(out.toString()).isEqualTo("777");
  }

  @Test
  @DisplayName("Untyped template emits generic BytecodeRuntimeBridge.writeValue")
  void testUntypedTemplateEmitsWriteValue(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("untyped.vtl"), "$count", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeValue:");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeInteger:");
  }

  @Test
  @DisplayName("Strict reference mode retains generic writeValue for span diagnostics")
  void testStrictModeRetainsWriteValue() throws Exception {
    var source =
        io.github.minh124199.viettemplate.language.vtl.source.SourceText.of("strict.vtl", "$count");
    var parse = io.github.minh124199.viettemplate.language.vtl.parser.VtlParser.parse(source);
    var contract =
        TemplateContract.of(
            TemplateId.of("strict.vtl"), List.of(TemplateParameter.of("count", int.class)));
    var modelSchema =
        io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema.fromContract(
            contract);
    var semOptions =
        io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions.builder()
            .modelSchema(modelSchema)
            .build();
    var analysis =
        io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer.analyze(
            parse.template(), semOptions);
    var ir =
        io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer.lower(
            parse.template(), source, analysis, semOptions);

    var compiler =
        new io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode
            .BytecodeTemplateCompiler();
    var options =
        io.github.minh124199.viettemplate.vtl.internal.compiler.BackendOptions.builder()
            .strictReferences(true)
            .build();
    var res = compiler.compile(ir, options);
    assertThat(res.isSuccess()).isTrue();

    Path tempClass = Files.createTempFile("StrictTemplate", ".class");
    try {
      Files.write(tempClass, res.artifact().classBytes());
      String javap = disassemble(tempClass);
      assertThat(javap).contains("BytecodeRuntimeBridge.writeValue:");
      assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeInteger:");
    } finally {
      Files.deleteIfExists(tempClass);
    }
  }

  @Test
  @DisplayName("VTL_SAFE profile retains generic writeValue for runtime checks")
  void testSafeProfileRetainsWriteValue(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("safe.vtl"), "$count", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("safe.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_SAFE)
            .contract(
                templateId,
                TemplateContract.of(templateId, List.of(TemplateParameter.of("count", int.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeValue:");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeInteger:");
  }

  @Test
  @DisplayName(
      "Null differential matrix: writeInteger execution matches writeValue across EMPTY_STRING and"
          + " LITERAL_EXPRESSION")
  void testNullDifferentialMatrix() throws Exception {
    // Null differential: EMPTY_STRING (ordinal 1)
    var outSpecializedNullEmpty = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeInteger(null, outSpecializedNullEmpty, 1, "$count");
    var outGenericNullEmpty = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        null, outGenericNullEmpty, 0, 1, "$count", false, "test.vtl", 0, 0, 0, 0, null);
    assertThat(outSpecializedNullEmpty.toString())
        .isEqualTo(outGenericNullEmpty.toString())
        .isEqualTo("");

    // Null differential: LITERAL_EXPRESSION (ordinal 0)
    var outSpecializedNullLit = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeInteger(null, outSpecializedNullLit, 0, "$count");
    var outGenericNullLit = new StringTemplateOutput();
    BytecodeRuntimeBridge.writeValue(
        null, outGenericNullLit, 0, 0, "$count", false, "test.vtl", 0, 0, 0, 0, null);
    assertThat(outSpecializedNullLit.toString())
        .isEqualTo(outGenericNullLit.toString())
        .isEqualTo("$count");
  }

  @Test
  @DisplayName(
      "Edge cases differential matrix: 0, 1, -1, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE render"
          + " identical strings to generic writeValue")
  void testEdgeCasesDifferentialMatrix() throws Exception {
    int[] testInts = {0, 1, -1, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE};

    for (int val : testInts) {
      var outSpecialized = new StringTemplateOutput();
      BytecodeRuntimeBridge.writeInteger(val, outSpecialized, 0, "$count");

      var outGeneric = new StringTemplateOutput();
      BytecodeRuntimeBridge.writeValue(
          val, outGeneric, 0, 0, "$count", false, "test.vtl", 0, 0, 0, 0, null);

      assertThat(outSpecialized.toString())
          .as("Edge case value: %d", val)
          .isEqualTo(outGeneric.toString())
          .isEqualTo(Integer.toString(val));
    }
  }

  private String disassemble(Path classFile) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder("javap", "-c", "-p", classFile.toString());
    Process p = pb.start();
    String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = p.waitFor();
    assertThat(exit).isEqualTo(0);
    return output;
  }
}
