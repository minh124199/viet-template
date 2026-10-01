package io.github.minh124199.viettemplate.intellij.lsp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.github.minh124199.viettemplate.intellij.runtime.JavaRuntimeResolver;
import io.github.minh124199.viettemplate.intellij.runtime.LspServerLauncher;
import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Project-level service that manages the lifecycle of the Viet Template Language Server client.
 */
public class VietTemplateLspServerManager implements Disposable {

  private static final Logger LOG = Logger.getInstance(VietTemplateLspServerManager.class);

  private final Project project;
  private final VietTemplateSettings customSettings;
  private final Object lock = new Object();
  private VietTemplateLspClient client;

  public VietTemplateLspServerManager(@NotNull Project project) {
    this(project, null);
  }

  public VietTemplateLspServerManager(@NotNull Project project, @Nullable VietTemplateSettings customSettings) {
    this.project = project;
    this.customSettings = customSettings;
  }

  public static VietTemplateLspServerManager getInstance(@NotNull Project project) {
    return project.getService(VietTemplateLspServerManager.class);
  }

  private VietTemplateSettings resolveSettings() {
    if (customSettings != null) {
      return customSettings;
    }
    return ApplicationManager.getApplication() != null ? VietTemplateSettings.getInstance() : new VietTemplateSettings();
  }

  /**
   * Returns the running LSP client, launching it lazily if not currently running.
   */
  @Nullable
  public VietTemplateLspClient getClient() {
    synchronized (lock) {
      if (client != null && client.isRunning()) {
        return client;
      }
      try {
        startServer();
        return client;
      } catch (Exception e) {
        LOG.warn("Failed to start Viet Template Language Server", e);
        return null;
      }
    }
  }

  /**
   * Starts a fresh server process.
   */
  public void startServer() throws IOException {
    synchronized (lock) {
      stopServer();

      VietTemplateSettings settings = resolveSettings();
      JavaRuntimeResolver resolver = new JavaRuntimeResolver(settings);
      Path javaExec = resolver.resolveAndValidateJavaExecutable();

      LspServerLauncher launcher = new LspServerLauncher(settings);
      Path serverJar = launcher.locateServerJar();

      List<String> vmArgs = (settings != null) ? settings.getVmArgs() : List.of();
      File workingDir = (project.getBasePath() != null) ? new File(project.getBasePath()) : null;

      ProcessBuilder pb = launcher.createProcessBuilder(javaExec, serverJar, vmArgs, workingDir);
      client = new VietTemplateLspClient(pb);

      String rootUri = (project.getBasePath() != null) ? new File(project.getBasePath()).toURI().toString() : "";
      client.start(rootUri);
    }
  }

  /**
   * Stops the server if running.
   */
  public void stopServer() {
    synchronized (lock) {
      if (client != null) {
        try {
          client.stop();
        } catch (Exception e) {
          LOG.warn("Error stopping Viet Template LSP client", e);
        }
        client = null;
      }
    }
  }

  /**
   * Restarts the language server process.
   */
  public void restart() {
    synchronized (lock) {
      stopServer();
      try {
        startServer();
      } catch (Exception e) {
        LOG.warn("Failed to restart Viet Template LSP server", e);
      }
    }
  }

  @Override
  public void dispose() {
    stopServer();
  }
}
