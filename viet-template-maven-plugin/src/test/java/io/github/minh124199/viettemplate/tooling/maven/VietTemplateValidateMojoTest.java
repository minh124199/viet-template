package io.github.minh124199.viettemplate.tooling.maven;

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

class VietTemplateValidateMojoTest {

  @Test
  @DisplayName("Valid templates pass validation without throwing any exceptions")
  void testValidateValidTemplates(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("hello.vtl"), "Hello, $name! Welcome.", StandardCharsets.UTF_8);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Fails validation when template has syntax errors")
  void testValidateSyntaxError(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("invalid.vtl"), "#if($user)", StandardCharsets.UTF_8);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("Viet Template validation failed");
  }

  @Test
  @DisplayName("Fails validation when template has missing static dependencies")
  void testValidateMissingStaticDependency(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("page.vtl"), "<div>#parse('missing.vtl')</div>", StandardCharsets.UTF_8);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("Viet Template validation failed");
  }

  @Test
  @DisplayName("Skips validation when skip is true")
  void testValidateSkip(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("invalid.vtl"), "#if($broken", StandardCharsets.UTF_8);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setSkip(true);

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Handles non-existent source directory gracefully")
  void testValidateNonExistentDirectory(@TempDir Path tempDir) {
    Path srcDir = tempDir.resolve("does-not-exist");

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Handles empty source directory gracefully")
  void testValidateEmptyDirectory(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("empty");
    Files.createDirectories(srcDir);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());

    assertThatCode(mojo::execute).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Throws MojoExecutionException on invalid profile configuration")
  void testValidateInvalidProfile(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(srcDir.resolve("test.vtl"), "Test", StandardCharsets.UTF_8);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setProfile("INVALID_PROFILE_NAME");

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoExecutionException.class)
        .hasMessageContaining("Invalid profile configuration");
  }

  @Test
  @DisplayName("Fails validation when failOnWarning is true and contract warning occurs")
  void testValidateFailOnWarning(@TempDir Path tempDir) throws Exception {
    Path srcDir = tempDir.resolve("src/main/viet-template");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("profile.vtl"), "Hello $user.unknownProp", StandardCharsets.UTF_8);
    Files.writeString(
        srcDir.resolve("profile.vtl.contract"), "user=java.lang.String\n", StandardCharsets.UTF_8);

    VietTemplateValidateMojo mojo = new VietTemplateValidateMojo();
    mojo.setSourceDirectory(srcDir.toFile());
    mojo.setTypeChecking("WARN");
    mojo.setFailOnWarning(true);

    assertThatThrownBy(mojo::execute)
        .isInstanceOf(MojoFailureException.class)
        .hasMessageContaining("Viet Template validation failed");
  }
}
