package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HtmlTextEscaperTest {

  private final HtmlTextEscaper escaper = HtmlTextEscaper.INSTANCE;

  @Test
  @DisplayName("EscapeMode is HTML_TEXT")
  void testMode() {
    assertThat(escaper.mode()).isEqualTo(EscapeMode.HTML_TEXT);
  }

  @Test
  @DisplayName("Null or empty inputs produce empty output")
  void testNullOrEmpty() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(null, out);
    assertThat(out.toString()).isEmpty();

    escaper.escape("", out);
    assertThat(out.toString()).isEmpty();
  }

  @Test
  @DisplayName(
      "Fast-path: string without special characters is written unchanged without modification")
  void testNoEscapingNeeded() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("Hello World 123", out);
    assertThat(out.toString()).isEqualTo("Hello World 123");
  }

  @Test
  @DisplayName("Standard HTML text entities are correctly escaped")
  void testHtmlTextEscaping() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("a < b && c > d 'single' \"double\"", out);
    assertThat(out.toString())
        .isEqualTo("a &lt; b &amp;&amp; c &gt; d &#39;single&#39; &quot;double&quot;");
  }

  @Test
  @DisplayName("SafeHtml instances bypass escaping")
  void testSafeHtmlBypass() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(SafeHtml.of("<span><b>bold</b> & <i>italic</i></span>"), out);
    assertThat(out.toString()).isEqualTo("<span><b>bold</b> & <i>italic</i></span>");
  }

  @Test
  @DisplayName("Escaping at beginning, middle, and end of string")
  void testBoundaryEscaping() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("&middle<", out);
    assertThat(out.toString()).isEqualTo("&amp;middle&lt;");
  }

  static final class HostileCharSequence implements CharSequence {
    private final String content;

    HostileCharSequence(String content) {
      this.content = content;
    }

    @Override
    public int length() {
      return content.length();
    }

    @Override
    public char charAt(int index) {
      return content.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      throw new AssertionError(
          "subSequence(" + start + ", " + end + ") must not be called during escaping!");
    }

    @Override
    public String toString() {
      throw new AssertionError("toString() must not be called on HostileCharSequence!");
    }
  }

  @Test
  @DisplayName("Hostile CharSequence: subSequence() is never invoked during escaping")
  void testHostileCharSequenceEscaping() throws IOException {
    HostileCharSequence hostile = new HostileCharSequence("hello <world> & 'foo' \"bar\" baz");
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape(hostile, out);
    assertThat(out.toString())
        .isEqualTo("hello &lt;world&gt; &amp; &#39;foo&#39; &quot;bar&quot; baz");

    HostileCharSequence safeHostile = new HostileCharSequence("clean text without specials");
    StringTemplateOutput safeOut = new StringTemplateOutput();
    escaper.escape(safeHostile, safeOut);
    assertThat(safeOut.toString()).isEqualTo("clean text without specials");
  }

  static final class RecordingTemplateOutput implements TemplateOutput {
    record Call(String type, CharSequence value, int start, int end) {}

    final List<Call> calls = new ArrayList<>();

    @Override
    public void write(CharSequence value) {
      calls.add(new Call("writeWhole", value, -1, -1));
    }

    @Override
    public void write(CharSequence value, int start, int end) {
      calls.add(new Call("writeRange", value, start, end));
    }

    @Override
    public void write(char value) {
      calls.add(new Call("writeChar", String.valueOf(value), -1, -1));
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
  @DisplayName("Custom Range Output: abc&def emits range(0, 3), entity(&amp;), and range(4, 7)")
  void testCustomRangeOutput() throws IOException {
    RecordingTemplateOutput recording = new RecordingTemplateOutput();
    String input = "abc&def";
    escaper.escape(input, recording);

    assertThat(recording.calls).hasSize(3);
    assertThat(recording.calls.get(0))
        .isEqualTo(new RecordingTemplateOutput.Call("writeRange", input, 0, 3));
    assertThat(recording.calls.get(1))
        .isEqualTo(new RecordingTemplateOutput.Call("writeWhole", "&amp;", -1, -1));
    assertThat(recording.calls.get(2))
        .isEqualTo(new RecordingTemplateOutput.Call("writeRange", input, 4, 7));
  }

  @Test
  @DisplayName("Individual entities: &, <, >, \", '")
  void testIndividualEntities() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();
    escaper.escape("&", out);
    assertThat(out.toString()).isEqualTo("&amp;");

    out.reset();
    escaper.escape("<", out);
    assertThat(out.toString()).isEqualTo("&lt;");

    out.reset();
    escaper.escape(">", out);
    assertThat(out.toString()).isEqualTo("&gt;");

    out.reset();
    escaper.escape("\"", out);
    assertThat(out.toString()).isEqualTo("&quot;");

    out.reset();
    escaper.escape("'", out);
    assertThat(out.toString()).isEqualTo("&#39;");
  }

  @Test
  @DisplayName("Leading, trailing, and adjacent entities: &hello, hello&, &&, <>, &<>\"'")
  void testLeadingTrailingAdjacentEntities() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();

    escaper.escape("&hello", out);
    assertThat(out.toString()).isEqualTo("&amp;hello");

    out.reset();
    escaper.escape("hello&", out);
    assertThat(out.toString()).isEqualTo("hello&amp;");

    out.reset();
    escaper.escape("&&", out);
    assertThat(out.toString()).isEqualTo("&amp;&amp;");

    out.reset();
    escaper.escape("<>", out);
    assertThat(out.toString()).isEqualTo("&lt;&gt;");

    out.reset();
    escaper.escape("&<>\"'", out);
    assertThat(out.toString()).isEqualTo("&amp;&lt;&gt;&quot;&#39;");

    out.reset();
    escaper.escape("prefix&<>'\"suffix", out);
    assertThat(out.toString()).isEqualTo("prefix&amp;&lt;&gt;&#39;&quot;suffix");
  }

  @Test
  @DisplayName("Realistic mixed text, Vietnamese, CJK, and emoji with surrogate pairs")
  void testMixedUnicodeAndEmojiSurrogatePairs() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();

    escaper.escape("🙂 <emoji> 🚀 & done", out);
    assertThat(out.toString()).isEqualTo("🙂 &lt;emoji&gt; 🚀 &amp; done");

    out.reset();
    escaper.escape("Xin chào <thế giới> & 'Việt Nam'! Chúc một ngày \"tốt lành\" 🌟", out);
    assertThat(out.toString())
        .isEqualTo(
            "Xin chào &lt;thế giới&gt; &amp; &#39;Việt Nam&#39;! Chúc một ngày &quot;tốt lành&quot;"
                + " 🌟");

    out.reset();
    escaper.escape("日本語 <テスト> & 中文 '引用' \"双引号\" 🍵", out);
    assertThat(out.toString())
        .isEqualTo("日本語 &lt;テスト&gt; &amp; 中文 &#39;引用&#39; &quot;双引号&quot; 🍵");
  }

  @Test
  @DisplayName("Non-String CharSequence: StringBuilder and StringBuffer")
  void testNonStringCharSequence() throws IOException {
    StringTemplateOutput out = new StringTemplateOutput();

    StringBuilder sb = new StringBuilder("StringBuilder: <tag> & 'single' \"double\"");
    escaper.escape(sb, out);
    assertThat(out.toString())
        .isEqualTo("StringBuilder: &lt;tag&gt; &amp; &#39;single&#39; &quot;double&quot;");

    out.reset();
    StringBuffer sbuf = new StringBuffer("StringBuffer: a < b > c & 'x' \"y\"");
    escaper.escape(sbuf, out);
    assertThat(out.toString())
        .isEqualTo("StringBuffer: a &lt; b &gt; c &amp; &#39;x&#39; &quot;y&quot;");
  }

  private static String referenceEscape(CharSequence input) {
    if (input == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        case '\'' -> sb.append("&#39;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  @Test
  @DisplayName(
      "Deterministic property/fuzz testing (100,000 cases with fixed seed 42L) across escape"
          + " densities")
  void testDeterministicPropertyFuzz() throws IOException {
    SplittableRandom rng = new SplittableRandom(42L);
    double[] densities = {0.0, 0.01, 0.05, 0.10, 0.25, 0.50, 1.0};
    char[] specials = {'&', '<', '>', '"', '\''};
    String safeAscii =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-./=!?#@$%^*+";
    String[] unicodeSamples = {"Tiếng Việt", "Xin chào", "日本語", "中文", "🙂", "🚀", "🎉", "🔥"};

    StringTemplateOutput out = new StringTemplateOutput(256);

    for (int i = 0; i < 100_000; i++) {
      double density = densities[i % densities.length];
      int length = rng.nextInt(48);
      StringBuilder inputBuilder = new StringBuilder(length);

      for (int pos = 0; pos < length; pos++) {
        if (rng.nextDouble() < density) {
          inputBuilder.append(specials[rng.nextInt(specials.length)]);
        } else {
          int choice = rng.nextInt(10);
          if (choice < 7) {
            inputBuilder.append(safeAscii.charAt(rng.nextInt(safeAscii.length())));
          } else {
            inputBuilder.append(unicodeSamples[rng.nextInt(unicodeSamples.length)]);
          }
        }
      }

      String input = inputBuilder.toString();
      String expected = referenceEscape(input);

      out.reset();
      escaper.escape(input, out);
      assertThat(out.toString())
          .as("Mismatch at iteration %d with density %f for input: %s", i, density, input)
          .isEqualTo(expected);
    }
  }
}
