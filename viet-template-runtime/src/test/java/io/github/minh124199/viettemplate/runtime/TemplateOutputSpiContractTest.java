package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract test suite verifying that a minimal implementation of {@link TemplateOutput} (only
 * implementing non-default interface methods) adheres strictly to the SPI contract.
 */
class TemplateOutputSpiContractTest {

  /**
   * Minimal {@link TemplateOutput} implementation that overrides NO default methods. Specifically,
   * it does NOT override {@link #write(CharSequence, int, int)} or {@link #writeUtf8(byte[], int,
   * int)}.
   */
  static class MinimalCustomOutput implements TemplateOutput {
    final StringBuilder chars = new StringBuilder();
    final List<Character> singleChars = new ArrayList<>();
    final List<byte[]> utf8Chunks = new ArrayList<>();
    final List<String> primitives = new ArrayList<>();

    @Override
    public void write(CharSequence value) throws IOException {
      if (value != null) {
        chars.append(value);
      }
    }

    @Override
    public void write(char value) throws IOException {
      singleChars.add(value);
      chars.append(value);
    }

    @Override
    public void writeUtf8(byte[] bytes) throws IOException {
      utf8Chunks.add(bytes);
      chars.append(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void writeInt(int value) throws IOException {
      primitives.add("int:" + value);
      chars.append(value);
    }

    @Override
    public void writeLong(long value) throws IOException {
      primitives.add("long:" + value);
      chars.append(value);
    }

    @Override
    public void writeDouble(double value) throws IOException {
      primitives.add("double:" + value);
      chars.append(value);
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
      primitives.add("boolean:" + value);
      chars.append(value);
    }
  }

  @Test
  void rangeWriteDelegatesToSingleChar() throws IOException {
    MinimalCustomOutput out = new MinimalCustomOutput();
    out.write("Hello World", 0, 5);

    assertThat(out.chars.toString()).isEqualTo("Hello");
    assertThat(out.singleChars).containsExactly('H', 'e', 'l', 'l', 'o');
  }

  @Test
  void rangeWriteNullCharSequenceIsSafeNoOp() throws IOException {
    MinimalCustomOutput out = new MinimalCustomOutput();
    assertThatCode(() -> out.write(null, 0, 10)).doesNotThrowAnyException();
    assertThat(out.chars.toString()).isEmpty();
    assertThat(out.singleChars).isEmpty();
  }

  @Test
  void rangeWriteEmptyIntervalIsSafeNoOp() throws IOException {
    MinimalCustomOutput out = new MinimalCustomOutput();
    out.write("Hello", 2, 2);

    assertThat(out.chars.toString()).isEmpty();
    assertThat(out.singleChars).isEmpty();
  }

  @Test
  void rangeWriteValidatesHalfOpenBounds() {
    MinimalCustomOutput out = new MinimalCustomOutput();
    String text = "HelloWorld";

    assertThatThrownBy(() -> out.write(text, -1, 5)).isInstanceOf(IndexOutOfBoundsException.class);

    assertThatThrownBy(() -> out.write(text, 5, 2)).isInstanceOf(IndexOutOfBoundsException.class);

    assertThatThrownBy(() -> out.write(text, 0, 15)).isInstanceOf(IndexOutOfBoundsException.class);

    assertThatThrownBy(() -> out.write(text, 8, 12)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void utf8SubarrayDelegatesToUtf8Write() throws IOException {
    MinimalCustomOutput out = new MinimalCustomOutput();
    byte[] full = "0123456789".getBytes(StandardCharsets.UTF_8);

    out.writeUtf8(full, 2, 4);

    assertThat(out.chars.toString()).isEqualTo("2345");
    assertThat(out.utf8Chunks).hasSize(1);
    assertThat(out.utf8Chunks.get(0)).isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void primitiveFallbacksDelegateCorrectly() throws IOException {
    MinimalCustomOutput out = new MinimalCustomOutput();

    out.writeByte((byte) 8);
    out.writeShort((short) 16);
    out.writeFloat(3.14f);
    out.flush(); // default flush is safe no-op

    assertThat(out.primitives).contains("int:8", "int:16");
    assertThat(out.primitives.stream().anyMatch(s -> s.startsWith("double:"))).isTrue();
  }
}
