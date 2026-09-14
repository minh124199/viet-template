package io.github.minh124199.viettemplate.benchmarks.stress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.benchmarks.output.prototype.ArrayBlockingQueuePool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.AtomicSlotPool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.BufferPool;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.PrototypePooledUtf8Output;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.SynchronizedArrayStackPool;
import io.github.minh124199.viettemplate.runtime.HtmlTextEscaper;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Comprehensive stress and correctness qualification suite for bounded buffer pools. */
class BufferPoolConcurrencyStressTest {

  static List<BufferPool> providePoolsForBasicTests() {
    return List.of(
        new SynchronizedArrayStackPool(8), new AtomicSlotPool(8), new ArrayBlockingQueuePool(8));
  }

  @Test
  @DisplayName("Test 1: 16 platform threads, 1,000 operations each, verifying output byte-for-byte")
  void testPlatformThreadConcurrencyStress() throws Exception {
    int threadCount = 16;
    int opsPerThread = 1_000;
    BufferPool pool = new SynchronizedArrayStackPool(16, true);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CyclicBarrier startBarrier = new CyclicBarrier(threadCount);

    try {
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int t = 0; t < threadCount; t++) {
        final int taskId = t;
        tasks.add(
            () -> {
              startBarrier.await(10, TimeUnit.SECONDS);
              for (int op = 0; op < opsPerThread; op++) {
                long timestamp = 1700000000000L + op;
                ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
                try (PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, pool)) {
                  out.write("[TASK:");
                  out.writeInt(taskId);
                  out.write("][TIME:");
                  out.writeLong(timestamp);
                  out.write("][COUNT:");
                  out.writeInt(op);
                  out.write("][PAYLOAD:Direct streaming UTF-8 buffer verification]\n");
                }
                String expected =
                    "[TASK:"
                        + taskId
                        + "][TIME:"
                        + timestamp
                        + "][COUNT:"
                        + op
                        + "][PAYLOAD:Direct streaming UTF-8 buffer verification]\n";
                String actual = baos.toString(StandardCharsets.UTF_8);
                assertThat(actual).isEqualTo(expected);
              }
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "Test 2: 10,000 tasks on virtual threads producing exact expected bytes with zero corruption")
  void testVirtualThreadConcurrencyStress() throws Exception {
    int taskCount = 10_000;
    BufferPool pool = new AtomicSlotPool(32, true);
    ExecutorService executor =
        VirtualThreadSupport.isVirtualThreadSupported()
            ? VirtualThreadSupport.createVirtualThreadExecutor()
            : VirtualThreadSupport.createPlatformThreadExecutor(64);

    try {
      List<Callable<Void>> tasks = new ArrayList<>(taskCount);
      for (int i = 0; i < taskCount; i++) {
        final int taskId = i;
        tasks.add(
            () -> {
              long timestamp = 1720000000000L + taskId;
              int count = taskId * 3;
              ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
              try (PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, pool)) {
                out.write("VIRTUAL-TASK:");
                out.writeInt(taskId);
                out.write(";TIME:");
                out.writeLong(timestamp);
                out.write(";CNT:");
                out.writeInt(count);
                out.write(";TEXT:Virtual thread pooled render verification\n");
              }
              String expected =
                  "VIRTUAL-TASK:"
                      + taskId
                      + ";TIME:"
                      + timestamp
                      + ";CNT:"
                      + count
                      + ";TEXT:Virtual thread pooled render verification\n";
              String actual = baos.toString(StandardCharsets.UTF_8);
              assertThat(actual).isEqualTo(expected);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "Test 3: Pool capacity 4 with 64 concurrent threads succeeding via fallback allocation")
  void testPoolExhaustionFallback() throws Exception {
    int threadCount = 64;
    BufferPool pool = new SynchronizedArrayStackPool(4);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CyclicBarrier barrier = new CyclicBarrier(threadCount);

    try {
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int i = 0; i < threadCount; i++) {
        final int taskId = i;
        tasks.add(
            () -> {
              barrier.await(10, TimeUnit.SECONDS);
              ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
              try (PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, pool)) {
                out.write("Thread-");
                out.writeInt(taskId);
                out.write("-fallback-success");
              }
              String expected = "Thread-" + taskId + "-fallback-success";
              assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @MethodSource("providePoolsForBasicTests")
  @DisplayName(
      "Test 4: Calling close() twice returns buffer at most once; pool size increases by 1, not 2")
  void testDoubleCloseIdempotency(BufferPool pool) throws Exception {
    byte[] initialBuf = new byte[PrototypePooledUtf8Output.BUFFER_SIZE];
    pool.release(initialBuf);
    int initialSize = pool.size();
    assertThat(initialSize).isGreaterThanOrEqualTo(1);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, pool);
    assertThat(pool.size()).isEqualTo(initialSize - 1);

    out.write("idempotent close test");
    out.close();
    assertThat(pool.size()).isEqualTo(initialSize);

    // Second close is idempotent
    out.close();
    assertThat(pool.size()).isEqualTo(initialSize);

    assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("idempotent close test");
  }

  @Test
  @DisplayName("Test 5: Calling write after close throws IOException")
  void testWriteAfterCloseThrows() throws IOException {
    BufferPool pool = new SynchronizedArrayStackPool(2);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, pool);
    out.write("open write");
    out.close();

    assertThatThrownBy(() -> out.write("fail"))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write("fail", 0, 4))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write((CharSequence) null))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write('f'))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeUtf8(new byte[] {1, 2, 3}))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeUtf8(new byte[] {1, 2, 3}, 0, 3))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeInt(42))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeLong(42L))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeDouble(3.14))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeFloat(3.14f))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeShort((short) 10))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeByte((byte) 10))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeBoolean(true))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(out::flush).isInstanceOf(IOException.class).hasMessage("Output is closed");
  }

  @ParameterizedTest
  @MethodSource("providePoolsForBasicTests")
  @DisplayName("Test 6: After stress test finishes, pool size is <= capacity")
  void testQuiescenceBoundedRetention(BufferPool pool) throws Exception {
    int threadCount = 32;
    int opsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    try {
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int t = 0; t < threadCount; t++) {
        tasks.add(
            () -> {
              for (int op = 0; op < opsPerThread; op++) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
                try (PrototypePooledUtf8Output out = new PrototypePooledUtf8Output(baos, pool)) {
                  out.write("quiescence test data");
                }
              }
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }

      // Quiescent assertion: pool size must never exceed capacity
      assertThat(pool.size()).isLessThanOrEqualTo(pool.capacity());
    } finally {
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @MethodSource("providePoolsForBasicTests")
  @DisplayName("Test 7: Stale byte non-observability across buffer reuse")
  void testStaleByteNonObservability(BufferPool pool) throws IOException {
    // 1. First lease: write 100 bytes
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    try (PrototypePooledUtf8Output out1 = new PrototypePooledUtf8Output(baos1, pool)) {
      out1.write("A".repeat(100));
    }
    byte[] bytes1 = baos1.toByteArray();
    assertThat(bytes1).hasSize(100);
    assertThat(new String(bytes1, StandardCharsets.UTF_8)).isEqualTo("A".repeat(100));

    // 2. Second lease from the same pool: write only 10 bytes
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    try (PrototypePooledUtf8Output out2 = new PrototypePooledUtf8Output(baos2, pool)) {
      out2.write("0123456789");
      out2.flush();
      byte[] flushBytes = baos2.toByteArray();
      assertThat(flushBytes).hasSize(10);
      assertThat(new String(flushBytes, StandardCharsets.UTF_8)).isEqualTo("0123456789");
    }

    // 3. Verify final output after close contains ONLY the 10 new bytes, never any of the 90 stale
    // bytes
    byte[] finalBytes = baos2.toByteArray();
    assertThat(finalBytes).hasSize(10);
    assertThat(new String(finalBytes, StandardCharsets.UTF_8)).isEqualTo("0123456789");
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 1_000, 10_000})
  @DisplayName(
      "Test 8: Production Utf8OutputStreamTemplateOutput virtual-thread concurrency stress")
  void testProductionVirtualThreadConcurrencyStress(int taskCount) throws Exception {
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
    try {
      List<Callable<Void>> tasks = new ArrayList<>(taskCount);
      for (int i = 0; i < taskCount; i++) {
        final int taskId = i;
        tasks.add(
            () -> {
              long timestamp = 1730000000000L + taskId;
              ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
              try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
                out.writeInt(taskId);
                out.writeLong(timestamp);
                out.write("Xin chào thế giới 🇻🇳; ");
                HtmlTextEscaper.INSTANCE.escape("Safe & <Fast> '100%' \"Quoted\"", out);
                out.writeDouble(99.95);
              }

              String expected =
                  taskId
                      + Long.toString(timestamp)
                      + "Xin chào thế giới 🇻🇳; "
                      + "Safe &amp; &lt;Fast&gt; &#39;100%&#39; &quot;Quoted&quot;"
                      + "99.95";
              byte[] actualBytes = baos.toByteArray();
              byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
              assertThat(actualBytes).isEqualTo(expectedBytes);
              assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
              return null;
            });
      }

      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName(
      "Test 9: Production pool empty repeated misses, fallback allocation, and leak-free recovery")
  void testEmptyPoolRepeatedMissCostAndFallback() throws Exception {
    // 1. Drain the production pool using 16 sequential leases held open
    List<Utf8OutputStreamTemplateOutput> heldOutputs = new ArrayList<>(16);
    for (int i = 0; i < 16; i++) {
      heldOutputs.add(new Utf8OutputStreamTemplateOutput(new ByteArrayOutputStream()));
    }
    assertThat(getProductionPoolSize()).isEqualTo(0);

    // 2. Execute 1,000 renders while pool is empty; verify fallback allocation correctness
    ExecutorService executor = VirtualThreadSupport.createVirtualThreadExecutor();
    try {
      List<Callable<Void>> tasks = new ArrayList<>(1_000);
      for (int i = 0; i < 1_000; i++) {
        final int taskId = i;
        tasks.add(
            () -> {
              ByteArrayOutputStream baos = new ByteArrayOutputStream(64);
              try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
                out.write("empty-pool-fallback-task-");
                out.writeInt(taskId);
              }
              String expected = "empty-pool-fallback-task-" + taskId;
              assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
              return null;
            });
      }
      List<Future<Void>> futures = executor.invokeAll(tasks);
      for (Future<Void> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    // 3. Close the 16 held outputs; verify pool recovers without leaks
    for (Utf8OutputStreamTemplateOutput held : heldOutputs) {
      held.close();
    }
    int poolSize = getProductionPoolSize();
    int capacity = getProductionPoolCapacity();
    assertThat(poolSize).isGreaterThan(0);
    assertThat(poolSize).isLessThanOrEqualTo(capacity);
  }

  @Test
  @DisplayName("Test 10: Full pool surplus and invalid release rejection with AtomicSlotPool")
  void testFullPoolSurplusReleaseRejection() {
    AtomicSlotPool pool = new AtomicSlotPool(16);
    for (int i = 0; i < 16; i++) {
      boolean accepted = pool.release(new byte[AtomicSlotPool.BUFFER_SIZE]);
      assertThat(accepted).isTrue();
    }
    assertThat(pool.size()).isEqualTo(16);
    assertThat(pool.capacity()).isEqualTo(16);

    // Attempt to release a 17th valid buffer: rejected, pool size remains 16
    byte[] surplusBuffer = new byte[AtomicSlotPool.BUFFER_SIZE];
    boolean surplusAccepted = pool.release(surplusBuffer);
    assertThat(surplusAccepted).isFalse();
    assertThat(pool.size()).isEqualTo(16);

    // Attempt to release null: rejected, pool size remains 16
    boolean nullAccepted = pool.release(null);
    assertThat(nullAccepted).isFalse();
    assertThat(pool.size()).isEqualTo(16);

    // Attempt to release buffer of invalid size (e.g. 4096): rejected, pool size remains 16
    boolean invalidSizeAccepted = pool.release(new byte[4096]);
    assertThat(invalidSizeAccepted).isFalse();
    assertThat(pool.size()).isEqualTo(16);
  }

  @Test
  @DisplayName(
      "Test 11: Production output close exception guarantees buffer release and state closure")
  void testProductionOutputCloseExceptionGuaranteesBufferRelease() throws Exception {
    // Warm up one buffer in pool so acquire decrements size
    ByteArrayOutputStream warmup = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(warmup)) {
      out.write("warmup");
    }
    int initialPoolSize = getProductionPoolSize();
    assertThat(initialPoolSize).isGreaterThanOrEqualTo(1);

    OutputStream failingStream =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            throw new IOException("Simulated network/disk error on flush");
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("Simulated network/disk error on flush");
          }

          @Override
          public void flush() throws IOException {
            throw new IOException("Simulated network/disk error on flush");
          }
        };

    Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(failingStream);
    int sizeWhileHeld = getProductionPoolSize();
    assertThat(sizeWhileHeld).isEqualTo(initialPoolSize - 1);

    out.write("trigger flush on close");

    // Close must throw IOException from failingStream during flush
    assertThatThrownBy(out::close)
        .isInstanceOf(IOException.class)
        .hasMessage("Simulated network/disk error on flush");

    // Pool size must recover despite the exception
    assertThat(getProductionPoolSize()).isEqualTo(initialPoolSize);

    // Subsequent writes and flush must throw IOException("Output is closed")
    assertThatThrownBy(() -> out.write("post-exception write"))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write("post-exception write", 0, 4))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write('c'))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeInt(123))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeLong(123456L))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(out::flush).isInstanceOf(IOException.class).hasMessage("Output is closed");

    // Second close is idempotent
    out.close();
    assertThat(getProductionPoolSize()).isEqualTo(initialPoolSize);
  }

  private static int getProductionPoolSize() {
    try {
      Class<?> poolClass =
          Class.forName("io.github.minh124199.viettemplate.runtime.Utf8BufferPool");
      Method m = poolClass.getDeclaredMethod("size");
      m.setAccessible(true);
      return (int) m.invoke(null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to access Utf8BufferPool.size()", e);
    }
  }

  private static int getProductionPoolCapacity() {
    try {
      Class<?> poolClass =
          Class.forName("io.github.minh124199.viettemplate.runtime.Utf8BufferPool");
      Method m = poolClass.getDeclaredMethod("capacity");
      m.setAccessible(true);
      return (int) m.invoke(null);
    } catch (Exception e) {
      throw new RuntimeException("Failed to access Utf8BufferPool.capacity()", e);
    }
  }
}
