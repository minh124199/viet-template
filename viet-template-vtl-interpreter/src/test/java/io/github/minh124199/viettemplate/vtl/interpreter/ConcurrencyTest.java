package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ConcurrencyTest {

  @Test
  void sharedAstAndInterpreterAcrossConcurrentThreads() throws Exception {
    String templateContent =
        """
        #macro(format $name $id)User: $name (ID: $id)#end
        #set($greeting = 'Hello')
        $greeting, #format($user, $userId)!
        #foreach($item in $items)
          Item $foreach.count: $item
        #end
        """;

    SourceText source = SourceText.of("shared.vm", templateContent);
    VtlTemplate ast = VtlParser.parse(source).template();
    VtlInterpreter interpreter = new VtlInterpreter();

    int threadCount = 20;
    int runsPerThread = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        tasks.add(
            () -> {
              for (int r = 0; r < runsPerThread; r++) {
                String expectedUserName = "User_" + threadId + "_" + r;
                int expectedUserId = threadId * 1000 + r;
                List<String> items = List.of("alpha", "beta");

                Map<String, Object> ctx =
                    Map.of(
                        "user", expectedUserName,
                        "userId", expectedUserId,
                        "items", items);

                StringTemplateOutput output = new StringTemplateOutput();
                interpreter.interpret(ast, source, MapRenderContext.of(ctx), output);

                String rendered = output.toString();
                assertThat(rendered)
                    .contains("Hello, User: " + expectedUserName + " (ID: " + expectedUserId + ")!")
                    .contains("Item 1: alpha")
                    .contains("Item 2: beta");
              }
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(); // Ensure no exception was thrown in worker threads
      }
    } finally {
      executor.shutdown();
    }
  }
}
