package io.github.minh124199.viettemplate.intellij.lsp;

import com.intellij.openapi.project.Project;
import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VietTemplateLspServerManagerTest {

  @TempDir
  Path tempDir;

  private VietTemplateLspServerManager manager;

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.dispose();
    }
  }

  @Test
  void shouldRestartServerProcessAndTerminateOldProcessWithoutLeaks() throws Exception {
    Project project = createProjectProxy(tempDir);
    manager = new VietTemplateLspServerManager(project, new VietTemplateSettings());

    manager.startServer();

    VietTemplateLspClient client1 = manager.getClient();
    assertThat(client1).isNotNull();
    assertThat(client1.isRunning()).isTrue();

    Process p1 = client1.getProcess();
    assertThat(p1).isNotNull();
    assertThat(p1.isAlive()).isTrue();

    manager.restart();

    boolean p1Exited = p1.waitFor(3, TimeUnit.SECONDS);
    assertThat(p1Exited).isTrue();
    assertThat(p1.isAlive()).isFalse();

    VietTemplateLspClient client2 = manager.getClient();
    assertThat(client2).isNotNull();
    assertThat(client2).isNotSameAs(client1);
    assertThat(client2.isRunning()).isTrue();

    Process p2 = client2.getProcess();
    assertThat(p2).isNotNull();
    assertThat(p2).isNotSameAs(p1);
    assertThat(p2.pid()).isNotEqualTo(p1.pid());
    assertThat(p2.isAlive()).isTrue();

    manager.dispose();

    boolean p2Exited = p2.waitFor(3, TimeUnit.SECONDS);
    assertThat(p2Exited).isTrue();
    assertThat(p2.isAlive()).isFalse();
  }

  private Project createProjectProxy(Path basePath) {
    return (Project) Proxy.newProxyInstance(
        Project.class.getClassLoader(),
        new Class<?>[]{Project.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "getBasePath":
              return basePath.toString();
            case "getName":
              return "TestProject";
            case "isDisposed":
              return false;
            case "toString":
              return "MockProject[" + basePath + "]";
            case "hashCode":
              return System.identityHashCode(proxy);
            case "equals":
              return proxy == (args != null && args.length > 0 ? args[0] : null);
            default:
              Class<?> ret = method.getReturnType();
              if (ret.equals(boolean.class)) return false;
              if (ret.equals(int.class)) return 0;
              if (ret.equals(long.class)) return 0L;
              if (ret.equals(double.class)) return 0.0;
              if (ret.equals(float.class)) return 0.0f;
              return null;
          }
        }
    );
  }
}
