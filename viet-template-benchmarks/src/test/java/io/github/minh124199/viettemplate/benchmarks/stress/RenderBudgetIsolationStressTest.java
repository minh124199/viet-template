package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency stress test verifying that {@link RenderBudget} constraints (output characters, loop
 * iterations, timeout) remain strictly isolated per render and do not bleed across concurrent
 * virtual or platform threads.
 */
class RenderBudgetIsolationStressTest {

  private static InMemoryTemplateRepository repository;
  private static VtlTemplateEngine engine;

  @BeforeAll
  static void setUp() {
    repository = InMemoryTemplateRepository.create();
    repository.put(
        "largeLoop.vm", "#foreach($item in $items)\n" + "<span>Item: $item</span>\n" + "#end\n");

    engine =
        VtlTemplateEngine.builder().repository(repository).executionTier(ExecutionTier.IR).build();
  }

  @AfterAll
  static void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Test
  @DisplayName("Verify strict budget isolation under 500 concurrent mixed-budget renders")
  void testConcurrentBudgetIsolation() throws Exception {
    TemplateId templateId = TemplateId.of("largeLoop.vm");
    int totalTasks = 500;
    List<String> items = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      items.add("Entry-" + i);
    }
    RenderContext context = RenderContext.builder().put("items", items).build();
    RenderRequest request = RenderRequest.of(templateId, context);

    AtomicInteger successfulRenders = new AtomicInteger(0);
    AtomicInteger throttledRenders = new AtomicInteger(0);

    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();

    try {
      List<Callable<Void>> tasks = new ArrayList<>(totalTasks);
      for (int i = 0; i < totalTasks; i++) {
        final boolean tightBudget = (i % 2 == 0);
        tasks.add(
            () -> {
              StringTemplateOutput baseOutput = new StringTemplateOutput();
              // Tight budget allows max 10 loop iterations (template has 50), normal allows 1000
              RenderBudget budget =
                  tightBudget
                      ? new RenderBudget(100_000L, 0L, 10)
                      : new RenderBudget(100_000L, 0L, 1_000);

              CountingTemplateOutput countingOutput =
                  new CountingTemplateOutput(baseOutput, budget, templateId);

              if (tightBudget) {
                assertThatThrownBy(() -> engine.render(request, countingOutput))
                    .isInstanceOf(TemplateLimitException.class);
                throttledRenders.incrementAndGet();
              } else {
                engine.render(request, countingOutput);
                assertThat(baseOutput.toString()).contains("Item: Entry-49");
                successfulRenders.incrementAndGet();
              }
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }

      assertThat(successfulRenders.get()).isEqualTo(totalTasks / 2);
      assertThat(throttledRenders.get()).isEqualTo(totalTasks / 2);
    } finally {
      executor.shutdown();
    }
  }
}
