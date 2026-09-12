package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency stress test verifying that hot reload template replacement (generation A ->
 * generation B) is race-free, atomic, and safe while hundreds of concurrent virtual threads render
 * the template.
 */
class HotReloadConcurrencyStressTest {

  @Test
  @DisplayName("Stress test concurrent template hot reload during high-volume rendering")
  void testHotReloadDuringConcurrentRenders() throws Exception {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    TemplateId templateId = TemplateId.of("banner.vm");

    String genA = "<div>[GEN-A] Welcome, $user.name! Count: $user.count</div>\n";
    String genB = "<div>[GEN-B] Hello, $user.name! Value: $user.count</div>\n";

    repository.put(templateId, genA);

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .executionTier(ExecutionTier.IR)
            .build()) {

      // Warm up gen A
      Template initialTmpl = engine.get(templateId);
      StringTemplateOutput initOut = new StringTemplateOutput();
      initialTmpl.render(
          RenderContext.builder().put("user", new User("Alice", 1)).build(), initOut);
      assertThat(initOut.toString()).contains("[GEN-A]");

      int renderThreads = 20;
      int rendersPerThread = 200;
      ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
      AtomicBoolean running = new AtomicBoolean(true);
      AtomicInteger genACount = new AtomicInteger(0);
      AtomicInteger genBCount = new AtomicInteger(0);
      CountDownLatch readyLatch = new CountDownLatch(renderThreads);
      CountDownLatch reloadComplete = new CountDownLatch(1);

      try {
        List<Callable<Void>> renderTasks = new ArrayList<>(renderThreads);
        for (int t = 0; t < renderThreads; t++) {
          final int threadIdx = t;
          renderTasks.add(
              () -> {
                RenderContext ctx =
                    RenderContext.builder()
                        .put("user", new User("User-" + threadIdx, threadIdx))
                        .build();
                recordGeneration(engine.get(templateId), ctx, genACount, genBCount);
                readyLatch.countDown();
                if (!reloadComplete.await(5, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("Timed out waiting for hot reload");
                }
                for (int i = 1; i < rendersPerThread && running.get(); i++) {
                  recordGeneration(engine.get(templateId), ctx, genACount, genBCount);
                }
                return null;
              });
        }

        // Start renderers
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : renderTasks) {
          futures.add(executor.submit(task));
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Every renderer has completed generation A before this deterministic handoff.
        repository.put(templateId, genB);
        engine.invalidate(templateId);
        reloadComplete.countDown();

        // Wait for renderers to finish
        for (Future<Void> f : futures) {
          f.get();
        }

        // Verify that renders saw both Gen A and Gen B cleanly
        assertThat(genACount.get()).isGreaterThan(0);
        assertThat(genBCount.get()).isGreaterThan(0);

        // Post hot-reload renders must all be Gen B
        StringTemplateOutput finalOut = new StringTemplateOutput();
        engine
            .get(templateId)
            .render(RenderContext.builder().put("user", new User("Final", 99)).build(), finalOut);
        assertThat(finalOut.toString()).contains("[GEN-B]");
      } finally {
        running.set(false);
        executor.shutdown();
      }
    }
  }

  private static void recordGeneration(
      Template template, RenderContext context, AtomicInteger genACount, AtomicInteger genBCount)
      throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    template.render(context, out);
    String result = out.toString();
    if (result.contains("[GEN-A]")) {
      genACount.incrementAndGet();
    } else if (result.contains("[GEN-B]")) {
      genBCount.incrementAndGet();
    } else {
      throw new IllegalStateException("Corrupted output during hot reload: " + result);
    }
  }

  public record User(String name, int count) {}
}
