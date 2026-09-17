package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.minh124199.viettemplate.api.FilesystemTemplateRepository;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.linker.BoundedWeakClassCache;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerStatistics;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.vtl.engine.watcher.DevelopmentFileWatcher;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VtlTemplateEngineLifecycleTest {

  public static class DynamicTurnoverSubject {
    public String getTurnoverValue() {
      return "turnover-pass";
    }
  }

  private static byte[] readClassBytes(Class<?> clazz) throws Exception {
    String resourceName = clazz.getName().replace('.', '/') + ".class";
    try (InputStream is = clazz.getClassLoader().getResourceAsStream(resourceName)) {
      assertThat(is).isNotNull();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      int read;
      while ((read = is.read(buf)) != -1) {
        baos.write(buf, 0, read);
      }
      return baos.toByteArray();
    }
  }

  private static Thread getWatchThread(DevelopmentFileWatcher watcher) throws Exception {
    Field f = DevelopmentFileWatcher.class.getDeclaredField("watchThread");
    f.setAccessible(true);
    return (Thread) f.get(watcher);
  }

  private static ScheduledExecutorService getDebounceExecutor(DevelopmentFileWatcher watcher)
      throws Exception {
    Field f = DevelopmentFileWatcher.class.getDeclaredField("debounceExecutor");
    f.setAccessible(true);
    return (ScheduledExecutorService) f.get(watcher);
  }

  @Test
  void closeShutsDownWatcherThreadsClearsCachesAndIsIdempotent(@TempDir Path tempDir)
      throws Exception {
    Path templatesDir = tempDir.resolve("templates");
    Files.createDirectories(templatesDir);
    Path tplFile = templatesDir.resolve("page.vtl");
    Files.writeString(tplFile, "Hello $name!");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(FilesystemTemplateRepository.of(templatesDir))
            .hotReload(true)
            .watchDebounceMillis(50L)
            .build();

    // 1. Populate cache by getting the template
    Template template = engine.get("page.vtl");
    assertThat(template).isNotNull();
    assertThat(engine.cache().size()).isGreaterThanOrEqualTo(1);

    // 2. Verify DevelopmentFileWatcher is running with active threads
    assertThat(engine.fileWatcher()).isPresent();
    DevelopmentFileWatcher watcher = engine.fileWatcher().get();
    assertThat(watcher.isRunning()).isTrue();

    Thread watchThread = getWatchThread(watcher);
    ScheduledExecutorService debounceExecutor = getDebounceExecutor(watcher);

    assertThat(watchThread.isAlive()).isTrue();
    assertThat(debounceExecutor.isShutdown()).isFalse();

    // 3. First close: terminates watcher, shuts down threads, clears all internal caches
    engine.close();

    assertThat(watcher.isRunning()).isFalse();
    assertThat(debounceExecutor.isShutdown()).isTrue();
    assertThat(debounceExecutor.isTerminated()).isTrue();

    // Wait briefly for watchThread to complete if join needed a moment
    watchThread.join(1000);
    assertThat(watchThread.isAlive()).isFalse();

    // Internal caches must be fully cleared
    assertThat(engine.cache().size()).isEqualTo(0);
    assertThat(engine.cache().negativeCacheSize()).isEqualTo(0);

    // 4. Repeated close must be cleanly idempotent without errors
    assertThatCode(engine::close).doesNotThrowAnyException();
    assertThatCode(engine::close).doesNotThrowAnyException();

    assertThat(watcher.isRunning()).isFalse();
    assertThat(debounceExecutor.isShutdown()).isTrue();
    assertThat(engine.cache().size()).isEqualTo(0);
  }

  @Test
  void repeatedCloseWithoutWatcherIsIdempotent() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("test.vtl");
    repo.put(id, "Test content");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.AST).build();

    engine.get(id);
    assertThat(engine.cache().size()).isEqualTo(1);

    engine.close();
    assertThat(engine.cache().size()).isEqualTo(0);

    // Repeated close idempotent
    assertThatCode(engine::close).doesNotThrowAnyException();
    assertThatCode(engine::close).doesNotThrowAnyException();
  }

  @Test
  void boundedWeakClassCacheAndCallSiteUnderClassLoaderTurnover(@TempDir Path tempDir)
      throws Throwable {
    byte[] classBytes = readClassBytes(DynamicTurnoverSubject.class);
    String className = DynamicTurnoverSubject.class.getName();

    // Write class bytes into isolated directory structure for URLClassLoader
    Path packageDir =
        tempDir.resolve(DynamicTurnoverSubject.class.getPackageName().replace('.', '/'));
    Files.createDirectories(packageDir);
    Files.write(
        packageDir.resolve("VtlTemplateEngineLifecycleTest$DynamicTurnoverSubject.class"),
        classBytes);

    URL[] urls = new URL[] {tempDir.toUri().toURL()};

    BoundedWeakClassCache<String> weakCache = new BoundedWeakClassCache<>(50);
    DynamicLinker linker = new DynamicLinker();
    DynamicCallSite callSite =
        new DynamicCallSite(
            42,
            MemberKey.propertyGet("turnoverValue"),
            LinkerAccessPolicy.standard(),
            linker,
            new LinkerStatistics());

    List<WeakReference<ClassLoader>> classLoaderRefs = new ArrayList<>();
    List<WeakReference<Class<?>>> classRefs = new ArrayList<>();

    // Simulate classloader turnover across 10 generations
    for (int gen = 0; gen < 10; gen++) {
      URLClassLoader customCl = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
      Class<?> loadedClass = customCl.loadClass(className);

      assertThat(loadedClass.getClassLoader()).isSameAs(customCl);
      classLoaderRefs.add(new WeakReference<>(customCl));
      classRefs.add(new WeakReference<>(loadedClass));

      // 1. Cache in BoundedWeakClassCache
      weakCache.put(loadedClass, "generation-" + gen);
      assertThat(weakCache.get(loadedClass)).isEqualTo("generation-" + gen);

      // 2. Invoke through DynamicCallSite
      Object instance = loadedClass.getDeclaredConstructor().newInstance();
      Object result = callSite.invoke(instance);
      assertThat(result).isEqualTo("turnover-pass");

      // Close URLClassLoader
      customCl.close();
    }

    assertThat(callSite.state()).isEqualTo(DynamicCallSite.State.MEGAMORPHIC);

    // Drop callSite to release MethodHandle -> MethodType -> Class references
    callSite = null;

    // Drop any strong references and trigger GC
    boolean allCollected = false;
    for (int i = 0; i < 50; i++) {
      System.gc();
      System.runFinalization();
      boolean anyClAlive = classLoaderRefs.stream().anyMatch(ref -> ref.get() != null);
      boolean anyClassAlive = classRefs.stream().anyMatch(ref -> ref.get() != null);
      if (!anyClAlive && !anyClassAlive) {
        allCollected = true;
        break;
      }
      Thread.sleep(20L);
    }

    assertThat(allCollected)
        .as("All ephemeral URLClassLoaders and dynamic Classes should be reclaimed without pinning")
        .isTrue();

    // BoundedWeakClassCache should purge stale weak references
    assertThat(weakCache.size()).isEqualTo(0);
  }
}
