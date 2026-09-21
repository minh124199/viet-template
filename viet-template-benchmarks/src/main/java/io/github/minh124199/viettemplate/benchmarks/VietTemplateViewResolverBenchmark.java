package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.web.servlet.View;

/**
 * Microbenchmark measuring Spring MVC {@link VietTemplateViewResolver} candidate resolution latency
 * and throughput across single-suffix, multi-suffix probe hits, misses, and cached view lookups.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class VietTemplateViewResolverBenchmark {

  private TemplateEngine engine;
  private VietTemplateViewResolver singleSuffixResolver;
  private VietTemplateViewResolver multiSuffixResolver;
  private VietTemplateViewResolver cachingResolver;

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    InMemoryTemplateRepository repository =
        InMemoryTemplateRepository.create()
            .put("views/home.vtl", "<h1>Home VTL</h1>")
            .put("views/about.vm", "<h1>About VM</h1>")
            .put("views/cached.vtl", "<h1>Cached VTL</h1>");

    engine = VtlTemplateEngine.builder().repository(repository).build();

    singleSuffixResolver = new VietTemplateViewResolver(engine);
    singleSuffixResolver.setPrefix("views/");
    singleSuffixResolver.setSuffix(".vtl");
    singleSuffixResolver.setCache(false);

    multiSuffixResolver = new VietTemplateViewResolver(engine);
    multiSuffixResolver.setPrefix("views/");
    multiSuffixResolver.setSuffixes(List.of(".vtl", ".vm"));
    multiSuffixResolver.setCache(false);

    cachingResolver = new VietTemplateViewResolver(engine);
    cachingResolver.setPrefix("views/");
    cachingResolver.setSuffixes(List.of(".vtl", ".vm"));
    cachingResolver.setCache(true);
    // Pre-warm cache for steady-state lookup benchmark
    cachingResolver.resolveViewName("cached", Locale.ROOT);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (engine != null) {
      engine.close();
    }
  }

  @Benchmark
  public View singleSuffix() throws Exception {
    return singleSuffixResolver.resolveViewName("home", Locale.ROOT);
  }

  @Benchmark
  public View multiSuffixFirstHit() throws Exception {
    return multiSuffixResolver.resolveViewName("home", Locale.ROOT);
  }

  @Benchmark
  public View multiSuffixSecondHit() throws Exception {
    return multiSuffixResolver.resolveViewName("about", Locale.ROOT);
  }

  @Benchmark
  public View multiSuffixMiss() throws Exception {
    return multiSuffixResolver.resolveViewName("missing", Locale.ROOT);
  }

  @Benchmark
  public View cacheHit() throws Exception {
    return cachingResolver.resolveViewName("cached", Locale.ROOT);
  }
}
