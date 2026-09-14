package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcreteOutputLifecycleContractTest {

  @BeforeEach
  void setUp() {
    Utf8BufferPool.resetForTesting();
  }

  @Test
  void utf8Output_close_flushesAndClosesUnderlyingStream() throws IOException {
    TrackingOutputStream tracking = new TrackingOutputStream();
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(tracking);

    output.write("Hello, UTF-8 streaming output!");
    // Data is still in the 8 KiB buffer, not yet flushed to stream
    assertThat(tracking.closed).isFalse();
    assertThat(tracking.baos.size()).isEqualTo(0);

    output.close();

    assertThat(tracking.closed).isTrue();
    assertThat(tracking.baos.toString(StandardCharsets.UTF_8))
        .isEqualTo("Hello, UTF-8 streaming output!");
  }

  @Test
  void utf8Output_close_releasesPooledBuffer() throws IOException {
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Utf8OutputStreamTemplateOutput output1 = new Utf8OutputStreamTemplateOutput(baos);

    output1.write("Render 1 payload");
    // While in-use, buffer is checked out from pool
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    output1.close();
    // After close, the 8 KiB buffer is returned to Utf8BufferPool
    assertThat(Utf8BufferPool.size()).isEqualTo(1);

    // Opening a second output acquires the pooled buffer
    Utf8OutputStreamTemplateOutput output2 = new Utf8OutputStreamTemplateOutput(baos);
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    output2.close();
    assertThat(Utf8BufferPool.size()).isEqualTo(1);
  }

  @Test
  void utf8Output_flush_flushesWithoutClosingStreamOrReleasingBuffer() throws IOException {
    TrackingOutputStream tracking = new TrackingOutputStream();
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(tracking);

    output.write("First chunk; ");
    assertThat(tracking.baos.size()).isEqualTo(0);

    output.flush();
    assertThat(tracking.flushed).isTrue();
    assertThat(tracking.closed).isFalse();
    assertThat(tracking.baos.toString(StandardCharsets.UTF_8)).isEqualTo("First chunk; ");
    // Buffer is still held by output, not released back to pool
    assertThat(Utf8BufferPool.size()).isEqualTo(0);

    output.write("Second chunk;");
    output.flush();
    assertThat(tracking.closed).isFalse();
    assertThat(tracking.baos.toString(StandardCharsets.UTF_8))
        .isEqualTo("First chunk; Second chunk;");

    output.close();
    assertThat(tracking.closed).isTrue();
    assertThat(Utf8BufferPool.size()).isEqualTo(1);
  }

  @Test
  void utf8Output_doubleClose_isIdempotent() throws IOException {
    TrackingOutputStream tracking = new TrackingOutputStream();
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(tracking);

    output.write("Double close test");
    output.close();

    assertThat(tracking.closeCount.get()).isEqualTo(1);
    assertThat(Utf8BufferPool.size()).isEqualTo(1);

    // Second close invocation must be a safe, idempotent no-op
    output.close();
    assertThat(tracking.closeCount.get()).isEqualTo(1);
    assertThat(Utf8BufferPool.size()).isEqualTo(1);
  }

  @Test
  void utf8Output_writeAfterClose_throwsIOException() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Utf8OutputStreamTemplateOutput output = new Utf8OutputStreamTemplateOutput(baos);

    output.close();

    assertThatThrownBy(() -> output.write("test"))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");

    assertThatThrownBy(() -> output.write('A'))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");

    assertThatThrownBy(() -> output.writeInt(42))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");

    assertThatThrownBy(() -> output.writeLong(100L))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");

    assertThatThrownBy(() -> output.writeUtf8(new byte[] {1, 2, 3}))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");

    assertThatThrownBy(() -> output.writeBoolean(true))
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");

    assertThatThrownBy(output::flush)
        .isInstanceOf(IOException.class)
        .hasMessage("Output is closed");
  }

  @Test
  void utf8Output_nonClosingOutputStreamPattern_leavesUnderlyingOpen() throws IOException {
    TrackingOutputStream targetStream = new TrackingOutputStream();

    // Recommended pattern for managed streams (e.g. HttpServletResponse.getOutputStream())
    OutputStream nonClosingWrapper =
        new FilterOutputStream(targetStream) {
          @Override
          public void close() throws IOException {
            flush(); // Flush content but leave underlying target open for container lifecycle
          }
        };

    try (Utf8OutputStreamTemplateOutput output =
        new Utf8OutputStreamTemplateOutput(nonClosingWrapper)) {
      output.write("Managed servlet response payload");
    }

    // Output template stream closed, but the target stream was left open by the wrapper delegate
    assertThat(targetStream.closed).isFalse();
    assertThat(targetStream.flushed).isTrue();
    assertThat(targetStream.baos.toString(StandardCharsets.UTF_8))
        .isEqualTo("Managed servlet response payload");

    // Target stream can subsequently be closed by container
    targetStream.close();
    assertThat(targetStream.closed).isTrue();
  }

  @Test
  void writerOutput_callerRetainsOwnershipOfWriter() throws IOException {
    TrackingWriter trackingWriter = new TrackingWriter();
    WriterTemplateOutput output = new WriterTemplateOutput(trackingWriter);

    // WriterTemplateOutput does not implement AutoCloseable
    assertThat(output).isNotInstanceOf(AutoCloseable.class);

    output.write("Fragment rendered by template");
    output.flush();

    // Writer remains open; caller still owns it
    assertThat(trackingWriter.closed).isFalse();
    assertThat(trackingWriter.toString()).isEqualTo("Fragment rendered by template");

    // Caller can append more data outside template execution
    trackingWriter.write(" | Suffix from caller");
    trackingWriter.close();

    assertThat(trackingWriter.closed).isTrue();
    assertThat(trackingWriter.toString())
        .isEqualTo("Fragment rendered by template | Suffix from caller");
  }

  @Test
  void writerOutput_flush_delegatesToWriter() throws IOException {
    TrackingWriter trackingWriter = new TrackingWriter();
    WriterTemplateOutput output = new WriterTemplateOutput(trackingWriter);

    assertThat(trackingWriter.flushed.get()).isFalse();

    output.write("Content");
    output.flush();

    assertThat(trackingWriter.flushed.get()).isTrue();
  }

  @Test
  void stringOutput_inMemoryLifecycleAndReset() {
    StringTemplateOutput output = new StringTemplateOutput(32);

    assertThat(output).isNotInstanceOf(AutoCloseable.class);
    assertThat(output.length()).isEqualTo(0);

    output.write("First execution: ");
    output.writeInt(12345);
    assertThat(output.length()).isEqualTo(22);
    assertThat(output.toString()).isEqualTo("First execution: 12345");

    // Flush is a safe no-op on in-memory buffers
    output.flush();
    assertThat(output.length()).isEqualTo(22);

    // Reset clears the buffer for the next sequential render
    output.reset();
    assertThat(output.length()).isEqualTo(0);
    assertThat(output.toString()).isEqualTo("");

    output.write("Second execution");
    assertThat(output.length()).isEqualTo(16);
    assertThat(output.toString()).isEqualTo("Second execution");
  }

  private static final class TrackingOutputStream extends OutputStream {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final AtomicInteger closeCount = new AtomicInteger();
    volatile boolean flushed = false;
    volatile boolean closed = false;

    @Override
    public void write(int b) throws IOException {
      baos.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      baos.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      flushed = true;
      baos.flush();
    }

    @Override
    public void close() throws IOException {
      closeCount.incrementAndGet();
      closed = true;
      baos.close();
    }
  }

  private static final class TrackingWriter extends StringWriter {
    final AtomicBoolean flushed = new AtomicBoolean(false);
    volatile boolean closed = false;

    @Override
    public void flush() {
      flushed.set(true);
      super.flush();
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
