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

    // Null string with empty string mode
    out = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        null, out, 0, 0, "$name");
    assertThat(out.toString()).isEqualTo("");

    // Null string with literal fallback mode (ordinal 1)
    out = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        null, out, 0, 1, "$name");
    assertThat(out.toString()).isEqualTo("$name");

    // HTML text escaping mode (ordinal 1)
    out = new StringTemplateOutput();
    io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge.writeString(
        "<b>\"hello\" & 'world'</b>", out, 1, 0, "$name");
    assertThat(out.toString())
        .isEqualTo("&lt;b&gt;&quot;hello&quot; &amp; &#39;world&#39;&lt;/b&gt;");
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
