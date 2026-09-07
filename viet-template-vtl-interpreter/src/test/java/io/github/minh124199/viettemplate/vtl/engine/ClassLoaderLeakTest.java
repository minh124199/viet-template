package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.vtl.engine.cache.CompiledTemplateHandle;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;

class ClassLoaderLeakTest {

  @Test
  void invalidatingTemplateReleasesClassLoaderForGarbageCollection() {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("leak_check.vm");
    repo.put(id, "Hello $name!");

    VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    WeakReference<ClassLoader> clWeakRef = loadAndGetClassLoaderWeakRef(engine, id);

    // Invalidate the template from the engine cache
    engine.invalidate(id);

    // Run GC in a bounded loop to reclaim unreferenced classloader
    boolean collected = false;
    for (int i = 0; i < 50; i++) {
      System.gc();
      System.runFinalization();
      if (clWeakRef.get() == null) {
        collected = true;
        break;
      }
      try {
        Thread.sleep(20L);
      } catch (InterruptedException ignored) {
      }
    }

    engine.close();
    assertThat(collected)
        .as("TemplateClassLoader should be garbage collected after invalidation")
        .isTrue();
  }

  private WeakReference<ClassLoader> loadAndGetClassLoaderWeakRef(
      VtlTemplateEngine engine, TemplateId id) {
    Template template = engine.get(id);
    assertThat(template).isInstanceOf(VtlTemplate.class);
    VtlTemplate vtlTemplate = (VtlTemplate) template;
    CompiledTemplateHandle handle = vtlTemplate.handle();
    assertThat(handle.classLoader()).isPresent();

    return new WeakReference<>(handle.classLoader().get());
  }
}
