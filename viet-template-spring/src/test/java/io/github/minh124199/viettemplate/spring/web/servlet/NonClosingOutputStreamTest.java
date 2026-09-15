package io.github.minh124199.viettemplate.spring.web.servlet;

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
  @DisplayName("close() only flushes and leaves underlying stream open (closeCount == 0)")
  void closeOnlyFlushes() throws IOException {
    TrackingOutputStream underlying = new TrackingOutputStream();
    NonClosingOutputStream nonClosing = new NonClosingOutputStream(underlying);

    nonClosing.write("Hello World".getBytes(StandardCharsets.UTF_8));
    nonClosing.close();

    assertThat(underlying.closeCount).isZero();
    assertThat(underlying.flushCount).isGreaterThanOrEqualTo(1);
    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("Hello World");

    // Underlying stream remains fully usable and open
    underlying.write(" - Still Open".getBytes(StandardCharsets.UTF_8));
    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("Hello World - Still Open");
  }

  @Test
  @DisplayName("write methods forward bytes accurately to underlying stream")
  void writeMethodsForwardAccurately() throws IOException {
    TrackingOutputStream underlying = new TrackingOutputStream();
    NonClosingOutputStream nonClosing = new NonClosingOutputStream(underlying);

    nonClosing.write('A');
    nonClosing.write("BCDE".getBytes(StandardCharsets.UTF_8));
    nonClosing.write("FGHIJ".getBytes(StandardCharsets.UTF_8), 1, 3);
    nonClosing.flush();

    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("ABCDEGHI");
    assertThat(underlying.flushCount).isEqualTo(1);
    assertThat(underlying.closeCount).isZero();
  }
}
