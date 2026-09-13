package io.github.minh124199.viettemplate.benchmarks.output;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticDirectByteOutput;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticDirectHtmlTextEscaper;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticFastNumberFormatting;
import io.github.minh124199.viettemplate.benchmarks.output.prototype.DiagnosticPooledUtf8Output;
import io.github.minh124199.viettemplate.runtime.HtmlTextEscaper;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutputQualificationCorrectnessTest {

  @Test
  @DisplayName(
      "DiagnosticFastNumberFormatting: int formatting matches Integer.toString identically")
  void testIntFormatting() {
    int[] testCases = {
      0,
      1,
      -1,
      7,
      -7,
      10,
      -10,
      42,
      -42,
      99,
      -99,
      100,
      -100,
      999,
      -999,
      1000,
      -1000,
      12345,
      -12345,
      1000000,
      -1000000,
      123456789,
      -123456789,
      Integer.MAX_VALUE,
      Integer.MIN_VALUE
    };

    byte[] target = new byte[32];
    for (int v : testCases) {
      int len = DiagnosticFastNumberFormatting.formatInt(v, target, 2);
      String expected = Integer.toString(v);
      String actual = new String(target, 2, len, StandardCharsets.US_ASCII);
      assertThat(actual).as("Formatting int %d", v).isEqualTo(expected);
    }
  }

  @Test
  @DisplayName("DiagnosticFastNumberFormatting: long formatting matches Long.toString identically")
  void testLongFormatting() {
    long[] testCases = {
      0L,
      1L,
      -1L,
      42L,
      -42L,
      10000000000L,
      -10000000000L,
      1234567890123456789L,
      -1234567890123456789L,
      Long.MAX_VALUE,
      Long.MIN_VALUE
    };

    byte[] target = new byte[32];
    for (long v : testCases) {
      int len = DiagnosticFastNumberFormatting.formatLong(v, target, 2);
      String expected = Long.toString(v);
      String actual = new String(target, 2, len, StandardCharsets.US_ASCII);
      assertThat(actual).as("Formatting long %d", v).isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "HTML escaping: {0}")
  @ValueSource(
      strings = {
        "",
        "Hello World",
        "<script>alert('xss')</script>",
        "Tom & Jerry",
        "\"quote\" and 'single'",
        "Mixed: <div>\"Hello & Goodbye\" 'test'</div>",
        "Tiếng Việt có dấu: Cà phê trứng & Phở bò <ngon>",
        "Emoji: 👋 🌏 ☕ <tag> 'quote'"
      })
  @DisplayName(
      "DiagnosticDirectHtmlTextEscaper produces byte-for-byte identical output to HtmlTextEscaper")
  void testHtmlEscaperEquivalence(String input) throws IOException {
    StringTemplateOutput outExpected = new StringTemplateOutput();
    HtmlTextEscaper.INSTANCE.escape(input, outExpected);

    StringTemplateOutput outActual = new StringTemplateOutput();
    DiagnosticDirectHtmlTextEscaper.escape(input, outActual);

    assertThat(outActual.toString())
        .as("HTML escaping comparison for: %s", input)
        .isEqualTo(outExpected.toString());
  }

  @Test
  @DisplayName(
      "DiagnosticPooledUtf8Output produces identical bytes to Utf8OutputStreamTemplateOutput")
  void testPooledOutputEquivalence() throws IOException {
    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();

    try (Utf8OutputStreamTemplateOutput out1 = new Utf8OutputStreamTemplateOutput(baos1);
        DiagnosticPooledUtf8Output out2 = new DiagnosticPooledUtf8Output(baos2)) {
      for (int i = 0; i < 50; i++) {
        out1.write("Row-");
        out2.write("Row-");
        out1.writeInt(i * 100);
        out2.writeInt(i * 100);
        out1.write(": ");
        out2.write(": ");
        out1.writeLong(i * 1000000000L);
        out2.writeLong(i * 1000000000L);
        out1.write(" | ");
        out2.write(" | ");
        out1.writeBoolean(i % 2 == 0);
        out2.writeBoolean(i % 2 == 0);
        out1.write(" | Unicode: Việt Nam 👋; \n");
        out2.write(" | Unicode: Việt Nam 👋; \n");
      }
    }

    assertThat(baos2.toByteArray()).isEqualTo(baos1.toByteArray());
  }

  @Test
  @DisplayName(
      "DiagnosticDirectByteOutput produces identical bytes to Utf8OutputStreamTemplateOutput")
  void testDirectByteOutputEquivalence() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DiagnosticDirectByteOutput direct = new DiagnosticDirectByteOutput();

    try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
      for (int i = 0; i < 50; i++) {
        out.write("Item ");
        direct.write("Item ");
        out.writeInt(i);
        direct.writeInt(i);
        out.write(" = ");
        direct.write(" = ");
        out.writeDouble(i * 1.5);
        direct.writeDouble(i * 1.5);
        out.write(" [");
        direct.write(" [");
        out.writeBoolean(i % 3 == 0);
        direct.writeBoolean(i % 3 == 0);
        out.write("]\n");
        direct.write("]\n");
      }
    }

    assertThat(direct.toByteArray()).isEqualTo(baos.toByteArray());
    assertThat(direct.toUtf8String()).isEqualTo(baos.toString(StandardCharsets.UTF_8));
  }
}
