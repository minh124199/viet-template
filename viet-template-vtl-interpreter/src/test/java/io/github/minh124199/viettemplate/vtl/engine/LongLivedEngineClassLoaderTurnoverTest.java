package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LongLivedEngineClassLoaderTurnoverTest {

  public static class DynamicTurnoverSubject {
    public String getTurnoverValue() {
      return "turnover-pass";
    }

    public int getGenerationId() {
      return 42;
    }
  }

  public record AppSubject(String message) {}

  @Test
  @DisplayName(
      "Long-lived VtlTemplateEngine must not retain transient ClassLoaders or Classes through"
          + " dynamic call sites")
  void testLongLivedEngineDoesNotRetainTransientClassLoaders(@TempDir Path tempDir)
      throws Throwable {
    byte[] classBytes;
    try (InputStream in =
        DynamicTurnoverSubject.class.getResourceAsStream(
            "LongLivedEngineClassLoaderTurnoverTest$DynamicTurnoverSubject.class")) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      in.transferTo(baos);
      classBytes = baos.toByteArray();
    }
    String className = DynamicTurnoverSubject.class.getName();
    Path packageDir =
        tempDir.resolve(DynamicTurnoverSubject.class.getPackageName().replace('.', '/'));
    Files.createDirectories(packageDir);
    Files.write(
        packageDir.resolve("LongLivedEngineClassLoaderTurnoverTest$DynamicTurnoverSubject.class"),
        classBytes);

    URL[] urls = new URL[] {tempDir.toUri().toURL()};
    TemplateId templateId = TemplateId.of("turnover-test.vtl");
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put(templateId, "[$subject.turnoverValue:$subject.generationId]");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build();
    Template template = engine.get(templateId);

    List<WeakReference<ClassLoader>> classLoaderRefs = new ArrayList<>();
    List<WeakReference<Class<?>>> classRefs = new ArrayList<>();

    // Run across 32 isolated generations to exercise monomorphic, polymorphic, and megamorphic
    // cache transitions
    final int generations = 32;
    for (int gen = 0; gen < generations; gen++) {
      URLClassLoader cl = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
      Class<?> clazz = cl.loadClass(className);
      classLoaderRefs.add(new WeakReference<>(cl));
      classRefs.add(new WeakReference<>(clazz));

      Object subject = clazz.getDeclaredConstructor().newInstance();
      StringTemplateOutput out = new StringTemplateOutput();
      template.render(RenderContext.builder().put("subject", subject).build(), out);
      assertThat(out.toString()).isEqualTo("[turnover-pass:42]");
      cl.close();
    }

    // Force GC to verify that transient ClassLoaders and Classes are collected WITHOUT closing the
    // engine
    boolean allCollected = false;
    for (int i = 0; i < 50; i++) {
      System.gc();
      boolean anyClAlive = classLoaderRefs.stream().anyMatch(ref -> ref.get() != null);
      boolean anyClassAlive = classRefs.stream().anyMatch(ref -> ref.get() != null);
      if (!anyClAlive && !anyClassAlive) {
        allCollected = true;
        break;
      }
      Thread.sleep(20L);
    }

    assertThat(allCollected)
        .as(
            "All transient ClassLoaders and Classes must be collected before engine.close() while"
                + " engine is still active")
        .isTrue();

    // Verify CallSiteRegistry remains bounded
    assertThat(engine.callSiteRegistry().size())
        .isLessThanOrEqualTo(engine.callSiteRegistry().maxCapacity());

    // Verify engine is still fully operational after ClassLoader turnover
    TemplateId appTemplateId = TemplateId.of("app-test.vtl");
    repo.put(appTemplateId, "App: $app.message");
    Template appTemplate = engine.get(appTemplateId);
    StringTemplateOutput appOut = new StringTemplateOutput();
    appTemplate.render(
        RenderContext.builder().put("app", new AppSubject("alive-and-healthy")).build(), appOut);
    assertThat(appOut.toString()).isEqualTo("App: alive-and-healthy");

    // Finally close engine
    engine.close();
    assertThat(engine.callSiteRegistry().size()).isZero();
  }
}
