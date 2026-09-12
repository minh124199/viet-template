package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Concurrency harness and standalone CLI benchmark comparing throughput and scalability between
 * Java 21+ Virtual Threads and traditional platform thread pools.
 */
public class PlatformThreadComparisonHarness {

  public static void main(String[] args) throws Exception {
    int taskCount = 1_000;
    if (args.length > 0) {
      try {
        taskCount = Integer.parseInt(args[0]);
      } catch (NumberFormatException ignored) {
      }
    }

    System.out.println("==================================================================");
    System.out.println("Platform Threads vs Virtual Threads Rendering Benchmark");
    System.out.println(
        "  Java Runtime : "
            + System.getProperty("java.version")
            + " ("
            + System.getProperty("java.vendor")
            + ")");
    System.out.println(
        "  Virtual Threads Supported: " + VirtualThreadSupport.isVirtualThreadSupported());
    System.out.println("  Total Tasks  : " + taskCount);
    System.out.println("==================================================================");

    ComparisonResult platformRes =
        runBenchmark(
            "Platform Threads (Fixed Pool 16)",
            VirtualThreadSupport.createPlatformThreadExecutor(16),
            taskCount);
    ComparisonResult virtualRes =
        runBenchmark(
            "Virtual Threads / Unbounded Fallback",
            VirtualThreadSupport.createVirtualThreadExecutor(),
            taskCount);

    System.out.println(platformRes);
    System.out.println(virtualRes);
    System.out.println("==================================================================");
  }

  @Test
  @DisplayName("Verify platform vs virtual thread comparative execution correctness")
  void testPlatformVsVirtualThreadExecution() throws Exception {
    int tasks = 200;
    ComparisonResult platform =
        runBenchmark("Platform", VirtualThreadSupport.createPlatformThreadExecutor(8), tasks);
    ComparisonResult virtual =
        runBenchmark("Virtual", VirtualThreadSupport.createVirtualThreadExecutor(), tasks);

    assertThat(platform.completedTasks()).isEqualTo(tasks);
    assertThat(virtual.completedTasks()).isEqualTo(tasks);
    assertThat(platform.durationMillis()).isGreaterThan(0);
    assertThat(virtual.durationMillis()).isGreaterThan(0);
  }

  public static ComparisonResult runBenchmark(String label, ExecutorService executor, int taskCount)
      throws Exception {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId templateId = TemplateId.of("compare.vm");
    repo.put(templateId, "<div>User: $user.name, Role: $user.role, Active: $user.active</div>\n");

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).executionTier(ExecutionTier.IR).build()) {

      // Warmup
      RenderContext warmupCtx =
          RenderContext.builder().put("user", new User("Warm", "ADMIN", true)).build();
      StringTemplateOutput warmupOut = new StringTemplateOutput();
      engine.render(RenderRequest.of(templateId, warmupCtx), warmupOut);

      List<Callable<Void>> tasks = new ArrayList<>(taskCount);
      for (int i = 0; i < taskCount; i++) {
        final int id = i;
        tasks.add(
            () -> {
              RenderContext ctx =
                  RenderContext.builder()
                      .put("user", new User("User" + id, "MEMBER", id % 2 == 0))
                      .build();
              StringTemplateOutput out = new StringTemplateOutput();
              engine.render(RenderRequest.of(templateId, ctx), out);
              if (!out.toString().contains("User" + id)) {
                throw new IllegalStateException("Corrupted output");
              }
              return null;
            });
      }

      long startNanos = System.nanoTime();
      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get();
      }
      long endNanos = System.nanoTime();
      long durationMillis = TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
      if (durationMillis == 0) durationMillis = 1;

      double throughput = (taskCount * 1000.0) / durationMillis;
      return new ComparisonResult(label, taskCount, durationMillis, throughput);
    } finally {
      executor.shutdown();
    }
  }

  public record User(String name, String role, boolean active) {}

  public record ComparisonResult(
      String label, int completedTasks, long durationMillis, double throughputOpsPerSec) {
    @Override
    public String toString() {
      return String.format(
          "  %-35s : %d tasks in %d ms (%.1f renders/sec)",
          label, completedTasks, durationMillis, throughputOpsPerSec);
    }
  }
}
