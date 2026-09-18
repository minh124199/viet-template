package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.CallSiteRegistry;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.lang.reflect.Method;
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

  @Test
  @DisplayName(
      "Audit virtual threads with JFR to verify zero pinned events under 1,000 and 10,000 renders")
  void testVirtualThreadJfrPinningAuditUnder1000And10000Renders() throws Exception {
    // Prewarm template compilation, virtual thread executor, and test harness lambdas so
    // steady-state rendering is isolated from classloader initialization, StringConcatFactory
    // invokedynamic linking, and MethodHandle CountingWrapper JIT tiering
    runItemRendersStress(50, VirtualThreadSupport.createVirtualThreadExecutor());
    for (int i = 0; i < 150; i++) {
      engine.render(
          RenderRequest.of(
              TemplateId.of("item.vm"),
              RenderContext.builder().put("item", new Item("ITM-0", "Warmup", 0.0)).build()),
          new StringTemplateOutput());
    }

    long initialLinks = callSiteRegistry(engine).statistics().links();
    assertThat(initialLinks).as("Prewarming must link dynamic call sites").isGreaterThan(0L);

    try (jdk.jfr.Recording recording = new jdk.jfr.Recording()) {
      recording.enable("jdk.VirtualThreadPinned");
      recording.start();

      // Stress scenario 1: 1,000 renders
      runItemRendersStress(1_000, VirtualThreadSupport.createVirtualThreadExecutor());

      // Stress scenario 2: 10,000 renders
      runItemRendersStress(10_000, VirtualThreadSupport.createVirtualThreadExecutor());

      recording.stop();
      java.nio.file.Path tempJfr = java.nio.file.Files.createTempFile("vt-pinned-audit", ".jfr");
      try {
        recording.dump(tempJfr);
        List<jdk.jfr.consumer.RecordedEvent> events =
            jdk.jfr.consumer.RecordingFile.readAllEvents(tempJfr);
        List<jdk.jfr.consumer.RecordedEvent> pinnedEvents =
            events.stream()
                .filter(e -> e.getEventType().getName().equals("jdk.VirtualThreadPinned"))
                .toList();

        List<jdk.jfr.consumer.RecordedEvent> unexpectedPinnedEvents = new ArrayList<>();
        List<jdk.jfr.consumer.RecordedEvent> classifiedJvmInternalEvents = new ArrayList<>();
        for (jdk.jfr.consumer.RecordedEvent e : pinnedEvents) {
          if (isKnownJvmInternalMethodTypeMaintenanceEvent(e)) {
            classifiedJvmInternalEvents.add(e);
          } else {
            unexpectedPinnedEvents.add(e);
          }
        }

        if (!classifiedJvmInternalEvents.isEmpty()) {
          System.out.println(
              "=== DIAGNOSTIC: KNOWN JVM-INTERNAL METHODTYPE MAINTENANCE EVENTS ("
                  + classifiedJvmInternalEvents.size()
                  + ") ===");
          for (jdk.jfr.consumer.RecordedEvent e : classifiedJvmInternalEvents) {
            java.time.Duration d = e.getDuration();
            long nanos = d != null ? d.toNanos() : 0L;
            double millis = nanos / 1_000_000.0;
            System.out.printf(
                "  Event duration: %d ns / %.3f ms | reason: carrier thread pin%n", nanos, millis);
            jdk.jfr.consumer.RecordedStackTrace stack = e.getStackTrace();
            if (stack != null && !stack.getFrames().isEmpty()) {
              List<jdk.jfr.consumer.RecordedFrame> frames = stack.getFrames();
              var top = frames.get(0).getMethod();
              System.out.println(
                  "  blocking operation: " + top.getType().getName() + "." + top.getName());
              System.out.println("  top frames:");
              frames.stream()
                  .limit(5)
                  .forEach(
                      f ->
                          System.out.println(
                              "    at "
                                  + f.getMethod().getType().getName()
                                  + "."
                                  + f.getMethod().getName()
                                  + ":"
                                  + f.getLineNumber()));
            }
          }
        }

        if (!unexpectedPinnedEvents.isEmpty()) {
          System.out.println(
              "=== DIAGNOSTIC: UNEXPECTED PINNED VIRTUAL THREAD EVENTS DETECTED ("
                  + unexpectedPinnedEvents.size()
                  + ") ===");
          unexpectedPinnedEvents.stream()
              .limit(5)
              .forEach(
                  e -> {
                    System.out.println("Event: " + e);
                    jdk.jfr.consumer.RecordedStackTrace stack = e.getStackTrace();
                    if (stack != null) {
                      System.out.println("Stack trace:");
                      stack
                          .getFrames()
                          .forEach(
                              f ->
                                  System.out.println(
                                      "  at "
                                          + f.getMethod().getType().getName()
                                          + "."
                                          + f.getMethod().getName()
                                          + ":"
                                          + f.getLineNumber()));
                    }
                  });
        }

        assertThat(unexpectedPinnedEvents).isEmpty();
        assertThat(callSiteRegistry(engine).statistics().links())
            .as("Links count must remain unchanged during steady-state rendering")
            .isEqualTo(initialLinks);
      } finally {
        java.nio.file.Files.deleteIfExists(tempJfr);
      }
    }
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

  private static boolean isKnownJvmInternalMethodTypeMaintenanceEvent(
      jdk.jfr.consumer.RecordedEvent event) {
    if (event.getDuration() != null && event.getDuration().toNanos() >= 1_000_000L) {
      return false;
    }
    jdk.jfr.consumer.RecordedStackTrace stackTrace = event.getStackTrace();
    if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
      return false;
    }
    List<jdk.jfr.consumer.RecordedFrame> frames = stackTrace.getFrames();
    jdk.jfr.consumer.RecordedFrame topFrame = frames.get(0);
    if (!"java.lang.ref.ReferenceQueue".equals(topFrame.getMethod().getType().getName())
        || !"poll".equals(topFrame.getMethod().getName())) {
      return false;
    }
    boolean hasMethodTypeMakeImpl =
        frames.stream()
            .anyMatch(
                f ->
                    "java.lang.invoke.MethodType".equals(f.getMethod().getType().getName())
                        && "makeImpl".equals(f.getMethod().getName()));
    boolean hasDynamicLinker =
        frames.stream()
            .anyMatch(
                f ->
                    "io.github.minh124199.viettemplate.runtime.linker.DynamicLinker"
                            .equals(f.getMethod().getType().getName())
                        && "checkMethodAndCreateLink".equals(f.getMethod().getName()));
    return hasMethodTypeMakeImpl && hasDynamicLinker;
  }

  private static CallSiteRegistry callSiteRegistry(VtlTemplateEngine engine) {
    try {
      Method method = VtlTemplateEngine.class.getDeclaredMethod("callSiteRegistry");
      method.setAccessible(true);
      return (CallSiteRegistry) method.invoke(engine);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
