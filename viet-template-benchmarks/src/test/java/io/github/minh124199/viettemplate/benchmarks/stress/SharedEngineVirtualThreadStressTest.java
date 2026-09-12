package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Concurrency stress test verifying that a shared {@link VtlTemplateEngine} correctly and
 * deterministically serves thousands of concurrent renders using virtual threads (or platform
 * threads) with isolated {@link RenderContext}, {@link RenderRequest}, and {@link RenderBudget}.
 */
class SharedEngineVirtualThreadStressTest {

  private static InMemoryTemplateRepository repository;
  private static VtlTemplateEngine engine;

  @BeforeAll
  static void setUp() {
    repository = InMemoryTemplateRepository.create();
    repository.put(
        "item.vm",
        "<div class=\"item\" id=\"$item.id\"><h3>$item.title</h3><p>$item.price</p></div>\n");
    repository.put(
        "order.vm",
        "<h1>Order: $order.id</h1>\n"
            + "<ul>\n"
            + "#foreach($i in $order.items)\n"
            + "  <li>[$foreach.count] $i.name - $i.qty</li>\n"
            + "#end\n"
            + "</ul>\n");

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
  @DisplayName("Stress test 1,000 concurrent item renders across virtual threads")
  void testItemRendersConcurrency1000() throws Exception {
    runItemRendersStress(1_000, VirtualThreadSupport.createVirtualThreadExecutor());
  }

  @Test
  @DisplayName("Stress test 2,000 concurrent complex order renders across virtual threads")
  void testOrderRendersConcurrency2000() throws Exception {
    runOrderRendersStress(2_000, VirtualThreadSupport.createVirtualThreadExecutor());
  }

  @ParameterizedTest
  @ValueSource(strings = {"virtual", "platform"})
  @DisplayName("Compare 500 concurrent renders between virtual and platform threads")
  void testParityBetweenVirtualAndPlatformThreads(String threadType) throws Exception {
    ExecutorService executor =
        "virtual".equals(threadType)
            ? VirtualThreadSupport.createVirtualThreadExecutor()
            : VirtualThreadSupport.createPlatformThreadExecutor(16);
    runItemRendersStress(500, executor);
  }

  private void runItemRendersStress(int taskCount, ExecutorService executor) throws Exception {
    try {
      TemplateId templateId = TemplateId.of("item.vm");
      List<Callable<String>> tasks = new ArrayList<>(taskCount);

      for (int i = 0; i < taskCount; i++) {
        final int id = i;
        tasks.add(
            () -> {
              Item item = new Item("ITM-" + id, "Product " + id, id * 10.5);
              RenderContext context = RenderContext.builder().put("item", item).build();
              RenderRequest request = RenderRequest.of(templateId, context);
              RenderBudget budget = RenderBudget.unlimited();
              StringTemplateOutput stringOut = new StringTemplateOutput();
              CountingTemplateOutput out =
                  new CountingTemplateOutput(stringOut, budget, templateId);

              engine.render(request, out);
              return stringOut.toString();
            });
      }

      List<Future<String>> futures = executor.invokeAll(tasks);
      assertThat(futures).hasSize(taskCount);

      for (int i = 0; i < taskCount; i++) {
        String result = futures.get(i).get();
        String expected =
            "<div class=\"item\" id=\"ITM-"
                + i
                + "\"><h3>Product "
                + i
                + "</h3><p>"
                + (i * 10.5)
                + "</p></div>\n";
        assertThat(result).isEqualTo(expected);
      }
    } finally {
      executor.shutdown();
    }
  }

  private void runOrderRendersStress(int taskCount, ExecutorService executor) throws Exception {
    try {
      TemplateId templateId = TemplateId.of("order.vm");
      List<Callable<String>> tasks = new ArrayList<>(taskCount);

      for (int i = 0; i < taskCount; i++) {
        final int id = i;
        tasks.add(
            () -> {
              List<OrderItem> items =
                  List.of(
                      new OrderItem("A-" + id, 1),
                      new OrderItem("B-" + id, 2),
                      new OrderItem("C-" + id, 3));
              Order order = new Order("ORD-" + id, items);
              RenderContext context = RenderContext.builder().put("order", order).build();
              RenderRequest request = RenderRequest.of(templateId, context);
              RenderBudget budget = RenderBudget.unlimited();
              StringTemplateOutput stringOut = new StringTemplateOutput();
              CountingTemplateOutput out =
                  new CountingTemplateOutput(stringOut, budget, templateId);

              engine.render(request, out);
              return stringOut.toString();
            });
      }

      List<Future<String>> futures = executor.invokeAll(tasks);
      assertThat(futures).hasSize(taskCount);

      for (int i = 0; i < taskCount; i++) {
        String result = futures.get(i).get();
        String expected =
            "<h1>Order: ORD-"
                + i
                + "</h1>\n<ul>\n"
                + "  <li>[1] A-"
                + i
                + " - 1</li>\n"
                + "  <li>[2] B-"
                + i
                + " - 2</li>\n"
                + "  <li>[3] C-"
                + i
                + " - 3</li>\n"
                + "</ul>\n";
        assertThat(result).isEqualTo(expected);
      }
    } finally {
      executor.shutdown();
    }
  }

  public record Item(String id, String title, double price) {}

  public record OrderItem(String name, int qty) {}

  public record Order(String id, List<OrderItem> items) {}
}
