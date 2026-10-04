package io.github.minh124199.viettemplate.vtl.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.FreshnessToken;
import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateFreshnessProvider;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateSource;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GlobalMacroManagerConcurrencyTest {

  static final class ControllableRepository
      implements TemplateRepository, TemplateFreshnessProvider {
    private final InMemoryTemplateRepository delegate = new InMemoryTemplateRepository();
    volatile boolean interceptFind = false;
    final CountDownLatch threadAInLib2Latch = new CountDownLatch(1);
    final CountDownLatch latchA = new CountDownLatch(1);

    ControllableRepository put(TemplateId id, String content) {
      delegate.put(id, content);
      return this;
    }

    @Override
    public Optional<TemplateSource> find(TemplateId id) {
      if (interceptFind && id.value().equals("macro2.vm")) {
        threadAInLib2Latch.countDown();
        try {
          if (!latchA.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timed out waiting on latchA");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
      return delegate.find(id);
    }

    @Override
    public Optional<FreshnessToken> freshnessToken(TemplateId id) {
      return delegate.freshnessToken(id);
    }
  }

  @Test
  @DisplayName(
      "CONC-01: Synchronized invalidation prevents race condition during concurrent fingerprint"
          + " computation")
  void concurrentInvalidationDuringFingerprintComputationDoesNotLeaveStaleFingerprint()
      throws Exception {
    ControllableRepository repo = new ControllableRepository();
    TemplateId macroId1 = TemplateId.of("macro1.vm");
    TemplateId macroId2 = TemplateId.of("macro2.vm");
    TemplateId mainId = TemplateId.of("main.vm");

    // a. Initial setup
    repo.put(macroId1, "#macro(greeting)OldGreeting1#end");
    repo.put(macroId2, "#macro(farewell)Farewell2#end");
    repo.put(mainId, "#greeting() - #farewell()");

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .globalMacroLibraries(List.of(macroId1, macroId2))
            .build()) {

      // b. Initial compile & render outputs "OldGreeting1 - Farewell2"
      StringTemplateOutput out1 = new StringTemplateOutput();
      engine.get(mainId).render(RenderContext.empty(), out1);
      assertThat(out1.toString()).isEqualTo("OldGreeting1 - Farewell2");

      // c. Invalidate once to clear cachedFingerprint
      engine.invalidate(macroId1);

      // d. Thread A initiates computeFingerprint()
      repo.interceptFind = true;
      Thread threadA =
          new Thread(
              () -> engine.globalMacroManager().computeFingerprint(), "ThreadA-ComputeFingerprint");
      threadA.start();

      // e. Controllable repository intercepts find(macro2.vm) when interceptFind is enabled
      boolean intercepted = repo.threadAInLib2Latch.await(10, TimeUnit.SECONDS);
      assertThat(intercepted).isTrue();

      // f. Main thread mutates macro1.vm in repo, then starts Thread B calling
      // engine.invalidate(macroId1)
      repo.put(macroId1, "#macro(greeting)NewGreeting1#end");
      Thread threadB = new Thread(() -> engine.invalidate(macroId1), "ThreadB-InvalidateMacro1");
      threadB.start();

      // g. Main thread waits until Thread B is BLOCKED on monitor (or TERMINATED)
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (threadB.getState() != Thread.State.BLOCKED
          && threadB.getState() != Thread.State.TERMINATED) {
        if (System.nanoTime() > deadline) {
          break;
        }
        Thread.onSpinWait();
      }
      assertThat(threadB.getState()).isEqualTo(Thread.State.BLOCKED);

      // Unblock Thread A
      repo.interceptFind = false;
      repo.latchA.countDown();

      // h. Joins both threads
      threadA.join(10000);
      threadB.join(10000);
      assertThat(threadA.isAlive()).isFalse();
      assertThat(threadB.isAlive()).isFalse();

      // i. Renders main.vm again
      StringTemplateOutput out2 = new StringTemplateOutput();
      engine.get(mainId).render(RenderContext.empty(), out2);

      // j. Asserts that the output is "NewGreeting1 - Farewell2"
      assertThat(out2.toString()).isEqualTo("NewGreeting1 - Farewell2");
    }
  }
}
