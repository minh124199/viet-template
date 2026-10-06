package io.github.minh124199.viettemplate.tooling.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateExplainMojoTest {

  @Test
  @DisplayName("Explains typed template with String output specialization")
  void testExplainTypedStringSpecialization(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("greeting.vtl"), "Hello, $name! Welcome.", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("greeting.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/explain-str.txt");

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("WRITE_STRING_SPECIALIZED");
    assertThat(content).contains("BytecodeRuntimeBridge.writeString");
    assertThat(content).contains("Total Templates: 1");
  }

  @Test
  @DisplayName("Explains typed template with Integer output specialization")
  void testExplainTypedIntegerSpecialization(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("counter.vtl"), "Current count is $count items.", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("counter.vtl.contract"), "count=Integer\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/explain-int.txt");

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("WRITE_INTEGER_SPECIALIZED");
    assertThat(content).contains("BytecodeRuntimeBridge.writeInteger");
  }

  @Test
  @DisplayName("Explains untyped template requiring dynamic fallback")
  void testExplainDynamicFallback(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("dynamic.vtl"), "Value: $item.property", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/explain-dyn.txt");

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());
    mojo.setFailOnDynamicFallback(false);

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("AOT_OK_WITH_DYNAMIC_SITES");
    assertThat(content).contains("UNTYPED_TEMPLATE");
  }

  @Test
  @DisplayName("Explains single template when filtered via template parameter")
  void testExplainSingleTemplateFilter(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("tmpl1.vtl"), "Template 1: $name", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("tmpl2.vtl"), "Template 2: $name", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/explain-filter.txt");

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputFile(outFile.toFile());
    mojo.setTemplate("tmpl1.vtl");

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("Template Explanation: tmpl1.vtl");
    assertThat(content).doesNotContain("Template Explanation: tmpl2.vtl");
    assertThat(content).contains("Total Templates: 1");
  }

  @Test
  @DisplayName("Generates JSON formatted explanation to specified output file")
  void testExplainJsonFormatAndOutputFile(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("hello.vtl"), "Hello, $name!", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("hello.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    Path outFile = tempDir.resolve("target/explain.json");

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setFormat("json");
    mojo.setOutputFile(outFile.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    assertThat(outFile).isRegularFile();
    String content = Files.readString(outFile, StandardCharsets.UTF_8);
    assertThat(content).contains("\"formatVersion\": 1");
    assertThat(content).contains("\"totalTemplates\": 1");
    assertThat(content).contains("\"success\": true");
  }

  @Test
  @DisplayName("Handles non-existent source directory gracefully")
  void testExplainNonExistentDirectory(@TempDir Path tempDir) {
    Path srcDir = tempDir.resolve("does-not-exist");

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Handles empty source directory gracefully")
  void testExplainEmptyDirectory(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("empty");
    Files.createDirectories(srcDir);

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Skips explanation when skip is true")
  void testExplainSkip(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("invalid.vtl"), "#if($broken", StandardCharsets.UTF_8);

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setSkip(true);

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "Fails explanation when failOnDynamicFallback is true and template requires fallback")
  void testFailOnDynamicFallback(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("dyn.vtl"), "Hello $user.unknownField", StandardCharsets.UTF_8);

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setFailOnDynamicFallback(true);

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("dynamic fallback");
  }

  @Test
  @DisplayName("Fails explanation when template has syntax errors")
  void testExplainSyntaxError(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("invalid.vtl"), "#if($broken", StandardCharsets.UTF_8);

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("Viet Template explanation failed");
  }

  @Test
  @DisplayName("Throws MojoExecutionException on invalid profile configuration")
  void testExplainInvalidProfile(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("test.vtl"), "Test", StandardCharsets.UTF_8);

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setProfile("INVALID_PROFILE_NAME");

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoExecutionException.class)
        .hasMessageContaining("Invalid profile configuration");
  }

  @Test
  @DisplayName("Throws MojoExecutionException on invalid encoding")
  void testExplainInvalidEncoding(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("test.vtl"), "Test", StandardCharsets.UTF_8);

    VietTemplateExplainMojo mojo = new VietTemplateExplainMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setEncoding("INVALID_ENCODING_NAME");

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoExecutionException.class)
        .hasMessageContaining("Invalid encoding");
  }
}
