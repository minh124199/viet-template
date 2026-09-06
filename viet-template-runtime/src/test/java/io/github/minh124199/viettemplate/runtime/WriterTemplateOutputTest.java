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
    out.write(", UTF8: ");
    out.writeUtf8("chào".getBytes(StandardCharsets.UTF_8));

    assertThat(writer.toString()).isEqualTo("Items: 5, Active: false, UTF8: chào");
  }
}
