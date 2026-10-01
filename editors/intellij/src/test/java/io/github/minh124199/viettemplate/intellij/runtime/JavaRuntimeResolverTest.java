package io.github.minh124199.viettemplate.intellij.runtime;

import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaRuntimeResolverTest {

  @Test
  void shouldParseVariousJavaVersionStrings() {
    assertThat(JavaRuntimeResolver.parseMajorVersion("openjdk version \"21.0.12.1\" 2024-04-16")).isEqualTo(21);
    assertThat(JavaRuntimeResolver.parseMajorVersion("java version \"21\" 2023-09-19")).isEqualTo(21);
    assertThat(JavaRuntimeResolver.parseMajorVersion("openjdk version \"25-ea\" 2025-03-18")).isEqualTo(25);
    assertThat(JavaRuntimeResolver.parseMajorVersion("java version \"17.0.8\" 2023-07-18 LTS")).isEqualTo(17);
    assertThat(JavaRuntimeResolver.parseMajorVersion("java version \"1.8.0_352\"")).isEqualTo(8);
    assertThat(JavaRuntimeResolver.parseMajorVersion("")).isEqualTo(-1);
    assertThat(JavaRuntimeResolver.parseMajorVersion(null)).isEqualTo(-1);
    assertThat(JavaRuntimeResolver.parseMajorVersion("unrelated output without version tag")).isEqualTo(-1);
  }

  @Test
  void shouldRespectPrecedenceWhenResolvingJavaHome(@TempDir Path tempDir) throws IOException {
    Path customDir = tempDir.resolve("custom-jdk");
    Path envDir = tempDir.resolve("env-jdk");
    Path sysDir = tempDir.resolve("sys-jdk");

    Files.createDirectories(customDir);
    Files.createDirectories(envDir);
    Files.createDirectories(sysDir);

    // Custom takes precedence
    Path resolved = JavaRuntimeResolver.resolveJavaHome(customDir.toString(), envDir.toString(), sysDir.toString());
    assertThat(resolved).isEqualTo(customDir);

    // Env takes precedence if custom is blank
    resolved = JavaRuntimeResolver.resolveJavaHome("", envDir.toString(), sysDir.toString());
    assertThat(resolved).isEqualTo(envDir);

    // Sys takes precedence if custom and env are blank
    resolved = JavaRuntimeResolver.resolveJavaHome(null, "", sysDir.toString());
    assertThat(resolved).isEqualTo(sysDir);

    // None valid returns null
    resolved = JavaRuntimeResolver.resolveJavaHome(null, null, null);
    assertThat(resolved).isNull();
  }

  @Test
  void shouldFindExecutableInJavaHome(@TempDir Path tempDir) throws IOException {
    Path binDir = tempDir.resolve("bin");
    Files.createDirectories(binDir);

    Path javaBin = binDir.resolve("java");
    Files.createFile(javaBin);
    File javaFile = javaBin.toFile();
    javaFile.setExecutable(true);

    Path found = JavaRuntimeResolver.findExecutableInJavaHome(tempDir);
    assertThat(found).isNotNull();
    assertThat(found.getFileName().toString()).startsWith("java");

    // Missing bin directory returns null
    Path emptyDir = tempDir.resolve("empty");
    Files.createDirectories(emptyDir);
    assertThat(JavaRuntimeResolver.findExecutableInJavaHome(emptyDir)).isNull();
  }

  @Test
  void shouldValidateActiveJvmMeetsMinimumVersion() {
    JavaRuntimeResolver resolver = new JavaRuntimeResolver(new VietTemplateSettings());
    Path javaExecutable = resolver.resolveJavaExecutable();
    assertThat(javaExecutable).isNotNull();
    assertThat(Files.isExecutable(javaExecutable)).isTrue();

    Path validated = resolver.resolveAndValidateJavaExecutable();
    assertThat(validated).isNotNull();
    assertThat(Files.isExecutable(validated)).isTrue();

    int version = JavaRuntimeResolver.inspectJavaMajorVersion(validated);
    assertThat(version).isGreaterThanOrEqualTo(21);
  }
}
