package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WriterTemplateOutputTest {

  @Test
  void streamsOutputDirectlyToWriter() throws IOException {
    StringWriter writer = new StringWriter();
    WriterTemplateOutput out = new WriterTemplateOutput(writer);

    out.write("Items: ");
    out.writeInt(5);
    out.write(", Active: ");
    out.writeBoolean(false);
    out.write(", Long: ");
    out.writeLong(9876543210L);
    out.write(", Double: ");
    out.writeDouble(1.25);
    out.write(", Float: ");
    out.writeFloat(2.5f);
    out.write(", Short: ");
    out.writeShort((short) 100);
    out.write(", Byte: ");
    out.writeByte((byte) 8);
    out.write(", UTF8: ");
    out.writeUtf8("chào".getBytes(StandardCharsets.UTF_8));
    out.flush();

    assertThat(writer.toString())
        .isEqualTo(
            "Items: 5, Active: false, Long: 9876543210, Double: 1.25, Float: 2.5, Short: 100, Byte:"
                + " 8, UTF8: chào");
  }

  @Test
  void appendsCharSequenceWithoutForcedToStringCall() throws IOException {
    StringWriter writer = new StringWriter();
    WriterTemplateOutput out = new WriterTemplateOutput(writer);

    CharSequence sequence =
        new CharSequence() {
          private final String val = "LazyCharSequence";

          @Override
          public int length() {
            return val.length();
          }

          @Override
          public char charAt(int index) {
            return val.charAt(index);
          }

          @Override
          public CharSequence subSequence(int start, int end) {
            return val.subSequence(start, end);
          }

          @Override
          public String toString() {
            throw new AssertionError("toString() should not be called on CharSequence!");
          }
        };

    out.write(sequence);
    assertThat(writer.toString()).isEqualTo("LazyCharSequence");
  }
}
