package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.FreshnessToken;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateFreshnessProvider;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateSource;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AtomicTemplateReplacementTest {

  @Test
  void inFlightOldSourceCannotAcquireReplacementFreshnessToken() throws Exception {
    TemplateId id = TemplateId.of("in-flight.vm");
    InMemoryTemplateRepository delegate = InMemoryTemplateRepository.create();
    delegate.put(id, "Version 1: $val");
    CountDownLatch sourceRead = new CountDownLatch(1);
    CountDownLatch releaseSource = new CountDownLatch(1);
    AtomicBoolean blockFirstRead = new AtomicBoolean(true);

    class BlockingRepository implements TemplateRepository, TemplateFreshnessProvider {
      @Override
      public Optional<TemplateSource> find(TemplateId requested) {
        Optional<TemplateSource> snapshot = delegate.find(requested);
        if (blockFirstRead.compareAndSet(true, false)) {
          sourceRead.countDown();
          try {
            assertThat(releaseSource.await(10, TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
          }
        }
        return snapshot;
      }

      @Override
      public Optional<FreshnessToken> freshnessToken(TemplateId requested) {
        return delegate.freshnessToken(requested);
      }
    }

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(new BlockingRepository()).build()) {
      Future<Template> oldLoad = executor.submit(() -> engine.get(id));
      try {
        assertThat(sourceRead.await(10, TimeUnit.SECONDS)).isTrue();
        delegate.put(id, "Version 2: $val");
        engine.invalidate(id);
        releaseSource.countDown();
        // The in-flight caller may retain V1, but must not publish it with V2's token.
        assertThat(render(oldLoad.get(10, TimeUnit.SECONDS))).isEqualTo("Version 1: OK");
        Template current = engine.get(id);
        assertThat(render(current)).isEqualTo("Version 2: OK");
        assertThat(engine.get(id)).isSameAs(current);
      } finally {
        releaseSource.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  private static String render(Template template) throws java.io.IOException {
    StringTemplateOutput output = new StringTemplateOutput();
    template.render(RenderContext.builder().put("val", "OK").build(), output);
    return output.toString();
  }

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
    CountDownLatch firstVersionRendered = new CountDownLatch(readerThreads);
    CountDownLatch secondVersionRendered = new CountDownLatch(readerThreads);
    List<Future<?>> readers = new ArrayList<>();

    try {
      // Readers
      for (int i = 0; i < readerThreads; i++) {
        readers.add(
            executor.submit(
                () -> {
                  try {
                    startLatch.await();
                    RenderContext ctx = RenderContext.builder().put("val", "OK").build();
                    boolean sawFirst = false;
                    boolean sawSecond = false;
                    while (running.get()) {
                      Template template = engine.get(id);
                      StringTemplateOutput output = new StringTemplateOutput();
                      template.render(ctx, output);
                      String res = output.toString();
                      if (res.equals("Version 1: OK")) {
                        v1Count.incrementAndGet();
                        if (!sawFirst) {
                          sawFirst = true;
                          firstVersionRendered.countDown();
                        }
                      } else if (res.equals("Version 2: OK")) {
                        v2Count.incrementAndGet();
                        if (!sawSecond) {
                          sawSecond = true;
                          secondVersionRendered.countDown();
                        }
                      } else {
                        throw new AssertionError("Corrupted render result: " + res);
                      }
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                }));
      }

      startLatch.countDown();
      assertThat(firstVersionRendered.await(10, TimeUnit.SECONDS)).isTrue();

      // Writer replaces the source, then invalidates cached entries.
      repository.put(id, "Version 2: $val");
      engine.invalidate(id);

      assertThat(secondVersionRendered.await(10, TimeUnit.SECONDS)).isTrue();
      running.set(false);

      executor.shutdown();
      boolean done = executor.awaitTermination(3, TimeUnit.SECONDS);
      assertThat(done).isTrue();
      for (Future<?> reader : readers) {
        reader.get(10, TimeUnit.SECONDS);
      }

      assertThat(v1Count.get()).isGreaterThan(0);
      assertThat(v2Count.get()).isGreaterThan(0);

      // Final render must be strictly Version 2
      StringTemplateOutput finalOut = new StringTemplateOutput();
      engine.get(id).render(RenderContext.builder().put("val", "OK").build(), finalOut);
      assertThat(finalOut.toString()).isEqualTo("Version 2: OK");
    } finally {
      running.set(false);
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
      engine.close();
    }
  }
}
