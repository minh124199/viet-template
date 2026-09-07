package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class TemplateHotReloaderTest {

  @Test
  void compilesAndCachesTemplateInitially() {
    TemplateHotReloader reloader = new TemplateHotReloader();
    TemplateId id = TemplateId.of("greeting.vm");

    TemplateHotReloader.CacheEntry t1 = reloader.getOrLoadEntry(id, "Hello $name!");
    assertThat(t1.generation()).isEqualTo(1L);
    assertThat(reloader.size()).isEqualTo(1);
    assertThat(reloader.cachedTemplateIds()).containsExactly(id);

    // Same content should return cached instance without incrementing generation
    TemplateHotReloader.CacheEntry t2 = reloader.getOrLoadEntry(id, "Hello $name!");
    assertThat(t2.generation()).isEqualTo(1L);
    assertThat(t2.contentHash()).isEqualTo(t1.contentHash());
    assertThat(t2.template()).isSameAs(t1.template());
  }

  @Test
  void atomicallyReloadsWhenContentChanges() throws IOException {
    TemplateHotReloader reloader = new TemplateHotReloader();
    TemplateId id = TemplateId.of("dynamic.vm");

    TemplateHotReloader.CacheEntry v1 = reloader.getOrLoadEntry(id, "Version 1: $val");
    assertThat(v1.generation()).isEqualTo(1L);

    StringTemplateOutput out1 = new StringTemplateOutput();
    VtlInterpreter interpreter = new VtlInterpreter();
    interpreter.render(
        v1.template(),
        SourceText.of(id.value(), "Version 1: $val"),
        MapRenderContext.of(Map.of("val", "A")),
        out1);
    assertThat(out1.toString()).isEqualTo("Version 1: A");

    // Change content
    TemplateHotReloader.CacheEntry v2 = reloader.getOrLoadEntry(id, "Version 2: $val updated");
    assertThat(v2.generation()).isEqualTo(2L);
    assertThat(v2.contentHash()).isNotEqualTo(v1.contentHash());

    StringTemplateOutput out2 = new StringTemplateOutput();
    interpreter.render(
        v2.template(),
        SourceText.of(id.value(), "Version 2: $val updated"),
        MapRenderContext.of(Map.of("val", "B")),
        out2);
    assertThat(out2.toString()).isEqualTo("Version 2: B updated");
  }

  @Test
  void invalidatesSpecificAndAllTemplates() {
    TemplateHotReloader reloader = new TemplateHotReloader();
    TemplateId id1 = TemplateId.of("t1.vm");
    TemplateId id2 = TemplateId.of("t2.vm");

    reloader.getOrLoad(id1, "Content 1");
    reloader.getOrLoad(id2, "Content 2");
    assertThat(reloader.size()).isEqualTo(2);

    reloader.invalidate(id1);
    assertThat(reloader.size()).isEqualTo(1);
    assertThat(reloader.cachedTemplateIds()).containsExactly(id2);

    reloader.invalidateAll();
    assertThat(reloader.size()).isEqualTo(0);
    assertThat(reloader.cachedTemplateIds()).isEmpty();
  }

  @Test
  void handlesConcurrentAccessAndReloadsThreadSafely()
      throws InterruptedException, ExecutionException {
    TemplateHotReloader reloader = new TemplateHotReloader();
    TemplateId id = TemplateId.of("concurrent.vm");
    int threadCount = 8;
    int iterations = 100;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int t = 0; t < threadCount; t++) {
        tasks.add(
            () -> {
              for (int i = 0; i < iterations; i++) {
                String content = "Hello thread " + (i % 2);
                TemplateHotReloader.CacheEntry rt = reloader.getOrLoadEntry(id, content);
                assertThat(rt).isNotNull();
                assertThat(rt.generation()).isGreaterThanOrEqualTo(1L);
              }
              return null;
            });
      }
      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }
    } finally {
      executor.shutdown();
    }

    assertThat(reloader.size()).isEqualTo(1);
  }
}
