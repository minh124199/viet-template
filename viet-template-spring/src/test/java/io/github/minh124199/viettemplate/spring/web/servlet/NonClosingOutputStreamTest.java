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
  @DisplayName("Verifies compatibility delegation, null rejection, and non-closing behavior")
  void verifiesDelegationAndNonClosingBehavior() throws IOException {
    assertThatNullPointerException().isThrownBy(() -> new NonClosingOutputStream(null));

    TrackingOutputStream underlying = new TrackingOutputStream();
    NonClosingOutputStream nonClosing = new NonClosingOutputStream(underlying);

    assertThat(nonClosing)
        .isInstanceOf(
            io.github.minh124199.viettemplate.runtime.stream.NonClosingOutputStream.class);

    nonClosing.write("Hello World".getBytes(StandardCharsets.UTF_8));
    nonClosing.close();

    assertThat(underlying.closeCount).isZero();
    assertThat(underlying.flushCount).isGreaterThanOrEqualTo(1);
    assertThat(underlying.toString(StandardCharsets.UTF_8)).isEqualTo("Hello World");
  }
}
