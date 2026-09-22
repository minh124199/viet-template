package io.github.minh124199.viettemplate.runtime.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NonClosingOutputStreamTest {

  static final class TrackingOutputStream extends ByteArrayOutputStream {
    int closeCount = 0;
    int flushCount = 0;

    @Override
    public void flush() throws IOException {
      flushCount++;
      super.flush();
    }

    @Override
    public void close() throws IOException {
      closeCount++;
      super.close();
    }
  }

  @Test
  @DisplayName("Constructor rejects null underlying output stream")
  void constructorRejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> new NonClosingOutputStream(null));
  }

  @Test
  @DisplayName("close() flushes and leaves underlying stream open without closing it")
  void closeOnlyFlushes() throws IOException {
    TrackingOutputStream underlying = new TrackingOutputStream();
    NonClosingOutputStream nonClosing = new NonClosingOutputStream(underlying);

    nonClosing.write("Data to flush".getBytes(StandardCharsets.UTF_8));
    nonClosing.close();

    assertThat(underlying.closeCount).isZero();
    assertThat(underlying.flushCount).isGreaterThanOrEqualTo(1);
    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("Data to flush");

    // Underlying stream remains fully open and usable
    underlying.write(" - more data".getBytes(StandardCharsets.UTF_8));
    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("Data to flush - more data");
  }

  @Test
  @DisplayName("write and flush methods forward correctly to underlying stream")
  void writeMethodsForwardAccurately() throws IOException {
    TrackingOutputStream underlying = new TrackingOutputStream();
    NonClosingOutputStream nonClosing = new NonClosingOutputStream(underlying);

    nonClosing.write('X');
    nonClosing.write("YZ".getBytes(StandardCharsets.UTF_8));
    nonClosing.write("012345".getBytes(StandardCharsets.UTF_8), 1, 3);
    nonClosing.flush();

    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("XYZ123");
    assertThat(underlying.flushCount).isEqualTo(1);
    assertThat(underlying.closeCount).isZero();
  }
}
