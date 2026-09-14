package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Utf8OutputStreamTemplateOutputTest {

  @Test
  void writesPrimitivesAndStringsToStream() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      out.write("Hello ");
      out.write('W');
      out.write("orld! ");
      out.writeInt(42);
      out.write(" ");
      out.writeLong(10000000000L);
      out.write(" ");
      out.writeBoolean(true);
      out.write(" ");
      out.writeBoolean(false);
      out.write(" ");
      out.writeDouble(3.14);
      out.write(" ");
      out.writeFloat(1.5f);
      out.write(" ");
      out.writeShort((short) 12);
      out.write(" ");
      out.writeByte((byte) 7);
    }

    String result = baos.toString(StandardCharsets.UTF_8);
    assertThat(result).isEqualTo("Hello World! 42 10000000000 true false 3.14 1.5 12 7");
  }

  @Test
  void writesUtf8BytesDirectly() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      byte[] chunk = "Static UTF-8 chunk: Việt Nam".getBytes(StandardCharsets.UTF_8);
      out.writeUtf8(chunk);
      out.write(" - ");
      out.writeUtf8(chunk, 7, 5); // "UTF-8"
    }

    String result = baos.toString(StandardCharsets.UTF_8);
    assertThat(result).isEqualTo("Static UTF-8 chunk: Việt Nam - UTF-8");
  }

  @Test
  void encodesMultibyteAndSurrogatePairsCorrectly() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String complex = "Xin chào 👋 thế giới 🌏! Cà phê trứng ☕ và phở bò 🍜";
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      out.write(complex);
    }

    assertThat(baos.toByteArray()).isEqualTo(complex.getBytes(StandardCharsets.UTF_8));
    assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo(complex);
  }

  @Test
  void writesCharSequenceWithoutForcedToStringCall() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    CharSequence nonStringSequence =
        new CharSequence() {
          private final String data = "Zero-allocation CharSequence";

          @Override
          public int length() {
            return data.length();
          }

          @Override
          public char charAt(int index) {
            return data.charAt(index);
          }

          @Override
          public CharSequence subSequence(int start, int end) {
            return data.subSequence(start, end);
          }

          @Override
          public String toString() {
            throw new AssertionError("toString() should not be called on CharSequence!");
          }
        };

    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      out.write(nonStringSequence);
    }

    assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("Zero-allocation CharSequence");
  }

  @Test
  void handlesSmallBufferOverflowsAndFlushes() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // Use minimum buffer size 64
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos, 64)) {
      for (int i = 0; i < 100; i++) {
        out.write("Item-");
        out.writeInt(i);
        out.write("; ");
      }
      out.flush();
    }

    String result = baos.toString(StandardCharsets.UTF_8);
    assertThat(result).startsWith("Item-0; Item-1; ").endsWith("Item-99; ");
  }

  @Test
  void validatesMinimumBufferSize() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    assertThatThrownBy(() -> new Utf8OutputStreamTemplateOutput(baos, 32))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 64");
  }

  @Test
  void testBufferReuseAcrossRenders() throws IOException {
    Utf8BufferPool.resetForTesting();
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    // 1. First render: leases/allocates buffer, releases to pool on close
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out1 = new Utf8OutputStreamTemplateOutput(baos1)) {
      out1.write("Render 1: First streaming render");
    }
    assertThat(baos1.toString(StandardCharsets.UTF_8))
        .isEqualTo("Render 1: First streaming render");
    assertThat(Utf8BufferPool.size()).isEqualTo(1);

    // 2. Second render: acquires buffer from pool (size drops to 0)
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out2 = new Utf8OutputStreamTemplateOutput(baos2)) {
      assertThat(Utf8BufferPool.size()).isEqualTo(0);
      out2.write("Render 2: Reused streaming buffer");
    }
    // Released back to pool on close (size returns to 1)
    assertThat(baos2.toString(StandardCharsets.UTF_8))
        .isEqualTo("Render 2: Reused streaming buffer");
    assertThat(Utf8BufferPool.size()).isEqualTo(1);
  }

  @Test
  void testStaleByteNonObservability() throws IOException {
    Utf8BufferPool.resetForTesting();

    // 1. Render long string into buffer
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    String longString = "A".repeat(500);
    try (Utf8OutputStreamTemplateOutput out1 = new Utf8OutputStreamTemplateOutput(baos1)) {
      out1.write(longString);
    }
    assertThat(baos1.toByteArray()).hasSize(500);

    // 2. Second render leases the same buffer and writes a short string
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    String shortString = "Short";
    try (Utf8OutputStreamTemplateOutput out2 = new Utf8OutputStreamTemplateOutput(baos2)) {
      out2.write(shortString);
    }

    // 3. Verify output contains ONLY short string bytes, zero stale bytes from prior render
    byte[] bytes2 = baos2.toByteArray();
    assertThat(bytes2).hasSize(5);
    assertThat(new String(bytes2, StandardCharsets.UTF_8)).isEqualTo(shortString);
  }

  @Test
  void testDoubleCloseIdempotency() throws IOException {
    Utf8BufferPool.resetForTesting();

    AtomicInteger streamCloseCount = new AtomicInteger();
    OutputStream trackingStream =
        new ByteArrayOutputStream() {
          @Override
          public void close() throws IOException {
            streamCloseCount.incrementAndGet();
            super.close();
          }
        };

    Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(trackingStream);
    out.write("Idempotent double close");
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    // First close returns buffer to pool and closes underlying stream
    out.close();
    assertThat(streamCloseCount.get()).isEqualTo(1);
    assertThat(Utf8BufferPool.size()).isEqualTo(1);

    // Second close is no-op: does not close stream again, does not release buffer again
    out.close();
    assertThat(streamCloseCount.get()).isEqualTo(1);
    assertThat(Utf8BufferPool.size()).isEqualTo(1);
  }

  @Test
  void testWriteAfterCloseThrowsIOException() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos);
    out.write("initial data");
    out.close();

    assertThatThrownBy(() -> out.write("more"))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write("more", 0, 4))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write((CharSequence) null))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.write('x'))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeUtf8(new byte[] {1, 2, 3}))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeUtf8(new byte[] {1, 2, 3}, 0, 3))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeInt(123))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeLong(123456789L))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeDouble(1.23))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeFloat(1.23f))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeShort((short) 12))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeByte((byte) 5))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
    assertThatThrownBy(() -> out.writeBoolean(true))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
  }

  @Test
  void testFlushAfterCloseThrowsIOException() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos);
    out.close();

    assertThatThrownBy(out::flush).isInstanceOf(IOException.class).hasMessage("Output is closed");
  }

  @Test
  void testCustomBufferSizeUnpooled() throws IOException {
    Utf8BufferPool.resetForTesting();
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    // Custom buffer size 1024
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out1 = new Utf8OutputStreamTemplateOutput(baos1, 1024)) {
      out1.write("Custom buffer size 1024 payload");
    }
    assertThat(baos1.toString(StandardCharsets.UTF_8)).isEqualTo("Custom buffer size 1024 payload");
    assertThat(Utf8BufferPool.size()).isEqualTo(0); // Not released to pool

    // Custom buffer size 64
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out2 = new Utf8OutputStreamTemplateOutput(baos2, 64)) {
      out2.write("Custom buffer size 64 payload");
    }
    assertThat(baos2.toString(StandardCharsets.UTF_8)).isEqualTo("Custom buffer size 64 payload");
    assertThat(Utf8BufferPool.size()).isEqualTo(0); // Not released to pool
  }

  @Test
  void testExceptionDuringFlushReleasesBuffer() {
    Utf8BufferPool.resetForTesting();
    // Warm up pool so there is 1 buffer in pool
    Utf8BufferPool.release(new byte[Utf8BufferPool.BUFFER_SIZE]);
    assertThat(Utf8BufferPool.size()).isEqualTo(1);

    OutputStream failingStream =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            throw new IOException("Simulated network/disk error");
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("Simulated network/disk error");
          }
        };

    Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(failingStream);
    // Leased from pool
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    assertThatThrownBy(
            () -> {
              out.write("trigger flush on close");
              out.close();
            })
        .isInstanceOf(IOException.class)
        .hasMessage("Simulated network/disk error");

    // Output marked closed and buffer released in finally block
    assertThat(Utf8BufferPool.size()).isEqualTo(1);
    assertThatThrownBy(() -> out.write("post-exception write"))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
  }

  @Test
  void testPoolExhaustionFallbackAllocation() throws IOException {
    Utf8BufferPool.resetForTesting();
    int capacity = Utf8BufferPool.capacity();

    // Populate all pool slots
    for (int i = 0; i < capacity; i++) {
      Utf8BufferPool.release(new byte[Utf8BufferPool.BUFFER_SIZE]);
    }
    assertThat(Utf8BufferPool.size()).isEqualTo(capacity);

    // Acquire all pool slots to exhaust the pool
    byte[][] holders = new byte[capacity][];
    for (int i = 0; i < capacity; i++) {
      holders[i] = Utf8BufferPool.tryAcquire();
      assertThat(holders[i]).isNotNull();
    }
    assertThat(Utf8BufferPool.size()).isEqualTo(0);
    assertThat(Utf8BufferPool.tryAcquire()).isNull();

    // Instantiate another output - pool is exhausted, works normally via fallback allocation
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      out.write("Fallback allocation succeeds without error");
    }
    assertThat(baos.toString(StandardCharsets.UTF_8))
        .isEqualTo("Fallback allocation succeeds without error");

    // Clean up
    Utf8BufferPool.resetForTesting();
  }
}
