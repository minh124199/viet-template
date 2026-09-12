package io.github.minh124199.viettemplate.tck.differential;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionLimits;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlInterpreterOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ConcurrencyRobustnessTest {

  @ParameterizedTest
  @EnumSource(ExecutionTier.class)
  @DisplayName(
      "Concurrency: Concurrent renders across independent threads have isolated state and budgets")
  void testConcurrentRendering(ExecutionTier tier) throws Exception {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();
    TemplateId tid = TemplateId.of("concurrent.vtl");
    repo.put(
        tid.value(),
        "#set($local = $threadId * 10)Thread:$threadId:Local:$local:#foreach($i in"
            + " [1..$count])[$i]#end");

    // Limits per render
    ExecutionLimits limits =
        ExecutionLimits.builder().maxOutputCharacters(10_000).maxLoopIterations(500).build();

    VtlInterpreterOptions opts =
        VtlInterpreterOptions.builder()
            .profile(VtlProfile.VTL_CORE)
            .limits(limits)
            .executionTier(tier)
            .build();

    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repo).interpreterOptions(opts).build()) {

      int threadCount = 8;
      int tasksPerThread = 25;
      ExecutorService executor = Executors.newFixedThreadPool(threadCount);

      List<Callable<Void>> tasks = new ArrayList<>();
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        for (int task = 0; task < tasksPerThread; task++) {
          final int count = 1 + (task % 5);
          tasks.add(
              () -> {
                StringTemplateOutput out = new StringTemplateOutput();
                RenderContext ctx = RenderContext.of(Map.of("threadId", threadId, "count", count));

                engine.render(tid, ctx, out);
                String result = out.toString();

                String expectedPrefix = "Thread:" + threadId + ":Local:" + (threadId * 10) + ":";
                assertThat(result)
                    .as("Output must belong to thread %d", threadId)
                    .startsWith(expectedPrefix);

                StringBuilder expectedLoop = new StringBuilder();
                for (int i = 1; i <= count; i++) {
                  expectedLoop.append("[").append(i).append("]");
                }
                assertThat(result).contains(expectedLoop.toString());
                return null;
              });
        }
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      executor.shutdown();
      assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

      for (Future<Void> future : futures) {
        future.get(); // Will rethrow any assertion error or exception from thread
      }
    }
  }
}
