package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Contract test suite verifying that {@link TemplateEngine} fulfills its thread-safety, lifecycle,
 * string render convenience, and cache invalidation contracts.
 */
class TemplateEngineContractTest {

  @Test
  void engineLifecycleAndConvenienceMethods() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("greeting.vm", "Hello, ${name}!");

    try (TemplateEngine engine = TemplateEngine.builder().repository(repo).build()) {
      // Convenience: engine.render(String, RenderContext)
      String res1 = engine.render("greeting.vm", RenderContext.of("name", "Alice"));
      assertThat(res1).isEqualTo("Hello, Alice!");

      // Convenience: engine.render(TemplateId, RenderContext)
      String res2 = engine.render(TemplateId.of("greeting.vm"), RenderContext.of("name", "Bob"));
      assertThat(res2).isEqualTo("Hello, Bob!");

      // Convenience: template.render(RenderContext)
      Template tpl = engine.get("greeting.vm");
      String res3 = tpl.render(RenderContext.of("name", "Charlie"));
      assertThat(res3).isEqualTo("Hello, Charlie!");

      // Invalidation contracts
      engine.invalidate(TemplateId.of("greeting.vm"));
      engine.invalidateAll();

      // Ensure template still renders after cache invalidation (recompiles on miss)
      String res4 = engine.render("greeting.vm", RenderContext.of("name", "David"));
      assertThat(res4).isEqualTo("Hello, David!");
    }
  }

  @Test
  void concurrentRendersSharingSameEngine() throws Exception {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    repo.put("math.vm", "Item: $item, Index: $idx, Total: $total");

    TemplateEngine engine = TemplateEngine.builder().repository(repo).build();

    int threadCount = 8;
    int iterationsPerThread = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < threadCount * iterationsPerThread; i++) {
        final int id = i;
        tasks.add(
            () -> {
              RenderContext ctx =
                  RenderContext.builder()
                      .put("item", "Item" + id)
                      .put("idx", id)
                      .put("total", id * 10)
                      .build();

              String rendered = engine.render("math.vm", ctx);
              assertThat(rendered)
                  .isEqualTo("Item: Item" + id + ", Index: " + id + ", Total: " + (id * 10));
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get(); // propagate any assertion or rendering error
      }
    } finally {
      executor.shutdown();
      engine.close();
    }
  }
}
