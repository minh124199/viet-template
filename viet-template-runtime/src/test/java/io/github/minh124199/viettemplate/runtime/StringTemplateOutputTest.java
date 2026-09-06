package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StringTemplateOutputTest {

  @Test
  void writesVariousTypesCorrectly() {
    StringTemplateOutput out = new StringTemplateOutput();
    out.write("Hello ");
    out.write('W');
    out.write("orld! Count: ");
    out.writeInt(42);
    out.write(", Long: ");
    out.writeLong(1000L);
    out.write(", Double: ");
    out.writeDouble(3.14);
    out.write(", Flag: ");
    out.writeBoolean(true);

    assertThat(out.toString())
        .isEqualTo("Hello World! Count: 42, Long: 1000, Double: 3.14, Flag: true");
  }

  @Test
  void writesUtf8Bytes() {
    StringTemplateOutput out = new StringTemplateOutput();
    byte[] bytes = "Xin chào Việt Nam".getBytes(StandardCharsets.UTF_8);
    out.writeUtf8(bytes);

    assertThat(out.toString()).isEqualTo("Xin chào Việt Nam");
  }

  @Test
  void resetsOutputBuffer() {
    StringTemplateOutput out = new StringTemplateOutput();
    out.write("temporary");
    assertThat(out.length()).isEqualTo(9);

    out.reset();
    assertThat(out.length()).isEqualTo(0);
    assertThat(out.toString()).isEmpty();

    out.write("new content");
    assertThat(out.toString()).isEqualTo("new content");
  }
}
