package io.github.minh124199.viettemplate.benchmarks.output;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Concurrent lifecycle benchmark evaluating throughput, contention, and allocation scaling between
 * fresh unpooled 8 KiB buffer allocation and the production bounded 16-slot pool under thread
 * concurrency (1, 2, 4, 8, 16, 32).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 2,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class StreamingOutputConcurrencyBenchmark {

  @Param({"CURRENT_NEW", "POOLED_PRODUCTION"})
  private String outputMode;

  private VtlTemplateEngine engine;
  private Template tplDynamicTiny;
  private Template tplMedium;
  private RenderContext ctxDynamicTiny;
  private RenderContext ctxMedium;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    InMemoryTemplateRepository repo = InMemoryTemplateRepository.create();

    repo.put(
        "dynamic_tiny.vm",
        "<div class=\"user-badge\"><span>$userName</span><span"
            + " class=\"role\">$role</span><em>$dept</em></div>");

    repo.put(
        "medium.vm",
        """
        <article class=\"profile-card\">
          <header>
            <h2>User Profile: $user</h2>
            <p class=\"subtitle\">Department: $dept | Location: $location</p>
          </header>
          <section class=\"bio\">
            <p>$bio</p>
          </section>
          <footer>
            <p>&copy; 2026 Viet Template. All rights reserved.</p>
          </footer>
        </article>
        """);

    engine =
        VtlTemplateEngine.builder()
            .repository(repo)
            .executionTier(ExecutionTier.AOT_BYTECODE)
            .build();

    tplDynamicTiny = engine.get("dynamic_tiny.vm");
    tplMedium = engine.get("medium.vm");

    ctxDynamicTiny =
        RenderContext.builder()
            .put("userName", "Alice")
            .put("role", "Administrator")
            .put("dept", "Engineering")
            .build();

    ctxMedium =
        RenderContext.builder()
            .put("user", "NguyenVanA")
            .put("dept", "Platform Infrastructure")
            .put("location", "Ho Chi Minh City")
            .put(
                "bio",
                "Senior systems engineer focusing on high-performance streaming architectures.")
            .build();
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  private void render(Template tpl, RenderContext ctx, Blackhole bh) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
    if ("CURRENT_NEW".equals(outputMode)) {
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos, 8191)) {
        tpl.render(ctx, out);
      }
    } else {
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        tpl.render(ctx, out);
      }
    }
    bh.consume(baos);
  }

  @Benchmark
  public void concurrentRender_dynamicTiny(Blackhole bh) throws IOException {
    render(tplDynamicTiny, ctxDynamicTiny, bh);
  }

  @Benchmark
  public void concurrentRender_medium(Blackhole bh) throws IOException {
    render(tplMedium, ctxMedium, bh);
  }
}
