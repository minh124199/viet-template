package io.github.minh124199.viettemplate.intellij.runtime;

import com.intellij.openapi.diagnostic.Logger;
import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the Viet Template Language Server JAR and constructs the execution command line.
 */
public class LspServerLauncher {

  private static final Logger LOG = Logger.getInstance(LspServerLauncher.class);
  private static final String BUNDLED_JAR_RESOURCE = "/server/viet-template-lsp.jar";

  private final VietTemplateSettings settings;

  public LspServerLauncher() {
    this(VietTemplateSettings.getInstance());
  }

  public LspServerLauncher(VietTemplateSettings settings) {
    this.settings = settings;
  }

  /**
   * Locates the language server JAR file by searching:
   * 1. Custom configured server JAR path in settings
   * 2. Bundled server JAR inside plugin distribution (/server/viet-template-lsp.jar)
   * 3. Repository / development build paths
   *
   * @return Path to the runnable language server JAR
   * @throws IllegalStateException if the JAR cannot be found
   */
  public Path locateServerJar() {
    // 1. Settings custom path
    if (settings != null && !settings.getServerJarPath().isBlank()) {
      Path customPath = Paths.get(settings.getServerJarPath().trim());
      if (Files.isRegularFile(customPath)) {
        return customPath.toAbsolutePath().normalize();
      }
      LOG.warn("Custom server JAR path does not exist: " + customPath);
    }

    // 2. Bundled resource
    URL bundledUrl = getClass().getResource(BUNDLED_JAR_RESOURCE);
    if (bundledUrl != null) {
      try {
        if ("file".equalsIgnoreCase(bundledUrl.getProtocol())) {
          Path filePath = Paths.get(bundledUrl.toURI());
          if (Files.isRegularFile(filePath)) {
            return filePath.toAbsolutePath().normalize();
          }
        } else {
          // Extract from JAR or IDE plugin container to temp file
          Path tempDir = Files.createTempDirectory("viet-template-lsp-cache-");
          Path tempJar = tempDir.resolve("viet-template-lsp.jar");
          tempJar.toFile().deleteOnExit();
          tempDir.toFile().deleteOnExit();
          try (InputStream in = bundledUrl.openStream()) {
            Files.copy(in, tempJar, StandardCopyOption.REPLACE_EXISTING);
          }
          return tempJar.toAbsolutePath().normalize();
        }
      } catch (Exception e) {
        LOG.warn("Failed to extract bundled server JAR from " + bundledUrl, e);
      }
    }

    // 3. Fallback repository and build paths
    List<Path> candidatePaths = List.of(
        Paths.get("src/main/resources/server/viet-template-lsp.jar"),
        Paths.get("build/server/viet-template-lsp.jar"),
        Paths.get("../vscode/server/viet-template-lsp.jar"),
        Paths.get("../../editors/vscode/server/viet-template-lsp.jar"),
        Paths.get("editors/vscode/server/viet-template-lsp.jar")
    );

    for (Path candidate : candidatePaths) {
      if (Files.isRegularFile(candidate)) {
        return candidate.toAbsolutePath().normalize();
      }
    }

    throw new IllegalStateException(
        "Could not locate viet-template-lsp.jar. Please ensure the plugin is properly packaged or configure the path in settings."
    );
  }

  /**
   * Constructs the command argument list to launch the language server.
   */
  public List<String> buildCommand(Path javaExecutable, Path serverJar, List<String> vmArgs) {
    List<String> command = new ArrayList<>();
    command.add(javaExecutable.toAbsolutePath().toString());
    command.add("-Dfile.encoding=UTF-8");

    if (vmArgs != null) {
      for (String arg : vmArgs) {
        if (arg != null && !arg.isBlank()) {
          command.add(arg.trim());
        }
      }
    }

    command.add("-jar");
    command.add(serverJar.toAbsolutePath().toString());
    return command;
  }

  /**
   * Creates a ProcessBuilder for launching the language server.
   */
  public ProcessBuilder createProcessBuilder(Path javaExecutable, Path serverJar, List<String> vmArgs, File workingDir) {
    List<String> command = buildCommand(javaExecutable, serverJar, vmArgs);
    ProcessBuilder pb = new ProcessBuilder(command);
    if (workingDir != null && workingDir.isDirectory()) {
      pb.directory(workingDir);
    }
    return pb;
  }
}
