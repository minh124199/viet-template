package io.github.minh124199.viettemplate.benchmarks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Diagnostic comparison used to qualify or reject RandomAccess list specialization. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class ListTraversalBenchmark {

  @Param({"0", "1", "5", "10", "50", "100", "1000"})
  private int size;

  private List<Integer> values;

  @Setup(Level.Trial)
  public void setUp() {
    values = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      values.add(i);
    }
  }

  @Benchmark
  public void iteratorTraversal(Blackhole blackhole) {
    Iterator<Integer> iterator = values.iterator();
    while (iterator.hasNext()) {
      blackhole.consume(iterator.next());
    }
  }

  @Benchmark
  public void indexedTraversal(Blackhole blackhole) {
    int length = values.size();
    for (int i = 0; i < length; i++) {
      blackhole.consume(values.get(i));
    }
  }
}
