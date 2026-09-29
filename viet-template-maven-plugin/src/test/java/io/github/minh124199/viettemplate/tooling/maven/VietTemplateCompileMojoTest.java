package io.github.minh124199.viettemplate.tooling.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateCompileMojoTest {

  @Test
  @DisplayName("Compiles valid templates successfully and produces class and index artifacts")
  void testCompileValidTemplates(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path outDir = tempDir.resolve("target/classes");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("hello.vtl"), "Hello, $name! Welcome.", StandardCharsets.UTF_8);

    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputDirectory(outDir.toFile());
    mojo.setResourceOutputDirectory(outDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    Path idxFile = outDir.resolve("META-INF/viet-template/templates.idx");
    assertThat(idxFile).isRegularFile();
    String idxContent = Files.readString(idxFile, StandardCharsets.UTF_8);
    assertThat(idxContent).contains("hello.vtl=");

    String fqcn =
        idxContent
            .lines()
            .filter(l -> l.startsWith("hello.vtl="))
            .findFirst()
            .map(l -> l.substring("hello.vtl=".length()).trim())
            .orElseThrow();
    Path generatedClass = outDir.resolve(fqcn.replace('.', '/') + ".class");
    assertThat(generatedClass).isRegularFile();
  }

  @Test
  @DisplayName("Compiles template with companion contract and generates typed Java facade")
  void testCompileWithTypedFacades(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path outDir = tempDir.resolve("target/classes");
    Path genSourcesDir = tempDir.resolve("target/generated-sources/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("greeting.vtl"), "Hello, $name! Welcome.", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("greeting.vtl.contract"), "name=String\n", StandardCharsets.UTF_8);

    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputDirectory(outDir.toFile());
    mojo.setResourceOutputDirectory(outDir.toFile());
    mojo.setGenerateTypedFacades(true);
    mojo.setGeneratedSourcesDirectory(genSourcesDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();

    Path viewFile =
        genSourcesDir.resolve("io/github/minh124199/viettemplate/generated/GreetingView.java");
    assertThat(viewFile).isRegularFile();
    String viewContent = Files.readString(viewFile, StandardCharsets.UTF_8);
    assertThat(viewContent).contains("public final class GreetingView");
    assertThat(viewContent)
        .contains("public static void render(TemplateOutput output, java.lang.String name)");
  }

  @Test
  @DisplayName("Fails build when template has syntax or compilation errors")
  void testCompileFailureTemplates(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path outDir = tempDir.resolve("target/classes");
    Files.createDirectories(srcDir);
    // Unclosed #if block produces syntax error
    Files.writeString(srcDir.resolve("invalid.vtl"), "#if($user)", StandardCharsets.UTF_8);

    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputDirectory(outDir.toFile());
    mojo.setResourceOutputDirectory(outDir.toFile());

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("Viet Template AOT compilation failed");
  }

  @Test
  @DisplayName("Skips compilation when skip is true")
  void testSkipFlag(@TempDir Path tempDir) {
    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSkip(true);
    mojo.setSourceDirectory(tempDir.resolve("does-not-exist").toFile());
    mojo.setOutputDirectory(tempDir.resolve("target/classes").toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Skips compilation when source directory is empty")
  void testEmptySourceDirectory(@TempDir Path tempDir) throws Exception {
    Path emptyDir = tempDir.resolve("empty-src");
    Files.createDirectories(emptyDir);

    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(emptyDir.toFile());
    mojo.setOutputDirectory(tempDir.resolve("target/classes").toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Skips compilation when source directory does not exist")
  void testNonExistentSourceDirectory(@TempDir Path tempDir) {
    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(tempDir.resolve("non-existent").toFile());
    mojo.setOutputDirectory(tempDir.resolve("target/classes").toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("TypeChecking ERROR fails compilation on contract mismatch")
  void testTypeCheckingErrorFailsCompilation(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path outDir = tempDir.resolve("target/classes");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("user.vtl"), "User: $user.invalidProp", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("user.vtl.contract"), "user=String\n", StandardCharsets.UTF_8);

    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputDirectory(outDir.toFile());
    mojo.setResourceOutputDirectory(outDir.toFile());
    mojo.setTypeChecking("ERROR");

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("Viet Template AOT compilation failed");
  }

  @Test
  @DisplayName("TypeChecking WARN allows compilation to succeed on contract mismatch")
  void testTypeCheckingWarnSucceeds(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Path outDir = tempDir.resolve("target/classes");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("user.vtl"), "User: $user.invalidProp", StandardCharsets.UTF_8);
    Files.writeString(srcDir.resolve("user.vtl.contract"), "user=String\n", StandardCharsets.UTF_8);

    VietTemplateCompileMojo mojo = new VietTemplateCompileMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setOutputDirectory(outDir.toFile());
    mojo.setResourceOutputDirectory(outDir.toFile());
    mojo.setTypeChecking("WARN");

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }
}
