package io.github.minh124199.viettemplate.benchmarks.output;

import io.github.minh124199.viettemplate.benchmarks.output.prototype.ArrayBlockingQueuePool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.AtomicSlotPool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.BufferPool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.SynchronizedArrayStackPool;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Microbenchmark suite for bounded UTF-8 buffer pools evaluating acquire-and-release throughput and
 * zero-allocation characteristics under varying thread contention and capacities.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class Utf8BufferPoolBenchmark {

  @Param({"SYNC_STACK", "ATOMIC_SLOT", "BLOCKING_QUEUE"})
  private String strategy;

  @Param({"8", "16", "32", "64"})
  private int capacity;

  private BufferPool pool;

  @Setup(Level.Trial)
  public void setUp() {
    pool =
        switch (strategy) {
          case "SYNC_STACK" -> new SynchronizedArrayStackPool(capacity, true);
          case "ATOMIC_SLOT" -> new AtomicSlotPool(capacity, true);
          case "BLOCKING_QUEUE" -> new ArrayBlockingQueuePool(capacity, true);
          default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        };
  }

  @Benchmark
  public void acquireAndRelease(Blackhole bh) {
    byte[] buf = pool.tryAcquire();
    if (buf != null) {
      bh.consume(buf);
      pool.release(buf);
    }
  }

  @Benchmark
  @Threads(1)
  public void acquireAndRelease_1Thread(Blackhole bh) {
    byte[] buf = pool.tryAcquire();
    if (buf != null) {
      bh.consume(buf);
      pool.release(buf);
    }
  }

  @Benchmark
  @Threads(4)
  public void acquireAndRelease_4Threads(Blackhole bh) {
    byte[] buf = pool.tryAcquire();
    if (buf != null) {
      bh.consume(buf);
      pool.release(buf);
    }
  }

  @Benchmark
  @Threads(8)
  public void acquireAndRelease_8Threads(Blackhole bh) {
    byte[] buf = pool.tryAcquire();
    if (buf != null) {
      bh.consume(buf);
      pool.release(buf);
    }
  }
}
