package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AtomicTemplateReplacementTest {

  @Test
  void concurrentRendersProceedSafelyDuringAtomicTemplateReplacement() throws Exception {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    TemplateId id = TemplateId.of("concurrent.vm");
    repository.put(id, "Version 1: $val");

    VtlTemplateEngine engine = VtlTemplateEngine.builder().repository(repository).build();

    int readerThreads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(readerThreads + 1);
    AtomicBoolean running = new AtomicBoolean(true);
    AtomicInteger v1Count = new AtomicInteger(0);
    AtomicInteger v2Count = new AtomicInteger(0);
    CountDownLatch startLatch = new CountDownLatch(1);

    try {
      // Readers
      for (int i = 0; i < readerThreads; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                RenderContext ctx = RenderContext.builder().put("val", "OK").build();
                while (running.get()) {
                  Template template = engine.get(id);
                  StringTemplateOutput output = new StringTemplateOutput();
                  template.render(ctx, output);
                  String res = output.toString();
                  if (res.contains("Version 1: OK")) {
                    v1Count.incrementAndGet();
                  } else if (res.contains("Version 2: OK")) {
                    v2Count.incrementAndGet();
                  } else {
                    throw new AssertionError("Corrupted render result: " + res);
                  }
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
      }

      startLatch.countDown();
      Thread.sleep(50L);

      // Writer updates repository and invalidates cache atomically
      repository.put(id, "Version 2: $val");
      engine.invalidate(id);

      Thread.sleep(50L);
      running.set(false);

      executor.shutdown();
      boolean done = executor.awaitTermination(3, TimeUnit.SECONDS);
      assertThat(done).isTrue();

      assertThat(v1Count.get()).isGreaterThan(0);
      assertThat(v2Count.get()).isGreaterThan(0);

      // Final render must be strictly Version 2
      StringTemplateOutput finalOut = new StringTemplateOutput();
      engine.get(id).render(RenderContext.builder().put("val", "OK").build(), finalOut);
      assertThat(finalOut.toString()).isEqualTo("Version 2: OK");
    } finally {
      engine.close();
      executor.shutdownNow();
    }
  }
}
