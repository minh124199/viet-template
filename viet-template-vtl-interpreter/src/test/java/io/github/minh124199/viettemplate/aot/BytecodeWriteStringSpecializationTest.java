package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.*;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BytecodeWriteStringSpecializationTest {

  public record User(String name) {}

  @Test
  @DisplayName("Typed String emits BytecodeRuntimeBridge.writeString instead of generic writeValue")
  void testTypedStringEmitsWriteString(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("typed.vtl"), "$name", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("typed.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                templateId,
                TemplateContract.of(
                    templateId, List.of(TemplateParameter.of("name", String.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeString:(Ljava/lang/String;");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeValue:");
  }

  @Test
  @DisplayName("Untyped template emits generic BytecodeRuntimeBridge.writeValue")
  void testUntypedTemplateEmitsWriteValue(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("untyped.vtl"), "$name", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeValue:");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeString:");
  }

  @Test
  @DisplayName("Typed Record String accessor emits writeString")
  void testTypedRecordPropertyEmitsWriteString(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("record.vtl"), "$user.name", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("record.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                templateId,
                TemplateContract.of(templateId, List.of(TemplateParameter.of("user", User.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeString:(Ljava/lang/String;");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeValue:");
  }

  @Test
  @DisplayName("Safe profile template retains generic writeValue for runtime class checks")
  void testSafeProfileRetainsWriteValue(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("safe.vtl"), "$name", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId templateId = TemplateId.of("safe.vtl");
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .profile(io.github.minh124199.viettemplate.language.vtl.VtlProfile.VTL_SAFE)
            .contract(
                templateId,
                TemplateContract.of(
                    templateId, List.of(TemplateParameter.of("name", String.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(1);

    String javap = disassemble(result.artifacts().get(0).outputFile());
    assertThat(javap).contains("BytecodeRuntimeBridge.writeValue:");
    assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeString:");
  }

  @Test
  @DisplayName("writeString produces identical rendered results across null and escaped values")
  void testExecutionCorrectness() throws Exception {
    var out = new StringTemplateOutput();
    // Non-null clean string
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        "hello", out, 0, 0, "$name");
    assertThat(out.toString()).isEqualTo("hello");

    // Null string with empty string mode (ordinal 1)
    out = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        null, out, 0, 1, "$name");
    assertThat(out.toString()).isEqualTo("");

    // Null string with literal fallback mode (ordinal 0)
    out = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        null, out, 0, 0, "$name");
    assertThat(out.toString()).isEqualTo("$name");

    // HTML text escaping mode (ordinal 1)
    out = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        "<b>\"hello\" & 'world'</b>", out, 1, 0, "$name");
    assertThat(out.toString())
        .isEqualTo("&lt;b&gt;&quot;hello&quot; &amp; &#39;world&#39;&lt;/b&gt;");
  }

  @Test
  @DisplayName(
      "Non-String types (StringBuilder, SafeHtml, Object) do not specialize and retain writeValue")
  void testNonStringTypesRetainWriteValue(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("sb.vtl"), "$sb", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("safeHtml.vtl"), "$safe", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("obj.vtl"), "$obj", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateId sbId = TemplateId.of("sb.vtl");
    TemplateId safeId = TemplateId.of("safeHtml.vtl");
    TemplateId objId = TemplateId.of("obj.vtl");

    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .contract(
                sbId,
                TemplateContract.of(sbId, List.of(TemplateParameter.of("sb", StringBuilder.class))))
            .contract(
                safeId,
                TemplateContract.of(
                    safeId,
                    List.of(
                        TemplateParameter.of(
                            "safe", io.github.minh124199.viettemplate.runtime.SafeHtml.class))))
            .contract(
                objId,
                TemplateContract.of(objId, List.of(TemplateParameter.of("obj", Object.class))))
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.artifacts()).hasSize(3);

    for (TemplateAotArtifact artifact : result.artifacts()) {
      String javap = disassemble(artifact.outputFile());
      assertThat(javap).contains("BytecodeRuntimeBridge.writeValue:");
      assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeString:");
    }
  }

  @Test
  @DisplayName("Strict reference mode retains generic writeValue for span diagnostics")
  void testStrictModeRetainsWriteValue() throws Exception {
    var source =
        io.github.minh124199.viettemplate.language.vtl.source.SourceText.of("strict.vtl", "$name");
    var parse = io.github.minh124199.viettemplate.language.vtl.parser.VtlParser.parse(source);
    var contract =
        TemplateContract.of(
            TemplateId.of("strict.vtl"), List.of(TemplateParameter.of("name", String.class)));
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
      assertThat(javap).doesNotContain("BytecodeRuntimeBridge.writeString:");
    } finally {
      Files.deleteIfExists(tempClass);
    }
  }

  @Test
  @DisplayName(
      "Differential matrix: writeString execution matches writeValue across all test vectors")
  void testDifferentialOutputMatrix() throws Exception {
    String[] testStrings = {
      "",
      "hello world",
      "Unicode: Xin chào Việt Nam \uD83D\uDE00",
      "HTML: <script>alert('xss & \"more\"')</script>",
      "Quotes: \"double\" 'single'",
      "Special: & < > \" '",
      "Multi\nLine\r\nText\tWith\tTabs"
    };

    for (String val : testStrings) {
      for (int escapeMode = 0; escapeMode <= 1; escapeMode++) {
        var outSpecialized = new StringTemplateOutput();
        io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
            val, outSpecialized, escapeMode, 0, "$name");

        var outGeneric = new StringTemplateOutput();
        io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeValue(
            val, outGeneric, escapeMode, 0, "$name", false, "test.vtl", 0, 0, 0, 0, null);

        assertThat(outSpecialized.toString())
            .as("Escape mode %d for string '%s'", escapeMode, val)
            .isEqualTo(outGeneric.toString());
      }
    }

    // Null differential: EMPTY_STRING (ordinal 1)
    var outSpecializedNullEmpty = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        null, outSpecializedNullEmpty, 1, 1, "$name");
    var outGenericNullEmpty = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeValue(
        null, outGenericNullEmpty, 1, 1, "$name", false, "test.vtl", 0, 0, 0, 0, null);
    assertThat(outSpecializedNullEmpty.toString())
        .isEqualTo(outGenericNullEmpty.toString())
        .isEqualTo("");

    // Null differential: LITERAL_EXPRESSION (ordinal 0)
    var outSpecializedNullLit = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        null, outSpecializedNullLit, 1, 0, "$name");
    var outGenericNullLit = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeValue(
        null, outGenericNullLit, 1, 0, "$name", false, "test.vtl", 0, 0, 0, 0, null);
    assertThat(outSpecializedNullLit.toString())
        .isEqualTo(outGenericNullLit.toString())
        .isEqualTo("$name");
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
