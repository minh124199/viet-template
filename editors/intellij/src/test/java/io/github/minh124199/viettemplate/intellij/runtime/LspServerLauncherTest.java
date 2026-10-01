package io.github.minh124199.viettemplate.intellij.runtime;

import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LspServerLauncherTest {

  @Test
  void shouldBuildCorrectProcessCommand() {
    LspServerLauncher launcher = new LspServerLauncher(new VietTemplateSettings());
    Path javaExec = Path.of("/fake/bin/java");
    Path serverJar = Path.of("/fake/viet-template-lsp.jar");
    List<String> vmArgs = List.of("-Xms128m", "-Xmx512m");

    List<String> command = launcher.buildCommand(javaExec, serverJar, vmArgs);
    assertThat(command).containsExactly(
        javaExec.toAbsolutePath().toString(),
        "-Dfile.encoding=UTF-8",
        "-Xms128m",
        "-Xmx512m",
        "-jar",
        serverJar.toAbsolutePath().toString()
    );
  }

  @Test
  void shouldPrioritizeCustomServerJarPathFromSettings(@TempDir Path tempDir) throws IOException {
    Path customJar = tempDir.resolve("custom-server.jar");
    Files.createFile(customJar);

    VietTemplateSettings settings = new VietTemplateSettings();
    settings.setServerJarPath(customJar.toString());

    LspServerLauncher launcher = new LspServerLauncher(settings);
    Path located = launcher.locateServerJar();

    assertThat(located).isEqualTo(customJar.toAbsolutePath().normalize());
  }

  @Test
  void shouldLocateBundledOrRepositoryServerJar() {
    LspServerLauncher launcher = new LspServerLauncher(new VietTemplateSettings());
    Path serverJar = launcher.locateServerJar();

    assertThat(serverJar).isNotNull();
    assertThat(Files.isRegularFile(serverJar)).isTrue();
    assertThat(serverJar.getFileName().toString()).isEqualTo("viet-template-lsp.jar");
  }
}
