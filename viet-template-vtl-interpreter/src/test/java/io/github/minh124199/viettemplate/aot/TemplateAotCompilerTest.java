package io.github.minh124199.viettemplate.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.TemplateClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateAotCompilerTest {

  @Test
  @DisplayName("1. Single valid template compilation succeeds and produces executable artifact")
  void testSingleValidTemplateCompilation(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path templateFile = srcDir.resolve("single.vtl");
    Files.writeString(templateFile, "Hello, $name! Static text.", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.success()).isTrue();
    assertThat(result.compiledCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(0);
    assertThat(result.deletedCount()).isEqualTo(0);
    assertThat(result.artifacts()).hasSize(1);

    TemplateAotArtifact artifact = result.artifacts().get(0);
    assertThat(artifact.templateId()).isEqualTo(TemplateId.of("single.vtl"));
    assertThat(artifact.outputFile()).isRegularFile();
    assertThat(artifact.className()).startsWith("io.github.minh124199.viettemplate.generated.");
    assertThat(artifact.simpleClassName()).isNotEmpty();
    assertThat(artifact.fingerprint()).isNotEmpty();

    // Verify templates.idx
    Path idxFile = outDir.resolve("META-INF/viet-template/templates.idx");
    assertThat(idxFile).isRegularFile();
    String idxContent = Files.readString(idxFile, StandardCharsets.UTF_8);
    assertThat(idxContent).isEqualTo("single.vtl=" + artifact.className() + "\n");

    // Verify aot-state
    Path stateFile = outDir.resolve("META-INF/viet-template/aot-state");
    assertThat(stateFile).isRegularFile();

    // Verify execution via TemplateClassLoader
    byte[] classBytes = Files.readAllBytes(artifact.outputFile());
    TemplateClassLoader loader = new TemplateClassLoader(getClass().getClassLoader());
    Class<? extends CompiledTemplate> clazz =
        loader.defineTemplateClass(artifact.className(), classBytes);
    CompiledTemplate instance = clazz.getDeclaredConstructor().newInstance();

    StringTemplateOutput output = new StringTemplateOutput();
    instance.render(RenderContext.of(Map.of("name", "VietTemplate")), output);
    assertThat(output.toString()).isEqualTo("Hello, VietTemplate! Static text.");
  }

  @Test
  @DisplayName("2. Multiple valid templates across nested directories are discovered and sorted")
  void testMultipleValidTemplatesAcrossNestedDirectories(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Path resDir = tempDir.resolve("res");
    Files.createDirectories(srcDir.resolve("partials"));

    Files.writeString(srcDir.resolve("index.vtl"), "Main Page", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("partials/header.vtl"), "Header Content", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("partials/footer.vtl"), "Footer Content", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .resourceOutputDirectory(resDir)
            .build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.compiledCount()).isEqualTo(3);
    assertThat(result.artifacts()).hasSize(3);

    // templates.idx must be written to resourceOutputDirectory and sorted by templateId
    Path idxFile = resDir.resolve("META-INF/viet-template/templates.idx");
    assertThat(idxFile).isRegularFile();
    List<String> lines = Files.readAllLines(idxFile, StandardCharsets.UTF_8);
    assertThat(lines).hasSize(3);
    assertThat(lines.get(0)).startsWith("index.vtl=");
    assertThat(lines.get(1)).startsWith("partials/footer.vtl=");
    assertThat(lines.get(2)).startsWith("partials/header.vtl=");
  }

  @Test
  @DisplayName("3. Deterministic compilation produces byte-for-byte identical output files")
  void testDeterministicCompilation(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path out1 = tempDir.resolve("out1");
    Path out2 = tempDir.resolve("out2");
    Files.createDirectories(srcDir.resolve("components"));

    Files.writeString(
        srcDir.resolve("page.vtl"), "Hello $name, count: $count", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("components/card.vtl"), "Card: $card.title", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request1 =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(out1).build();
    TemplateAotRequest request2 =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(out2).build();

    TemplateAotResult result1 = compiler.compile(request1);
    TemplateAotResult result2 = compiler.compile(request2);

    assertThat(result1.isSuccess()).isTrue();
    assertThat(result2.isSuccess()).isTrue();
    assertThat(result1.artifacts()).hasSize(2);
    assertThat(result2.artifacts()).hasSize(2);

    // Compare each .class byte-for-byte
    for (int i = 0; i < result1.artifacts().size(); i++) {
      TemplateAotArtifact a1 = result1.artifacts().get(i);
      TemplateAotArtifact a2 = result2.artifacts().get(i);
      assertThat(a1.templateId()).isEqualTo(a2.templateId());
      assertThat(a1.className()).isEqualTo(a2.className());
      assertThat(a1.fingerprint()).isEqualTo(a2.fingerprint());

      byte[] bytes1 = Files.readAllBytes(a1.outputFile());
      byte[] bytes2 = Files.readAllBytes(a2.outputFile());
      assertThat(bytes1).isEqualTo(bytes2);
    }

    // Compare templates.idx byte-for-byte
    Path idx1 = out1.resolve("META-INF/viet-template/templates.idx");
    Path idx2 = out2.resolve("META-INF/viet-template/templates.idx");
    assertThat(Files.readAllBytes(idx1)).isEqualTo(Files.readAllBytes(idx2));

    // Compare aot-state byte-for-byte
    Path state1 = out1.resolve("META-INF/viet-template/aot-state");
    Path state2 = out2.resolve("META-INF/viet-template/aot-state");
    assertThat(Files.readAllBytes(state1)).isEqualTo(Files.readAllBytes(state2));
  }

  @Test
  @DisplayName("4. Incremental compilation skips unchanged templates and recompiles modified ones")
  void testIncrementalCompilation(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path file1 = srcDir.resolve("t1.vtl");
    Path file2 = srcDir.resolve("t2.vtl");
    Path file3 = srcDir.resolve("t3.vtl");
    Files.writeString(file1, "Template 1", StandardCharsets.UTF_8);
    Files.writeString(file2, "Template 2", StandardCharsets.UTF_8);
    Files.writeString(file3, "Template 3", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .incremental(true)
            .build();

    // Pass 1: Fresh build compiles all 3
    TemplateAotResult r1 = compiler.compile(request);
    assertThat(r1.isSuccess()).isTrue();
    assertThat(r1.compiledCount()).isEqualTo(3);
    assertThat(r1.skippedCount()).isEqualTo(0);
    assertThat(r1.deletedCount()).isEqualTo(0);
    assertThat(r1.artifacts()).hasSize(3);

    // Pass 2: Unchanged templates -> 0 compiled, 3 skipped
    TemplateAotResult r2 = compiler.compile(request);
    assertThat(r2.isSuccess()).isTrue();
    assertThat(r2.compiledCount()).isEqualTo(0);
    assertThat(r2.skippedCount()).isEqualTo(3);
    assertThat(r2.deletedCount()).isEqualTo(0);
    assertThat(r2.artifacts()).hasSize(3);

    // Pass 3: Modify t2.vtl -> 1 compiled, 2 skipped
    Files.writeString(file2, "Template 2 Modified", StandardCharsets.UTF_8);
    TemplateAotResult r3 = compiler.compile(request);
    assertThat(r3.isSuccess()).isTrue();
    assertThat(r3.compiledCount()).isEqualTo(1);
    assertThat(r3.skippedCount()).isEqualTo(2);
    assertThat(r3.deletedCount()).isEqualTo(0);
    assertThat(r3.artifacts()).hasSize(3);
  }

  @Test
  @DisplayName(
      "5. Stale output removal deletes stale class files and updates index when template is"
          + " deleted")
  void testStaleOutputRemoval(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Path file1 = srcDir.resolve("keep.vtl");
    Path file2 = srcDir.resolve("to_delete.vtl");
    Files.writeString(file1, "Keep", StandardCharsets.UTF_8);
    Files.writeString(file2, "To Delete", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .incremental(true)
            .build();

    TemplateAotResult r1 = compiler.compile(request);
    assertThat(r1.isSuccess()).isTrue();
    assertThat(r1.compiledCount()).isEqualTo(2);

    TemplateAotArtifact deleteArtifact =
        r1.artifacts().stream()
            .filter(a -> a.templateId().value().equals("to_delete.vtl"))
            .findFirst()
            .orElseThrow();
    Path staleClassFile = deleteArtifact.outputFile();
    assertThat(staleClassFile).isRegularFile();

    // Delete source template
    Files.delete(file2);

    // Recompile
    TemplateAotResult r2 = compiler.compile(request);
    assertThat(r2.isSuccess()).isTrue();
    assertThat(r2.compiledCount()).isEqualTo(0);
    assertThat(r2.skippedCount()).isEqualTo(1);
    assertThat(r2.deletedCount()).isEqualTo(1);
    assertThat(r2.artifacts()).hasSize(1);
    assertThat(r2.artifacts().get(0).templateId()).isEqualTo(TemplateId.of("keep.vtl"));

    // Stale class file must be deleted
    assertThat(staleClassFile).doesNotExist();

    // templates.idx must no longer contain to_delete.vtl
    Path idxFile = outDir.resolve("META-INF/viet-template/templates.idx");
    String idx = Files.readString(idxFile, StandardCharsets.UTF_8);
    assertThat(idx).doesNotContain("to_delete.vtl");
    assertThat(idx).contains("keep.vtl=");
  }

  @Test
  @DisplayName(
      "6. Syntax error diagnostics report source path, line, column, code, and formatted message")
  void testSyntaxErrorDiagnostics(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("syntax_error.vtl"), "#if (\nHello\n", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics()).isNotEmpty();

    TemplateAotDiagnostic diag = result.diagnostics().get(0);
    assertThat(diag.templateId()).isEqualTo(TemplateId.of("syntax_error.vtl"));
    assertThat(diag.sourcePath()).isEqualTo("syntax_error.vtl");
    assertThat(diag.startLine()).isGreaterThan(0);
    assertThat(diag.startColumn()).isGreaterThan(0);
    assertThat(diag.code()).isNotNull();
    assertThat(diag.message()).isNotEmpty();

    String formatted = diag.formattedMessage();
    assertThat(formatted)
        .contains("syntax_error.vtl:" + diag.startLine() + ":" + diag.startColumn());
    assertThat(formatted).contains("[" + diag.code().qualifiedCode() + "]");
    assertThat(formatted).contains(diag.message());
    assertThat(diag.toString()).isEqualTo(formatted);

    // In case of error, no corrupt index or state should be written
    assertThat(outDir.resolve("META-INF/viet-template/templates.idx")).doesNotExist();
    assertThat(outDir.resolve("META-INF/viet-template/aot-state")).doesNotExist();
  }

  @Test
  @DisplayName("7. Semantic error diagnostics survive compiler facade")
  void testSemanticErrorDiagnostics(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(
        srcDir.resolve("invalid_semantic.vtl"),
        "#foreach ($item in 123)\n$item\n#end",
        StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.templateId().value().equals("invalid_semantic.vtl")
                    && d.severity() == DiagnosticSeverity.ERROR
                    && d.message().contains("not iterable"));
  }

  @Test
  @DisplayName("8. Fail-on-warning option fails compilation when warnings exist")
  void testFailOnWarningOption(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir1 = tempDir.resolve("out1");
    Path outDir2 = tempDir.resolve("out2");
    Files.createDirectories(srcDir);

    // Untyped template produces dynamic dispatch warning in AOT
    Files.writeString(srcDir.resolve("dynamic.vtl"), "Hello $name!", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();

    // 8a. failOnWarning = false: succeeds with warning
    TemplateAotRequest requestNoFail =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir1)
            .failOnWarning(false)
            .build();

    TemplateAotResult r1 = compiler.compile(requestNoFail);
    assertThat(r1.isSuccess()).isTrue();
    assertThat(r1.hasWarnings()).isTrue();
    assertThat(r1.hasErrors()).isFalse();
    assertThat(r1.compiledCount()).isEqualTo(1);
    assertThat(outDir1.resolve("META-INF/viet-template/templates.idx")).isRegularFile();

    // 8b. failOnWarning = true: fails due to warning
    TemplateAotRequest requestFail =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir2)
            .failOnWarning(true)
            .build();

    TemplateAotResult r2 = compiler.compile(requestFail);
    assertThat(r2.isSuccess()).isFalse();
    assertThat(r2.hasWarnings()).isTrue();
    assertThat(r2.hasErrors()).isFalse();
    assertThat(r2.diagnostics()).anyMatch(d -> d.severity() == DiagnosticSeverity.WARNING);
    assertThat(outDir2.resolve("META-INF/viet-template/templates.idx")).doesNotExist();
  }

  @Test
  @DisplayName("9. Empty template directory succeeds gracefully with 0 compiled")
  void testEmptyTemplateDirectory(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();

    TemplateAotResult result = compiler.compile(request);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.compiledCount()).isEqualTo(0);
    assertThat(result.skippedCount()).isEqualTo(0);
    assertThat(result.deletedCount()).isEqualTo(0);
    assertThat(result.artifacts()).isEmpty();
    assertThat(result.diagnostics()).isEmpty();

    Path idxFile = outDir.resolve("META-INF/viet-template/templates.idx");
    assertThat(idxFile).isRegularFile();
    assertThat(Files.readString(idxFile, StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  @DisplayName("10. Path traversal defense rejects malicious relative paths and package prefixes")
  void testPathTraversalDefense(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    // 10a. Malicious packagePrefix attempting traversal outside outputDirectory
    assertThatThrownBy(
            () ->
                TemplateAotRequest.builder()
                    .sourceDirectory(srcDir)
                    .outputDirectory(outDir)
                    .packagePrefix("../../evil")
                    .build())
        .isInstanceOf(IllegalArgumentException.class);

    // 10b. PackagePrefix with path separators
    assertThatThrownBy(
            () ->
                TemplateAotRequest.builder()
                    .sourceDirectory(srcDir)
                    .outputDirectory(outDir)
                    .packagePrefix("com/evil/package")
                    .build())
        .isInstanceOf(IllegalArgumentException.class);

    // 10c. Validation: outputDirectory must not be null
    assertThatThrownBy(
            () ->
                TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(null).build())
        .isInstanceOf(NullPointerException.class);

    // 10d. Validation: at least one source directory required
    assertThatThrownBy(() -> TemplateAotRequest.builder().outputDirectory(outDir).build())
        .isInstanceOf(IllegalArgumentException.class);

    // 10e. State file with malicious relativeClassPath path traversal is rejected
    Path metaInf = outDir.resolve("META-INF/viet-template");
    Files.createDirectories(metaInf);
    Files.writeString(
        metaInf.resolve("aot-state"),
        "t.vtl|hash|../../etc/passwd.class|com.evil.Evil|fp\n",
        StandardCharsets.UTF_8);
    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest reqWithBadState =
        TemplateAotRequest.builder().sourceDirectory(srcDir).outputDirectory(outDir).build();
    assertThatThrownBy(() -> compiler.compile(reqWithBadState))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  @DisplayName("11. Exclude patterns filter out matching templates")
  void testExcludePatterns(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src");
    Path outDir = tempDir.resolve("out");
    Files.createDirectories(srcDir);

    Files.writeString(srcDir.resolve("included.vtl"), "Included", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("excluded.vtl"), "Excluded", StandardCharsets.UTF_8);

    TemplateAotCompiler compiler = TemplateAotCompiler.create();
    TemplateAotRequest request =
        TemplateAotRequest.builder()
            .sourceDirectory(srcDir)
            .outputDirectory(outDir)
            .excludePattern("**/excluded.vtl")
            .build();

    TemplateAotResult result = compiler.compile(request);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.compiledCount()).isEqualTo(1);
    assertThat(result.artifacts()).hasSize(1);
    assertThat(result.artifacts().get(0).templateId()).isEqualTo(TemplateId.of("included.vtl"));
  }
}
