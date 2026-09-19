package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

/** Isolates loop-source normalization from real IR and AOT foreach rendering. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class LoopAlgorithmBenchmark {

  @Param({"IR", "AOT_BYTECODE"})
  private String tier;

  @Param({
    "OBJECT_ARRAY",
    "INT_ARRAY",
    "LONG_ARRAY",
    "ARRAY_LIST",
    "LINKED_LIST",
    "ITERABLE",
    "ITERATOR",
    "MAP",
    "RANGE_ASCENDING",
    "RANGE_DESCENDING"
  })
  private String source;

  @Param({"0", "1", "5", "10", "50", "100", "1000"})
  private int size;

  private VtlTemplateEngine engine;
  private Template template;
  private RenderContext context;
  private StringTemplateOutput output;
  private Object input;

  @Setup(Level.Trial)
  public void setUp() {
    input = createInput();
    String templateSource =
        source.startsWith("RANGE_")
            ? rangeTemplate(source.equals("RANGE_ASCENDING"), size)
            : "#foreach($item in $items)$item#end";

    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put("loop-algorithm.vm", templateSource);
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .modelSchema(
                source.startsWith("RANGE_")
                    ? ModelSchema.empty()
                    : ModelSchema.builder()
                        .add("items", VTypes.fromJavaClass(input.getClass(), Nullability.NON_NULL))
                        .build())
            .build();
    engine =
        VtlTemplateEngine.builder()
            .repository(repository)
            .executionTier(ExecutionTier.valueOf(tier))
            .semanticOptions(semanticOptions)
            .build();
    template = engine.get("loop-algorithm.vm");
    context =
        source.startsWith("RANGE_")
            ? RenderContext.empty()
            : RenderContext.builder().put("items", input).build();
    output = new StringTemplateOutput(Math.max(16, size * 4));
  }

  @Setup(Level.Invocation)
  public void resetIterator() {
    if (input instanceof ResettableIterator iterator) {
      iterator.reset();
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    engine.close();
  }

  @Benchmark
  public void templateRender(Blackhole blackhole) throws IOException {
    template.render(context, output);
    blackhole.consume(output.length());
    output.reset();
  }

  @Benchmark
  public void runtimeNormalization(Blackhole blackhole) {
    Iterator<?> iterator =
        source.startsWith("RANGE_")
            ? createRangeIterator()
            : BytecodeRuntimeBridge.toIterator(input);
    while (iterator.hasNext()) {
      blackhole.consume(iterator.next());
    }
  }

  private Object createInput() {
    Integer[] objects = new Integer[size];
    int[] ints = new int[size];
    long[] longs = new long[size];
    List<Integer> values = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      objects[i] = i;
      ints[i] = i;
      longs[i] = i;
      values.add(i);
    }
    return switch (source) {
      case "OBJECT_ARRAY" -> objects;
      case "INT_ARRAY" -> ints;
      case "LONG_ARRAY" -> longs;
      case "ARRAY_LIST" -> values;
      case "LINKED_LIST" -> new LinkedList<>(values);
      case "ITERABLE" -> new FixedIterable(List.copyOf(values));
      case "ITERATOR" -> new ResettableIterator(objects);
      case "MAP" -> {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int value : values) {
          map.put(value, value);
        }
        yield map;
      }
      case "RANGE_ASCENDING", "RANGE_DESCENDING" -> objects;
      default -> throw new IllegalArgumentException("Unknown loop source: " + source);
    };
  }

  private Iterator<?> createRangeIterator() {
    if (size == 0) {
      return List.of().iterator();
    }
    int start = source.equals("RANGE_ASCENDING") ? 1 : size;
    int end = source.equals("RANGE_ASCENDING") ? size : 1;
    return BytecodeRuntimeBridge.rangeIterator(start, end, 10_000, "benchmark.vm", 1, 1, 1, 1);
  }

  private static String rangeTemplate(boolean ascending, int size) {
    if (size == 0) {
      return "#foreach($item in $missing)$item#end";
    }
    return ascending
        ? "#foreach($item in [1.." + size + "])$item#end"
        : "#foreach($item in [" + size + "..1])$item#end";
  }

  private record FixedIterable(List<Integer> values) implements Iterable<Integer> {
    @Override
    public Iterator<Integer> iterator() {
      return values.iterator();
    }
  }

  private static final class ResettableIterator implements Iterator<Integer> {
    private final Integer[] values;
    private int index;

    private ResettableIterator(Integer[] values) {
      this.values = values;
    }

    private void reset() {
      index = 0;
    }

    @Override
    public boolean hasNext() {
      return index < values.length;
    }

    @Override
    public Integer next() {
      return values[index++];
    }
  }
}
