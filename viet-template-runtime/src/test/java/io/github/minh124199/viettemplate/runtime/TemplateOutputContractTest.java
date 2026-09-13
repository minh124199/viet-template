package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TemplateOutputContractTest {

  @Nested
  @DisplayName("StringTemplateOutput Contract")
  class StringTemplateOutputContract {

    @Test
    @DisplayName("Normal slice [start, end) writes expected characters")
    void testNormalSlice() {
      StringTemplateOutput out = new StringTemplateOutput();
      out.write("Hello, World!", 7, 12);
      assertThat(out.toString()).isEqualTo("World");

      out.write("123456", 0, 3);
      assertThat(out.toString()).isEqualTo("World123");
    }

    @Test
    @DisplayName("Empty slice start == end writes nothing")
    void testEmptySlice() {
      StringTemplateOutput out = new StringTemplateOutput();
      out.write("Hello", 2, 2);
      assertThat(out.toString()).isEmpty();

      out.write("Hello", 0, 0);
      assertThat(out.toString()).isEmpty();

      out.write("Hello", 5, 5);
      assertThat(out.toString()).isEmpty();
    }

    @Test
    @DisplayName("Invalid bounds throw IndexOutOfBoundsException")
    void testInvalidBounds() {
      StringTemplateOutput out = new StringTemplateOutput();
      assertThatThrownBy(() -> out.write("Hello", -1, 3))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 3, 2))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 0, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 6, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", -2, -1))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Null input is a no-op and does not throw")
    void testNullInputNoOp() {
      StringTemplateOutput out = new StringTemplateOutput();
      assertThatCode(() -> out.write(null, 0, 5)).doesNotThrowAnyException();
      assertThatCode(() -> out.write(null, 2, 1)).doesNotThrowAnyException();
      assertThat(out.toString()).isEmpty();
    }

    @Test
    @DisplayName("Non-String CharSequence is appended correctly without intermediate string")
    void testNonStringCharSequence() {
      StringTemplateOutput out = new StringTemplateOutput();
      StringBuilder sb = new StringBuilder("Custom CharSequence Range");
      out.write(sb, 7, 19);
      assertThat(out.toString()).isEqualTo("CharSequence");
    }
  }

  @Nested
  @DisplayName("Utf8OutputStreamTemplateOutput Contract")
  class Utf8OutputStreamTemplateOutputContract {

    @Test
    @DisplayName("Normal slice [start, end) encodes ASCII correctly")
    void testNormalSliceAscii() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write("Hello, World!", 7, 12);
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("World");
    }

    @Test
    @DisplayName("Normal slice [start, end) encodes UTF-8 multibyte characters correctly")
    void testNormalSliceMultibyte() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write("Xin chào Việt Nam", 4, 8); // "chào"
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("chào");
    }

    @Test
    @DisplayName("Normal slice [start, end) encodes surrogate pairs correctly")
    void testNormalSliceSurrogatePairs() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      String emojis = "🙂🚀🎉✨";
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(emojis, 2, 4); // "🚀" (high surrogate at 2, low at 3)
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("🚀");

      baos.reset();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(emojis, 0, 4); // "🙂🚀"
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("🙂🚀");
    }

    @Test
    @DisplayName("Empty slice start == end writes nothing")
    void testEmptySlice() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write("Hello", 2, 2);
        out.write("Hello", 0, 0);
        out.write("Hello", 5, 5);
      }
      assertThat(baos.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Invalid bounds throw IndexOutOfBoundsException")
    void testInvalidBounds() {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos);
      assertThatThrownBy(() -> out.write("Hello", -1, 3))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 3, 2))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 0, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 6, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Null input is a no-op and does not throw")
    void testNullInputNoOp() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        assertThatCode(() -> out.write(null, 0, 5)).doesNotThrowAnyException();
      }
      assertThat(baos.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Non-String CharSequence writes without toString() invocation")
    void testNonStringCharSequence() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      CharSequence sequence =
          new CharSequence() {
            private final String data = "ZeroAllocationSlice";

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
              throw new AssertionError("subSequence() should not be called!");
            }

            @Override
            public String toString() {
              throw new AssertionError("toString() should not be called!");
            }
          };

      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(sequence, 4, 14); // "Allocation"
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("Allocation");
    }
  }

  @Nested
  @DisplayName("WriterTemplateOutput Contract")
  class WriterTemplateOutputContract {

    @Test
    @DisplayName("Normal slice [start, end) on String writes expected range")
    void testNormalSliceString() throws IOException {
      StringWriter writer = new StringWriter();
      WriterTemplateOutput out = new WriterTemplateOutput(writer);
      out.write("Hello, World!", 7, 12);
      assertThat(writer.toString()).isEqualTo("World");
    }

    @Test
    @DisplayName("Normal slice [start, end) on non-String writes expected range")
    void testNormalSliceNonString() throws IOException {
      StringWriter writer = new StringWriter();
      WriterTemplateOutput out = new WriterTemplateOutput(writer);
      StringBuilder sb = new StringBuilder("Streaming CharSequence Range");
      out.write(sb, 10, 22);
      assertThat(writer.toString()).isEqualTo("CharSequence");
    }

    @Test
    @DisplayName("Large non-String CharSequence exceeding buffer chunk size (1024) writes fully")
    void testLargeNonStringChunking() throws IOException {
      StringWriter writer = new StringWriter();
      WriterTemplateOutput out = new WriterTemplateOutput(writer);

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 3000; i++) {
        sb.append((char) ('a' + (i % 26)));
      }

      CharSequence nonString =
          new CharSequence() {
            @Override
            public int length() {
              return sb.length();
            }

            @Override
            public char charAt(int index) {
              return sb.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
              throw new AssertionError("subSequence() should not be called!");
            }

            @Override
            public String toString() {
              throw new AssertionError("toString() should not be called!");
            }
          };

      out.write(nonString, 100, 2600);
      assertThat(writer.toString()).isEqualTo(sb.substring(100, 2600));
    }

    @Test
    @DisplayName("Empty slice start == end writes nothing")
    void testEmptySlice() throws IOException {
      StringWriter writer = new StringWriter();
      WriterTemplateOutput out = new WriterTemplateOutput(writer);
      out.write("Hello", 2, 2);
      out.write("Hello", 0, 0);
      out.write("Hello", 5, 5);

      StringBuilder sb = new StringBuilder("World");
      out.write(sb, 1, 1);
      assertThat(writer.toString()).isEmpty();
    }

    @Test
    @DisplayName("Invalid bounds throw IndexOutOfBoundsException")
    void testInvalidBounds() {
      StringWriter writer = new StringWriter();
      WriterTemplateOutput out = new WriterTemplateOutput(writer);
      assertThatThrownBy(() -> out.write("Hello", -1, 3))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 3, 2))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 0, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 6, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);

      StringBuilder sb = new StringBuilder("World");
      assertThatThrownBy(() -> out.write(sb, -1, 2)).isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write(sb, 3, 1)).isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write(sb, 0, 10)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Null input is a no-op and does not throw")
    void testNullInputNoOp() throws IOException {
      StringWriter writer = new StringWriter();
      WriterTemplateOutput out = new WriterTemplateOutput(writer);
      assertThatCode(() -> out.write(null, 0, 5)).doesNotThrowAnyException();
      assertThat(writer.toString()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Default TemplateOutput Contract")
  class DefaultTemplateOutputContract {

    static class MinimalTemplateOutput implements TemplateOutput {
      final StringBuilder sb = new StringBuilder();

      @Override
      public void write(CharSequence value) {
        sb.append(value);
      }

      @Override
      public void write(char value) {
        sb.append(value);
      }

      @Override
      public void writeUtf8(byte[] bytes) {}

      @Override
      public void writeInt(int value) {}

      @Override
      public void writeLong(long value) {}

      @Override
      public void writeDouble(double value) {}

      @Override
      public void writeBoolean(boolean value) {}
    }

    @Test
    @DisplayName(
        "Default write(CharSequence, int, int) emits characters individually via write(char)")
    void testDefaultImplementation() throws IOException {
      MinimalTemplateOutput out = new MinimalTemplateOutput();
      out.write("Hello, World!", 7, 12);
      assertThat(out.sb.toString()).isEqualTo("World");

      out.write("Hello", 2, 2);
      assertThat(out.sb.toString()).isEqualTo("World");

      out.write(null, 0, 5);
      assertThat(out.sb.toString()).isEqualTo("World");

      assertThatThrownBy(() -> out.write("Hello", -1, 2))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 3, 1))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 0, 10))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }
}
