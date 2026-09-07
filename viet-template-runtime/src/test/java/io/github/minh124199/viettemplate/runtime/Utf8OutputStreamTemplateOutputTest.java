package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
