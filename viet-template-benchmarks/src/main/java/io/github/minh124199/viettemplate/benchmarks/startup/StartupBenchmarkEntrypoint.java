package io.github.minh124199.viettemplate.benchmarks.startup;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Process-level startup measurement harness capturing micro-second precision checkpoints across JVM
 * bootstrap, engine initialization, template compilation, and first renders.
 */
public final class StartupBenchmarkEntrypoint {

  public static void main(String[] args) throws IOException {
    long tMainEntry = System.nanoTime();
    long jvmStartMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
    long jvmUptimeAtEntryMillis = System.currentTimeMillis() - jvmStartMillis;

    ExecutionTier tier = ExecutionTier.IR;
    Path outputPath = null;

    for (int i = 0; i < args.length; i++) {
      if ("--tier".equals(args[i]) && i + 1 < args.length) {
        tier = ExecutionTier.valueOf(args[++i].toUpperCase(Locale.ROOT));
      } else if ("--output".equals(args[i]) && i + 1 < args.length) {
        outputPath = Path.of(args[++i]);
      }
    }

    // Checkpoint 1: Repository readiness
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    populateRepository(repository);
    long tRepoReady = System.nanoTime();

    // Checkpoint 2: Engine initialization
    VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repository).executionTier(tier).build();
    long tEngineInit = System.nanoTime();

    // Checkpoint 3: Compilation of primary templates
    Template tStatic = engine.get("static.vm");
    Template tScalar = engine.get("scalar.vm");
    Template tTable = engine.get("table.vm");
    Template tMacro = engine.get("macro.vm");
    long tCompilationComplete = System.nanoTime();

    // Checkpoint 4: First single render
    RenderContext scalarCtx = createScalarContext();
    StringTemplateOutput out1 = new StringTemplateOutput();
    tScalar.render(scalarCtx, out1);
    long tFirstRender = System.nanoTime();

    // Checkpoint 5: All first renders (warming up dynamic linking across template corpus)
    tStatic.render(RenderContext.empty(), new StringTemplateOutput());
    tTable.render(createTableContext(), new StringTemplateOutput());
    tMacro.render(createMacroContext(), new StringTemplateOutput());
    long tAllFirstRenders = System.nanoTime();

    // Checkpoint 6: Small batch execution (50 renders)
    for (int i = 0; i < 50; i++) {
      StringTemplateOutput out = new StringTemplateOutput();
      tScalar.render(scalarCtx, out);
    }
    long tSmallBatch = System.nanoTime();

    long tProcessTotal = System.nanoTime();
    engine.close();

    // Calculate metrics
    long repoReadyNs = tRepoReady - tMainEntry;
    long engineInitNs = tEngineInit - tRepoReady;
    long compilationNs = tCompilationComplete - tEngineInit;
    long firstRenderNs = tFirstRender - tCompilationComplete;
    long allFirstRendersNs = tAllFirstRenders - tFirstRender;
    long smallBatchNs = tSmallBatch - tAllFirstRenders;
    long totalStartupNs = tProcessTotal - tMainEntry;

    String json =
        String.format(
            Locale.ROOT,
            """
            {
              "jvm": {
                "uptimeAtMainEntryMs": %d,
                "version": "%s",
                "vendor": "%s"
              },
              "tier": "%s",
              "nanoseconds": {
                "repoReady": %d,
                "engineInit": %d,
                "compilation": %d,
                "firstRender": %d,
                "allFirstRenders": %d,
                "smallBatch50": %d,
                "totalStartup": %d
              },
              "milliseconds": {
                "jvmUptimeAtEntry": %.3f,
                "repoReady": %.3f,
                "engineInit": %.3f,
                "compilation": %.3f,
                "firstRender": %.3f,
                "allFirstRenders": %.3f,
                "smallBatch50": %.3f,
                "totalStartup": %.3f
              }
            }
            """,
            jvmUptimeAtEntryMillis,
            System.getProperty("java.version"),
            System.getProperty("java.vendor"),
            tier.name(),
            repoReadyNs,
            engineInitNs,
            compilationNs,
            firstRenderNs,
            allFirstRendersNs,
            smallBatchNs,
            totalStartupNs,
            jvmUptimeAtEntryMillis * 1.0,
            repoReadyNs / 1_000_000.0,
            engineInitNs / 1_000_000.0,
            compilationNs / 1_000_000.0,
            firstRenderNs / 1_000_000.0,
            allFirstRendersNs / 1_000_000.0,
            smallBatchNs / 1_000_000.0,
            totalStartupNs / 1_000_000.0);

    if (outputPath != null) {
      if (outputPath.getParent() != null) {
        Files.createDirectories(outputPath.getParent());
      }
      Files.writeString(outputPath, json);
    }

    System.out.println(json);
  }

  private static void populateRepository(InMemoryTemplateRepository repo) {
    repo.put("static.vm", "<html><body><h1>Static Title</h1><p>Content</p></body></html>\n");
    repo.put("scalar.vm", "Hello, $user.name! Score: $user.score, Role: $user.role\n");
    repo.put(
        "table.vm",
        "<table>#foreach($r in $rows)<tr><td>$r.id</td><td>$r.name</td></tr>#end</table>\n");
    repo.put(
        "macro.vm",
        "#macro(card $title $tag)<div class=\"card\"><h3>$title</h3><span>$tag</span></div>#end\n"
            + "#card('Hello', 'TAG')\n");
  }

  private static RenderContext createScalarContext() {
    return RenderContext.builder().put("user", new UserRecord("Alice", 95.5, "ADMIN")).build();
  }

  private static RenderContext createTableContext() {
    List<TableRow> rows = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      rows.add(new TableRow(i, "Row-" + i));
    }
    return RenderContext.builder().put("rows", rows).build();
  }

  private static RenderContext createMacroContext() {
    return RenderContext.empty();
  }

  public record UserRecord(String name, double score, String role) {}

  public record TableRow(int id, String name) {}
}
