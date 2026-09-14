package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;
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
  @DisplayName("UTF-8 Surrogate Edge Cases Contract")
  class Utf8SurrogateEdgeCasesContract {

    @Test
    @DisplayName("Surrogate pair completely inside range encodes into 4 UTF-8 bytes")
    void testPairInside() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      String input = "Prefix🚀Suffix"; // "🚀" is at [6, 8)
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 6, 8);
      }
      assertThat(baos.toByteArray()).containsExactly(0xF0, 0x9F, 0x9A, 0x80);
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("🚀");

      baos.reset();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 0, input.length());
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("Prefix🚀Suffix");
    }

    @Test
    @DisplayName(
        "High surrogate at end boundary (split pair) encodes as 3-byte isolated surrogate without"
            + " reading past end")
    void testHighSurrogateAtEndBoundary() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      String input =
          "Pre🚀Post"; // rocket: high surrogate at index 3 (\uD83D), low surrogate at index 4
      // (\uDE80)
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 0, 4);
      }
      byte[] expected = new byte[] {0x50, 0x72, 0x65, (byte) 0xED, (byte) 0xA0, (byte) 0xBD};
      assertThat(baos.toByteArray()).isEqualTo(expected);
    }

    @Test
    @DisplayName(
        "Low surrogate at start boundary (split pair) encodes as 3-byte isolated surrogate")
    void testLowSurrogateAtStartBoundary() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      String input =
          "Pre🚀Post"; // rocket: high surrogate at index 3 (\uD83D), low surrogate at index 4
      // (\uDE80)
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 4, 9);
      }
      byte[] expected = new byte[] {(byte) 0xED, (byte) 0xBA, (byte) 0x80, 0x50, 0x6F, 0x73, 0x74};
      assertThat(baos.toByteArray()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Isolated high surrogate (unpaired) encodes as 3-byte UTF-8 sequence")
    void testIsolatedHighSurrogate() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      String input = "abc\uD83Dxyz";
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 3, 4);
      }
      assertThat(baos.toByteArray()).containsExactly(0xED, 0xA0, 0xBD);

      baos.reset();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 0, input.length());
      }
      byte[] expected =
          new byte[] {'a', 'b', 'c', (byte) 0xED, (byte) 0xA0, (byte) 0xBD, 'x', 'y', 'z'};
      assertThat(baos.toByteArray()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Isolated low surrogate (unpaired) encodes as 3-byte UTF-8 sequence")
    void testIsolatedLowSurrogate() throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      String input = "abc\uDE80xyz";
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 3, 4);
      }
      assertThat(baos.toByteArray()).containsExactly(0xED, 0xBA, 0x80);

      baos.reset();
      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(input, 0, input.length());
      }
      byte[] expected =
          new byte[] {'a', 'b', 'c', (byte) 0xED, (byte) 0xBA, (byte) 0x80, 'x', 'y', 'z'};
      assertThat(baos.toByteArray()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("CountingTemplateOutput Contract")
  class CountingTemplateOutputContract {

    @Test
    @DisplayName("Bounds validation throws IndexOutOfBoundsException and consumes zero budget")
    void testBoundsValidation() {
      StringTemplateOutput delegate = new StringTemplateOutput();
      RenderBudget budget = RenderBudget.unlimited();
      CountingTemplateOutput out =
          new CountingTemplateOutput(delegate, budget, TemplateId.of("test"));

      assertThatThrownBy(() -> out.write("Hello", -1, 3))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 3, 2))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 0, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> out.write("Hello", 6, 6))
          .isInstanceOf(IndexOutOfBoundsException.class);

      assertThat(out.written()).isEqualTo(0);
      assertThat(delegate.toString()).isEmpty();
    }

    @Test
    @DisplayName("Empty slice start == end consumes zero characters and writes nothing")
    void testEmptySliceConsumesZero() throws IOException {
      StringTemplateOutput delegate = new StringTemplateOutput();
      CountingTemplateOutput out = new CountingTemplateOutput(delegate, 100);

      out.write("Hello", 2, 2);
      out.write("Hello", 0, 0);
      out.write("Hello", 5, 5);

      assertThat(out.written()).isEqualTo(0);
      assertThat(delegate.toString()).isEmpty();
    }

    @Test
    @DisplayName("Null input is a safe no-op that does not throw and consumes zero budget")
    void testNullInputSafeNoOp() throws IOException {
      StringTemplateOutput delegate = new StringTemplateOutput();
      CountingTemplateOutput out = new CountingTemplateOutput(delegate, 100);

      assertThatCode(() -> out.write(null, 0, 5)).doesNotThrowAnyException();
      assertThatCode(() -> out.write(null, 3, 1)).doesNotThrowAnyException();

      assertThat(out.written()).isEqualTo(0);
      assertThat(delegate.toString()).isEmpty();
    }

    @Test
    @DisplayName("Exact budget consumption matches end - start")
    void testExactBudgetConsumption() throws IOException {
      StringTemplateOutput delegate = new StringTemplateOutput();
      CountingTemplateOutput out = new CountingTemplateOutput(delegate, 100);

      out.write("Hello, World!", 7, 12); // "World" -> 5 chars
      assertThat(out.written()).isEqualTo(5);
      assertThat(delegate.toString()).isEqualTo("World");

      out.write("ABCDEF", 1, 4); // "BCD" -> 3 chars
      assertThat(out.written()).isEqualTo(8);
      assertThat(delegate.toString()).isEqualTo("WorldBCD");
    }

    @Test
    @DisplayName("Exceeding budget limit throws TemplateLimitException without writing remainder")
    void testBudgetLimitExceeded() throws IOException {
      StringTemplateOutput delegate = new StringTemplateOutput();
      CountingTemplateOutput out = new CountingTemplateOutput(delegate, 10);

      out.write("0123456789", 0, 8); // 8 chars consumed
      assertThat(out.written()).isEqualTo(8);
      assertThat(delegate.toString()).isEqualTo("01234567");

      assertThatThrownBy(() -> out.write("ABCD", 0, 4)).isInstanceOf(TemplateLimitException.class);
      assertThat(delegate.toString()).isEqualTo("01234567");
    }
  }

  @Nested
  @DisplayName("Writer Non-String Equivalence Contract")
  class WriterNonStringEquivalenceContract {

    @Test
    @DisplayName("StringBuilder slice writes identical content to reference subSequence.toString()")
    void testStringBuilderEquivalence() throws IOException {
      StringBuilder sb =
          new StringBuilder("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
      int len = sb.length();

      int[][] ranges = {
        {0, 0},
        {5, 5},
        {len, len},
        {0, 10},
        {10, 36},
        {26, len},
        {0, len}
      };

      for (int[] r : ranges) {
        StringWriter sw = new StringWriter();
        WriterTemplateOutput out = new WriterTemplateOutput(sw);
        out.write(sb, r[0], r[1]);
        assertThat(sw.toString()).isEqualTo(sb.subSequence(r[0], r[1]).toString());
      }
    }

    @Test
    @DisplayName("StringBuffer slice writes identical content to reference subSequence.toString()")
    void testStringBufferEquivalence() throws IOException {
      StringBuffer sbuf = new StringBuffer("SynchronizedStringBuffer-Benchmark-Content-123456789");
      int len = sbuf.length();

      int[][] ranges = {
        {0, 0},
        {0, 12},
        {12, 21},
        {22, len},
        {0, len}
      };

      for (int[] r : ranges) {
        StringWriter sw = new StringWriter();
        WriterTemplateOutput out = new WriterTemplateOutput(sw);
        out.write(sbuf, r[0], r[1]);
        assertThat(sw.toString()).isEqualTo(sbuf.subSequence(r[0], r[1]).toString());
      }
    }

    @Test
    @DisplayName(
        "Custom CharSequence across chunk boundaries (> 1024) writes identical content to reference"
            + " slicing")
    void testCustomCharSequenceChunkedEquivalence() throws IOException {
      StringBuilder source = new StringBuilder();
      for (int i = 0; i < 4000; i++) {
        source.append((char) ('A' + (i % 26)));
      }

      CharSequence custom =
          new CharSequence() {
            @Override
            public int length() {
              return source.length();
            }

            @Override
            public char charAt(int index) {
              return source.charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
              return source.subSequence(start, end);
            }

            @Override
            public String toString() {
              return source.toString();
            }
          };

      int[][] ranges = {
        {0, 0},
        {10, 50},
        {100, 1000},
        {500, 1524},
        {100, 3500},
        {0, custom.length()}
      };

      for (int[] r : ranges) {
        StringWriter sw = new StringWriter();
        WriterTemplateOutput out = new WriterTemplateOutput(sw);
        out.write(custom, r[0], r[1]);
        assertThat(sw.toString()).isEqualTo(custom.subSequence(r[0], r[1]).toString());
      }
    }
  }

  @Nested
  @DisplayName("Hostile CharSequence Contract")
  class HostileCharSequenceContract {

    static class HostileCharSequence implements CharSequence {
      private final String data;

      HostileCharSequence(String data) {
        this.data = data;
      }

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
        throw new UnsupportedOperationException(
            "subSequence() must not be called during range write");
      }

      @Override
      public String toString() {
        throw new UnsupportedOperationException("toString() must not be called during range write");
      }
    }

    @Test
    @DisplayName("StringTemplateOutput writes hostile sequence without subSequence() or toString()")
    void testStringTemplateOutputHostile() {
      String raw = "HostileCharSequenceContentForTestingZeroAllocation";
      HostileCharSequence hostile = new HostileCharSequence(raw);
      StringTemplateOutput out = new StringTemplateOutput();

      out.write(hostile, 7, 23);
      assertThat(out.toString()).isEqualTo("CharSequenceCont");
    }

    @Test
    @DisplayName(
        "Utf8OutputStreamTemplateOutput writes hostile sequence without subSequence() or"
            + " toString()")
    void testUtf8OutputStreamTemplateOutputHostile() throws IOException {
      String raw = "HostileCharSequenceXinChàoThếGiới123";
      HostileCharSequence hostile = new HostileCharSequence(raw);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      try (Utf8OutputStreamTemplateOutput out = new Utf8OutputStreamTemplateOutput(baos)) {
        out.write(hostile, 7, 26);
      }
      assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("CharSequenceXinChào");
    }

    @Test
    @DisplayName(
        "WriterTemplateOutput writes small and large chunked hostile sequence without subSequence()"
            + " or toString()")
    void testWriterTemplateOutputHostile() throws IOException {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 3000; i++) {
        sb.append((char) ('a' + (i % 26)));
      }
      HostileCharSequence hostile = new HostileCharSequence(sb.toString());

      StringWriter swSmall = new StringWriter();
      WriterTemplateOutput outSmall = new WriterTemplateOutput(swSmall);
      outSmall.write(hostile, 10, 50);
      assertThat(swSmall.toString()).isEqualTo(sb.substring(10, 50));

      StringWriter swLarge = new StringWriter();
      WriterTemplateOutput outLarge = new WriterTemplateOutput(swLarge);
      outLarge.write(hostile, 50, 2500);
      assertThat(swLarge.toString()).isEqualTo(sb.substring(50, 2500));
    }

    @Test
    @DisplayName(
        "CountingTemplateOutput writes hostile sequence without subSequence() or toString()")
    void testCountingTemplateOutputHostile() throws IOException {
      String raw = "HostileCountingOutputRangeTest";
      HostileCharSequence hostile = new HostileCharSequence(raw);
      StringTemplateOutput delegate = new StringTemplateOutput();
      CountingTemplateOutput out = new CountingTemplateOutput(delegate, 100);

      out.write(hostile, 7, 21);
      assertThat(out.written()).isEqualTo(14);
      assertThat(delegate.toString()).isEqualTo("CountingOutput");
    }
  }

  @Nested
  @DisplayName("Property Fuzzing Contract")
  class PropertyFuzzingContract {

    private static final int FUZZ_ITERATIONS = 100_000;

    @Test
    @DisplayName(
        "100,000 deterministic property fuzz test with seed 42L compares all implementations"
            + " against reference slicing")
    void testFuzzingAcrossAllImplementations() throws IOException {
      Random rng = new Random(42L);

      String[] corpus =
          new String[] {
            "The quick brown fox jumps over the lazy dog 0123456789 !@#$%^&*()_+-=[]{}|;':\",./<>?",
            "Tiếng Việt có dấu: Hà Nội, TP Hồ Chí Minh, Đà Nẵng, Cần Thơ, Hải Phòng. Kỹ thuật lập"
                + " trình và tối ưu hóa bộ nhớ.",
            "こんにちは世界、你好世界、안녕하세요 세계! 漢字、ひらがな、カタカナ、한글.",
            "Unicode astral plane emojis:"
                + " 🚀🎉✨🌟💡🛡️⚡🎯🌈💎🤖📦☕🍵🍕🍔🙂😀😁😂🤣😃😄😅😆😉😊😋😎",
            buildLongMixedString()
          };

      StringTemplateOutput stringOut = new StringTemplateOutput(2048);
      StringWriter stringWriter = new StringWriter(2048);
      WriterTemplateOutput writerOut = new WriterTemplateOutput(stringWriter);
      ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
      Utf8OutputStreamTemplateOutput utf8Out = new Utf8OutputStreamTemplateOutput(baos, 8192);
      MinimalTemplateOutput minimalOut = new MinimalTemplateOutput();

      for (int iter = 0; iter < FUZZ_ITERATIONS; iter++) {
        String base = corpus[rng.nextInt(corpus.length)];
        int len = base.length();

        int start = rng.nextInt(len + 1);
        int end = rng.nextInt(len + 1);
        if (start > end) {
          int tmp = start;
          start = end;
          end = tmp;
        }

        if (start > 0
            && start < len
            && Character.isLowSurrogate(base.charAt(start))
            && Character.isHighSurrogate(base.charAt(start - 1))) {
          start--;
        }
        if (end > 0
            && end < len
            && Character.isHighSurrogate(base.charAt(end - 1))
            && Character.isLowSurrogate(base.charAt(end))) {
          end++;
        }

        CharSequence input =
            switch (iter % 4) {
              case 0 -> base;
              case 1 -> new StringBuilder(base);
              case 2 -> new StringBuffer(base);
              default -> new CustomCharSequenceFixture(base);
            };

        String expected = input.subSequence(start, end).toString();
        int expectedChars = end - start;

        // 1. StringTemplateOutput
        stringOut.reset();
        stringOut.write(input, start, end);
        String actualStr = stringOut.toString();
        if (!actualStr.equals(expected)) {
          assertThat(actualStr).isEqualTo(expected);
        }

        // 2. WriterTemplateOutput
        stringWriter.getBuffer().setLength(0);
        writerOut.write(input, start, end);
        String actualWriter = stringWriter.toString();
        if (!actualWriter.equals(expected)) {
          assertThat(actualWriter).isEqualTo(expected);
        }

        // 3. Utf8OutputStreamTemplateOutput
        baos.reset();
        utf8Out.write(input, start, end);
        utf8Out.flush();
        String actualUtf8 = baos.toString(StandardCharsets.UTF_8);
        if (!actualUtf8.equals(expected)) {
          assertThat(actualUtf8).isEqualTo(expected);
        }

        // 4. CountingTemplateOutput
        stringOut.reset();
        RenderBudget budget = RenderBudget.unlimited();
        CountingTemplateOutput countingOut =
            new CountingTemplateOutput(stringOut, budget, TemplateId.of("fuzz"));
        countingOut.write(input, start, end);
        if (countingOut.written() != expectedChars) {
          assertThat(countingOut.written()).isEqualTo(expectedChars);
        }
        String actualCounting = stringOut.toString();
        if (!actualCounting.equals(expected)) {
          assertThat(actualCounting).isEqualTo(expected);
        }

        // 5. MinimalTemplateOutput (Default SPI write(CharSequence, int, int))
        minimalOut.sb.setLength(0);
        minimalOut.write(input, start, end);
        String actualMinimal = minimalOut.sb.toString();
        if (!actualMinimal.equals(expected)) {
          assertThat(actualMinimal).isEqualTo(expected);
        }
      }
    }

    private static String buildLongMixedString() {
      StringBuilder sb = new StringBuilder(3000);
      String[] fragments = {
        "Short ASCII fragment 12345. ",
        "Đoạn văn bản tiếng Việt dài với nhiều ký tự Unicode phức tạp. ",
        "日本語と中国語のテキストフラグメントです。你好世界。 ",
        "Astral emoji block: 🚀🎉🔥✨💎🤖🌟. ",
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
      };
      while (sb.length() < 2500) {
        for (String frag : fragments) {
          sb.append(frag);
        }
      }
      return sb.toString();
    }

    static class CustomCharSequenceFixture implements CharSequence {
      private final String text;

      CustomCharSequenceFixture(String text) {
        this.text = text;
      }

      @Override
      public int length() {
        return text.length();
      }

      @Override
      public char charAt(int index) {
        return text.charAt(index);
      }

      @Override
      public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
      }

      @Override
      public String toString() {
        return text;
      }
    }
  }

  @Nested
  @DisplayName("Backward Compatibility TCK Contract")
  class BackwardCompatibilityTckContract {

    @Test
    @DisplayName(
        "Minimal custom TemplateOutput inheriting default write(CharSequence, int, int) emits chars"
            + " correctly")
    void testMinimalTemplateOutputTck() throws IOException {
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

  static class CountingTemplateOutput implements TemplateOutput {
    private final TemplateOutput delegate;
    private final RenderBudget budget;
    private final TemplateId templateId;

    public CountingTemplateOutput(
        TemplateOutput delegate, RenderBudget budget, TemplateId templateId) {
      this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
      this.budget = Objects.requireNonNull(budget, "budget must not be null");
      this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    }

    public CountingTemplateOutput(TemplateOutput delegate, long maxChars, TemplateId templateId) {
      this(delegate, new RenderBudget(maxChars, 0L, Integer.MAX_VALUE), templateId);
    }

    public CountingTemplateOutput(TemplateOutput delegate, long maxChars) {
      this(delegate, maxChars, TemplateId.of("test"));
    }

    public TemplateOutput delegate() {
      return delegate;
    }

    public RenderBudget budget() {
      return budget;
    }

    public TemplateId templateId() {
      return templateId;
    }

    public long written() {
      return budget.charactersWritten();
    }

    protected void checkLimit(int added) {
      budget.consumeCharacters(added, templateId, SourceSpan.UNKNOWN);
    }

    @Override
    public void write(CharSequence value) throws IOException {
      if (value != null) {
        checkLimit(value.length());
        delegate.write(value);
      }
    }

    @Override
    public void write(CharSequence value, int start, int end) throws IOException {
      if (value != null) {
        Objects.checkFromToIndex(start, end, value.length());
        checkLimit(end - start);
        delegate.write(value, start, end);
      }
    }

    @Override
    public void write(char value) throws IOException {
      checkLimit(1);
      delegate.write(value);
    }

    @Override
    public void writeUtf8(byte[] bytes) throws IOException {
      if (bytes != null) {
        checkLimit(bytes.length);
        delegate.writeUtf8(bytes);
      }
    }

    @Override
    public void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
      if (bytes != null) {
        checkLimit(length);
        delegate.writeUtf8(bytes, offset, length);
      }
    }

    @Override
    public void writeInt(int value) throws IOException {
      checkLimit(NumberFormatting.stringSize(value));
      delegate.writeInt(value);
    }

    @Override
    public void writeLong(long value) throws IOException {
      checkLimit(NumberFormatting.stringSize(value));
      delegate.writeLong(value);
    }

    @Override
    public void writeDouble(double value) throws IOException {
      String s = Double.toString(value);
      checkLimit(s.length());
      delegate.writeDouble(value);
    }

    @Override
    public void writeFloat(float value) throws IOException {
      String s = Float.toString(value);
      checkLimit(s.length());
      delegate.writeFloat(value);
    }

    @Override
    public void writeShort(short value) throws IOException {
      checkLimit(NumberFormatting.stringSize(value));
      delegate.writeShort(value);
    }

    @Override
    public void writeByte(byte value) throws IOException {
      checkLimit(NumberFormatting.stringSize(value));
      delegate.writeByte(value);
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
      checkLimit(value ? 4 : 5);
      delegate.writeBoolean(value);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }
  }
}
