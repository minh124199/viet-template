package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Hardening tests verifying receiver-aware dangerous method handling, universal reflection pivots
 * blocking, host object exposure differences between standard (denylist) and safe (allowlist)
 * profiles, and infrastructure rejection.
 */
class MemberAccessPolicyHardeningTest {

  public static class ApplicationCache {
    public String load(String key) {
      return "cached:" + key;
    }

    public void load(String key, boolean reload) {}
  }

  public static class WorkflowCoordinator {
    public void resume() {}

    public boolean resume(String stepId) {
      return true;
    }
  }

  public static class LifecycleService {
    public void shutdown() {}

    public boolean shutdown(long gracePeriodMillis) {
      return true;
    }
  }

  public static class CustomWaitBean {
    public void wait(int seconds) {}

    public String wait(String message, int seconds) {
      return message;
    }
  }

  public static class CustomThreadSubclass extends Thread {
    public void doWork() {}
  }

  public static class CustomExecutorImpl implements Executor {
    @Override
    public void execute(Runnable command) {
      command.run();
    }

    public void shutdown() {}
  }

  public static class CustomClassLoaderSubclass extends ClassLoader {
    public void load(String name) {}
  }

  @Test
  @DisplayName(
      "Application beans with business methods load, resume, shutdown, wait are permitted under"
          + " standard policy")
  void applicationBeansPermittedUnderStandardPolicy() throws Exception {
    MemberAccessPolicy standard = MemberAccessPolicy.standard();

    // Cache load methods
    assertThat(standard.isMethodPermitted(ApplicationCache.class, "load", 1)).isTrue();
    assertThat(standard.isMethodPermitted(ApplicationCache.class, "load", 2)).isTrue();
    assertThat(
            standard.isMethodPermitted(
                ApplicationCache.class, ApplicationCache.class.getMethod("load", String.class)))
        .isTrue();
    assertThat(
            standard.isMethodPermitted(
                ApplicationCache.class,
                ApplicationCache.class.getMethod("load", String.class, boolean.class)))
        .isTrue();

    // Workflow resume methods
    assertThat(standard.isMethodPermitted(WorkflowCoordinator.class, "resume", 0)).isTrue();
    assertThat(standard.isMethodPermitted(WorkflowCoordinator.class, "resume", 1)).isTrue();
    assertThat(
            standard.isMethodPermitted(
                WorkflowCoordinator.class, WorkflowCoordinator.class.getMethod("resume")))
        .isTrue();
    assertThat(
            standard.isMethodPermitted(
                WorkflowCoordinator.class,
                WorkflowCoordinator.class.getMethod("resume", String.class)))
        .isTrue();

    // Service shutdown methods
    assertThat(standard.isMethodPermitted(LifecycleService.class, "shutdown", 0)).isTrue();
    assertThat(standard.isMethodPermitted(LifecycleService.class, "shutdown", 1)).isTrue();
    assertThat(
            standard.isMethodPermitted(
                LifecycleService.class, LifecycleService.class.getMethod("shutdown")))
        .isTrue();
    assertThat(
            standard.isMethodPermitted(
                LifecycleService.class, LifecycleService.class.getMethod("shutdown", long.class)))
        .isTrue();

    // Custom bean wait method (declaring class != Object.class)
    assertThat(standard.isMethodPermitted(CustomWaitBean.class, "wait", 1)).isTrue();
    assertThat(standard.isMethodPermitted(CustomWaitBean.class, "wait", 2)).isTrue();
    assertThat(
            standard.isMethodPermitted(
                CustomWaitBean.class, CustomWaitBean.class.getMethod("wait", int.class)))
        .isTrue();
    assertThat(
            standard.isMethodPermitted(
                CustomWaitBean.class,
                CustomWaitBean.class.getMethod("wait", String.class, int.class)))
        .isTrue();

    // But Object.wait() inherited by CustomWaitBean is strictly BLOCKED
    assertThat(standard.isMethodPermitted(CustomWaitBean.class, "wait", 0)).isFalse();
    assertThat(
            standard.isMethodPermitted(
                CustomWaitBean.class, CustomWaitBean.class.getMethod("wait")))
        .isFalse();
  }

  @Test
  @DisplayName(
      "Dangerous pivots (System.exit, Runtime.load, Thread.resume, ExecutorService.shutdown,"
          + " Object.wait, getClass) are blocked under standard policy")
  void dangerousPivotsBlockedUnderStandardPolicy() throws Exception {
    MemberAccessPolicy standard = MemberAccessPolicy.standard();

    // System.exit / halt
    assertThat(standard.isMethodPermitted(System.class, "exit", 1)).isFalse();
    assertThat(standard.isMethodPermitted(System.class, System.class.getMethod("exit", int.class)))
        .isFalse();

    // Runtime.load / loadLibrary / halt
    assertThat(standard.isMethodPermitted(Runtime.class, "load", 1)).isFalse();
    assertThat(standard.isMethodPermitted(Runtime.class, "loadLibrary", 1)).isFalse();
    assertThat(standard.isMethodPermitted(Runtime.class, "halt", 1)).isFalse();
    assertThat(
            standard.isMethodPermitted(
                Runtime.class, Runtime.class.getMethod("load", String.class)))
        .isFalse();

    // Thread.resume / suspend / interrupt
    assertThat(standard.isMethodPermitted(Thread.class, "resume", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Thread.class, "suspend", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Thread.class, "interrupt", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Thread.class, Thread.class.getMethod("interrupt")))
        .isFalse();

    // Custom thread subclass
    assertThat(standard.isMethodPermitted(CustomThreadSubclass.class, "resume", 0)).isFalse();
    assertThat(standard.isMethodPermitted(CustomThreadSubclass.class, "interrupt", 0)).isFalse();

    // ExecutorService.shutdown / shutdownNow
    assertThat(standard.isMethodPermitted(ExecutorService.class, "shutdown", 0)).isFalse();
    assertThat(standard.isMethodPermitted(ExecutorService.class, "shutdownNow", 0)).isFalse();
    assertThat(
            standard.isMethodPermitted(
                ExecutorService.class, ExecutorService.class.getMethod("shutdown")))
        .isFalse();

    // Custom executor implementation
    assertThat(standard.isMethodPermitted(CustomExecutorImpl.class, "shutdown", 0)).isFalse();
    assertThat(
            standard.isMethodPermitted(
                CustomExecutorImpl.class, CustomExecutorImpl.class.getMethod("shutdown")))
        .isFalse();

    // ClassLoader.loadClass / loadLibrary
    assertThat(standard.isMethodPermitted(ClassLoader.class, "loadClass", 1)).isFalse();
    assertThat(standard.isMethodPermitted(CustomClassLoaderSubclass.class, "load", 1)).isFalse();

    // Object.wait / notify / notifyAll
    assertThat(standard.isMethodPermitted(Object.class, "wait", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, "wait", 1)).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, "wait", 2)).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, "notify", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, "notifyAll", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, Object.class.getMethod("wait"))).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, Object.class.getMethod("wait", long.class)))
        .isFalse();
    assertThat(
            standard.isMethodPermitted(
                Object.class, Object.class.getMethod("wait", long.class, int.class)))
        .isFalse();
    assertThat(standard.isMethodPermitted(Object.class, Object.class.getMethod("notify")))
        .isFalse();
    assertThat(standard.isMethodPermitted(Object.class, Object.class.getMethod("notifyAll")))
        .isFalse();

    // Universal reflection pivot: getClass()
    assertThat(standard.isMethodPermitted(Object.class, "getClass", 0)).isFalse();
    assertThat(standard.isMethodPermitted(Object.class, Object.class.getMethod("getClass")))
        .isFalse();
    assertThat(standard.isMethodPermitted(String.class, "getClass", 0)).isFalse();
    assertThat(standard.isMethodPermitted(String.class, String.class.getMethod("getClass")))
        .isFalse();
    assertThat(standard.isMethodPermitted(ApplicationCache.class, "getClass", 0)).isFalse();
    assertThat(standard.isPropertyPermitted(String.class, "class")).isFalse();
    assertThat(standard.isPropertyPermitted(ApplicationCache.class, "class")).isFalse();
  }

  @Test
  @DisplayName(
      "Host object exposure: File, Path, URI, URL permitted in standard (denylist) but rejected in"
          + " safe (allowlist)")
  void hostObjectExposureStandardVsSafe() {
    MemberAccessPolicy standard = MemberAccessPolicy.standard();
    MemberAccessPolicy safe = MemberAccessPolicy.safe();
    MemberAccessPolicy mandatorySafe = standard.toSafeProfile();

    // Standard profile permits host objects by default (defense-in-depth denylist for trusted
    // templates)
    assertThat(standard.isClassPermitted(File.class)).isTrue();
    assertThat(standard.isClassPermitted(Path.class)).isTrue();
    assertThat(standard.isClassPermitted(URI.class)).isTrue();
    assertThat(standard.isClassPermitted(URL.class)).isTrue();

    // Safe profile strictly denies host objects (strict allowlist sandbox for untrusted templates)
    assertThat(safe.isClassPermitted(File.class)).isFalse();
    assertThat(safe.isClassPermitted(Path.class)).isFalse();
    assertThat(safe.isClassPermitted(URI.class)).isFalse();
    assertThat(safe.isClassPermitted(URL.class)).isFalse();

    // Mandatory safe profile also strictly denies host objects
    assertThat(mandatorySafe.isClassPermitted(File.class)).isFalse();
    assertThat(mandatorySafe.isClassPermitted(Path.class)).isFalse();
    assertThat(mandatorySafe.isClassPermitted(URI.class)).isFalse();
    assertThat(mandatorySafe.isClassPermitted(URL.class)).isFalse();
  }

  @Test
  @DisplayName("Sensitive infrastructure classes rejected under both standard and safe policies")
  void infrastructureClassesRejectedUnderBothPolicies() {
    MemberAccessPolicy standard = MemberAccessPolicy.standard();
    MemberAccessPolicy safe = MemberAccessPolicy.safe();

    Class<?>[] infraClasses =
        new Class<?>[] {
          ProcessBuilder.class,
          Process.class,
          System.class,
          Runtime.class,
          ClassLoader.class,
          Thread.class,
          ThreadGroup.class,
          Executor.class,
          ExecutorService.class
        };

    for (Class<?> clazz : infraClasses) {
      assertThat(standard.isClassPermitted(clazz))
          .as("Standard policy must reject infrastructure class %s", clazz.getName())
          .isFalse();
      assertThat(safe.isClassPermitted(clazz))
          .as("Safe policy must reject infrastructure class %s", clazz.getName())
          .isFalse();
    }
  }

  @Test
  @DisplayName(
      "Custom policy builder initializes with universal denied methods and permits custom business"
          + " methods")
  void customPolicyBuilderPermitsBusinessMethods() {
    MemberAccessPolicy custom = MemberAccessPolicy.builder().build();

    assertThat(custom.isMethodPermitted(ApplicationCache.class, "load", 1)).isTrue();
    assertThat(custom.isMethodPermitted(WorkflowCoordinator.class, "resume", 0)).isTrue();
    assertThat(custom.isMethodPermitted(LifecycleService.class, "shutdown", 0)).isTrue();
    assertThat(custom.isMethodPermitted(CustomWaitBean.class, "wait", 1)).isTrue();

    // Dangerous pivots remain blocked
    assertThat(custom.isMethodPermitted(System.class, "exit", 1)).isFalse();
    assertThat(custom.isMethodPermitted(Runtime.class, "load", 1)).isFalse();
    assertThat(custom.isMethodPermitted(Thread.class, "resume", 0)).isFalse();
    assertThat(custom.isMethodPermitted(ExecutorService.class, "shutdown", 0)).isFalse();
    assertThat(custom.isMethodPermitted(Object.class, "wait", 0)).isFalse();
    assertThat(custom.isMethodPermitted(String.class, "getClass", 0)).isFalse();

    // Explicit deny overrides permission
    MemberAccessPolicy customWithExplicitDeny =
        MemberAccessPolicy.builder().denyMethod("load").build();
    assertThat(customWithExplicitDeny.isMethodPermitted(ApplicationCache.class, "load", 1))
        .isFalse();
    // But other business methods remain permitted
    assertThat(customWithExplicitDeny.isMethodPermitted(WorkflowCoordinator.class, "resume", 0))
        .isTrue();
  }
}
