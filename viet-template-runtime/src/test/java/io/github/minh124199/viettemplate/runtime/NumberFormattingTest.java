package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NumberFormattingTest {

  @ParameterizedTest
  @ValueSource(
      ints = {
        0,
        1,
        -1,
        42,
        -42,
        100,
        -100,
        123456789,
        -123456789,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE
      })
  void writesIntsToWriterAccurately(int value) throws IOException {
    StringWriter writer = new StringWriter();
    NumberFormatting.write(writer, value);
    assertThat(writer.toString()).isEqualTo(Integer.toString(value));
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        0L,
        1L,
        -1L,
        42L,
        -42L,
        1000L,
        -1000L,
        123456789012345L,
        -123456789012345L,
        Long.MAX_VALUE,
        Long.MIN_VALUE
      })
  void writesLongsToWriterAccurately(long value) throws IOException {
    StringWriter writer = new StringWriter();
    NumberFormatting.write(writer, value);
    assertThat(writer.toString()).isEqualTo(Long.toString(value));
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        0,
        1,
        -1,
        42,
        -42,
        100,
        -100,
        123456789,
        -123456789,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE
      })
  void formatsIntsToBytesAccurately(int value) {
    byte[] target = new byte[32];
    int len = NumberFormatting.formatInt(value, target, 5);
    String formatted = new String(target, 5, len, StandardCharsets.US_ASCII);
    assertThat(formatted).isEqualTo(Integer.toString(value));
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        0L,
        1L,
        -1L,
        42L,
        -42L,
        1000L,
        -1000L,
        123456789012345L,
        -123456789012345L,
        Long.MAX_VALUE,
        Long.MIN_VALUE
      })
  void formatsLongsToBytesAccurately(long value) {
    byte[] target = new byte[32];
    int len = NumberFormatting.formatLong(value, target, 2);
    String formatted = new String(target, 2, len, StandardCharsets.US_ASCII);
    assertThat(formatted).isEqualTo(Long.toString(value));
  }

  @Test
  void writesBooleansAndDoublesAccurately() throws IOException {
    StringWriter writer = new StringWriter();
    NumberFormatting.write(writer, true);
    NumberFormatting.write(writer, false);
    NumberFormatting.write(writer, 3.14159);
    NumberFormatting.write(writer, 42.0);

    assertThat(writer.toString()).isEqualTo("truefalse3.1415942");
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        0,
        1,
        -1,
        42,
        -42,
        100,
        -100,
        123456789,
        -123456789,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE
      })
  void computesStringSizeForInts(int value) {
    assertThat(NumberFormatting.stringSize(value)).isEqualTo(Integer.toString(value).length());
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        0L,
        1L,
        -1L,
        42L,
        -42L,
        1000L,
        -1000L,
        123456789012345L,
        -123456789012345L,
        Long.MAX_VALUE,
        Long.MIN_VALUE
      })
  void computesStringSizeForLongs(long value) {
    assertThat(NumberFormatting.stringSize(value)).isEqualTo(Long.toString(value).length());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Integer.MIN_VALUE,
        Integer.MIN_VALUE + 1,
        -1_000_000_000,
        -1000,
        -100,
        -10,
        -9,
        -1,
        0,
        1,
        9,
        10,
        99,
        100,
        999,
        1000,
        1_000_000_000,
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE
      })
  void formatsIntPreservingSentinelCanaryBytes(int value) {
    byte[] target = new byte[64];
    byte sentinel = 0x55;
    java.util.Arrays.fill(target, sentinel);
    int offset = 13;
    int len = NumberFormatting.formatInt(value, target, offset);
    assertThat(len).isEqualTo(NumberFormatting.stringSize(value));
    assertThat(new String(target, offset, len, StandardCharsets.US_ASCII))
        .isEqualTo(Integer.toString(value));
    for (int i = 0; i < offset; i++) {
      assertThat(target[i]).as("pre-canary at %d for %d", i, value).isEqualTo(sentinel);
    }
    for (int i = offset + len; i < target.length; i++) {
      assertThat(target[i]).as("post-canary at %d for %d", i, value).isEqualTo(sentinel);
    }
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        Long.MIN_VALUE,
        Long.MIN_VALUE + 1,
        -1_000_000_000_000_000_000L,
        -1_000_000_000L,
        -1000L,
        -100L,
        -10L,
        -9L,
        -1L,
        0L,
        1L,
        9L,
        10L,
        99L,
        100L,
        999L,
        1000L,
        1_000_000_000L,
        1_000_000_000_000_000_000L,
        Long.MAX_VALUE - 1,
        Long.MAX_VALUE
      })
  void formatsLongPreservingSentinelCanaryBytes(long value) {
    byte[] target = new byte[64];
    byte sentinel = 0x55;
    java.util.Arrays.fill(target, sentinel);
    int offset = 13;
    int len = NumberFormatting.formatLong(value, target, offset);
    assertThat(len).isEqualTo(NumberFormatting.stringSize(value));
    assertThat(new String(target, offset, len, StandardCharsets.US_ASCII))
        .isEqualTo(Long.toString(value));
    for (int i = 0; i < offset; i++) {
      assertThat(target[i]).as("pre-canary at %d for %d", i, value).isEqualTo(sentinel);
    }
    for (int i = offset + len; i < target.length; i++) {
      assertThat(target[i]).as("post-canary at %d for %d", i, value).isEqualTo(sentinel);
    }
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Integer.MIN_VALUE,
        Integer.MIN_VALUE + 1,
        -1_000_000_000,
        -1000,
        -100,
        -10,
        -9,
        -1,
        0,
        1,
        9,
        10,
        99,
        100,
        999,
        1000,
        1_000_000_000,
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE
      })
  void formatsIntBoundaries(int value) {
    byte[] target = new byte[32];
    int len = NumberFormatting.formatInt(value, target, 0);
    assertThat(len).isEqualTo(NumberFormatting.stringSize(value));
    assertThat(new String(target, 0, len, StandardCharsets.US_ASCII))
        .isEqualTo(Integer.toString(value));
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        Long.MIN_VALUE,
        Long.MIN_VALUE + 1,
        -1_000_000_000_000_000_000L,
        -1_000_000_000L,
        -1000L,
        -100L,
        -10L,
        -9L,
        -1L,
        0L,
        1L,
        9L,
        10L,
        99L,
        100L,
        999L,
        1000L,
        1_000_000_000L,
        1_000_000_000_000_000_000L,
        Long.MAX_VALUE - 1,
        Long.MAX_VALUE
      })
  void formatsLongBoundaries(long value) {
    byte[] target = new byte[32];
    int len = NumberFormatting.formatLong(value, target, 0);
    assertThat(len).isEqualTo(NumberFormatting.stringSize(value));
    assertThat(new String(target, 0, len, StandardCharsets.US_ASCII))
        .isEqualTo(Long.toString(value));
  }

  @Test
  void formatsIntDecimalTransitions() {
    int[] bases = {
      10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000
    };
    byte[] target = new byte[32];
    for (int base : bases) {
      int[] testValues = {base - 1, base, base + 1, -(base - 1), -base, -(base + 1)};
      for (int v : testValues) {
        int len = NumberFormatting.formatInt(v, target, 2);
        assertThat(len).isEqualTo(NumberFormatting.stringSize(v));
        assertThat(new String(target, 2, len, StandardCharsets.US_ASCII))
            .isEqualTo(Integer.toString(v));
      }
    }
  }

  @Test
  void formatsLongDecimalTransitions() {
    long[] bases = {
      10L,
      100L,
      1_000L,
      10_000L,
      100_000L,
      1_000_000L,
      10_000_000L,
      100_000_000L,
      1_000_000_000L,
      10_000_000_000L,
      100_000_000_000L,
      1_000_000_000_000L,
      10_000_000_000_000L,
      100_000_000_000_000L,
      1_000_000_000_000_000L,
      10_000_000_000_000_000L,
      100_000_000_000_000_000L,
      1_000_000_000_000_000_000L
    };
    byte[] target = new byte[32];
    for (long base : bases) {
      long[] testValues = {base - 1, base, base + 1, -(base - 1), -base, -(base + 1)};
      for (long v : testValues) {
        int len = NumberFormatting.formatLong(v, target, 2);
        assertThat(len).isEqualTo(NumberFormatting.stringSize(v));
        assertThat(new String(target, 2, len, StandardCharsets.US_ASCII))
            .isEqualTo(Long.toString(v));
      }
    }
  }

  @Test
  void formatsIntIntoExactSizedBuffer() {
    byte[] target11 = new byte[11];
    int len1 = NumberFormatting.formatInt(Integer.MIN_VALUE, target11, 0);
    assertThat(len1).isEqualTo(11);
    assertThat(new String(target11, 0, len1, StandardCharsets.US_ASCII)).isEqualTo("-2147483648");

    int len2 = NumberFormatting.formatInt(Integer.MIN_VALUE + 1, target11, 0);
    assertThat(len2).isEqualTo(11);
    assertThat(new String(target11, 0, len2, StandardCharsets.US_ASCII)).isEqualTo("-2147483647");

    byte[] target10 = new byte[10];
    int len3 = NumberFormatting.formatInt(Integer.MAX_VALUE, target10, 0);
    assertThat(len3).isEqualTo(10);
    assertThat(new String(target10, 0, len3, StandardCharsets.US_ASCII)).isEqualTo("2147483647");

    byte[] target1 = new byte[1];
    int len4 = NumberFormatting.formatInt(0, target1, 0);
    assertThat(len4).isEqualTo(1);
    assertThat(new String(target1, 0, len4, StandardCharsets.US_ASCII)).isEqualTo("0");
  }

  @Test
  void formatsLongIntoExactSizedBuffer() {
    byte[] target20 = new byte[20];
    int len1 = NumberFormatting.formatLong(Long.MIN_VALUE, target20, 0);
    assertThat(len1).isEqualTo(20);
    assertThat(new String(target20, 0, len1, StandardCharsets.US_ASCII))
        .isEqualTo("-9223372036854775808");

    int len2 = NumberFormatting.formatLong(Long.MIN_VALUE + 1, target20, 0);
    assertThat(len2).isEqualTo(20);
    assertThat(new String(target20, 0, len2, StandardCharsets.US_ASCII))
        .isEqualTo("-9223372036854775807");

    byte[] target19 = new byte[19];
    int len3 = NumberFormatting.formatLong(Long.MAX_VALUE, target19, 0);
    assertThat(len3).isEqualTo(19);
    assertThat(new String(target19, 0, len3, StandardCharsets.US_ASCII))
        .isEqualTo("9223372036854775807");

    byte[] target1 = new byte[1];
    int len4 = NumberFormatting.formatLong(0L, target1, 0);
    assertThat(len4).isEqualTo(1);
    assertThat(new String(target1, 0, len4, StandardCharsets.US_ASCII)).isEqualTo("0");
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Integer.MIN_VALUE,
        Integer.MIN_VALUE + 1,
        -1_000_000_000,
        -1000,
        -100,
        -10,
        -9,
        -1,
        0,
        1,
        9,
        10,
        99,
        100,
        999,
        1000,
        1_000_000_000,
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE
      })
  void formatsIntExactBufferForAllBoundaries(int value) {
    int expectedLen = Integer.toString(value).length();
    byte[] exactBuffer = new byte[expectedLen];
    int written = NumberFormatting.formatInt(value, exactBuffer, 0);
    assertThat(written).isEqualTo(expectedLen);
    assertThat(new String(exactBuffer, 0, written, StandardCharsets.US_ASCII))
        .isEqualTo(Integer.toString(value));
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        Long.MIN_VALUE,
        Long.MIN_VALUE + 1,
        -1_000_000_000_000_000_000L,
        -1_000_000_000L,
        -1000L,
        -100L,
        -10L,
        -9L,
        -1L,
        0L,
        1L,
        9L,
        10L,
        99L,
        100L,
        999L,
        1000L,
        1_000_000_000L,
        1_000_000_000_000_000_000L,
        Long.MAX_VALUE - 1,
        Long.MAX_VALUE
      })
  void formatsLongExactBufferForAllBoundaries(long value) {
    int expectedLen = Long.toString(value).length();
    byte[] exactBuffer = new byte[expectedLen];
    int written = NumberFormatting.formatLong(value, exactBuffer, 0);
    assertThat(written).isEqualTo(expectedLen);
    assertThat(new String(exactBuffer, 0, written, StandardCharsets.US_ASCII))
        .isEqualTo(Long.toString(value));
  }

  @Test
  void deterministicPseudoRandomIntPropertyTests() {
    java.util.Random rng = new java.util.Random(42L);
    byte[] target = new byte[32];
    for (int i = 0; i < 100_000; i++) {
      int v = rng.nextInt();
      String expected = Integer.toString(v);
      int expectedLen = expected.length();
      int size = NumberFormatting.stringSize(v);
      if (size != expectedLen) {
        assertThat(size).as("stringSize mismatch for %d", v).isEqualTo(expectedLen);
      }
      int offset = rng.nextInt(15);
      int writtenLen = NumberFormatting.formatInt(v, target, offset);
      if (writtenLen != expectedLen) {
        assertThat(writtenLen).as("writtenLen mismatch for %d", v).isEqualTo(expectedLen);
      }
      for (int j = 0; j < expectedLen; j++) {
        if (target[offset + j] != (byte) expected.charAt(j)) {
          assertThat(new String(target, offset, writtenLen, StandardCharsets.US_ASCII))
              .as("byte mismatch for %d at index %d", v, j)
              .isEqualTo(expected);
        }
      }
    }
  }

  @Test
  void deterministicPseudoRandomLongPropertyTests() {
    java.util.Random rng = new java.util.Random(84L);
    byte[] target = new byte[32];
    for (int i = 0; i < 100_000; i++) {
      long v = rng.nextLong();
      String expected = Long.toString(v);
      int expectedLen = expected.length();
      int size = NumberFormatting.stringSize(v);
      if (size != expectedLen) {
        assertThat(size).as("stringSize mismatch for %d", v).isEqualTo(expectedLen);
      }
      int offset = rng.nextInt(10);
      int writtenLen = NumberFormatting.formatLong(v, target, offset);
      if (writtenLen != expectedLen) {
        assertThat(writtenLen).as("writtenLen mismatch for %d", v).isEqualTo(expectedLen);
      }
      for (int j = 0; j < expectedLen; j++) {
        if (target[offset + j] != (byte) expected.charAt(j)) {
          assertThat(new String(target, offset, writtenLen, StandardCharsets.US_ASCII))
              .as("byte mismatch for %d at index %d", v, j)
              .isEqualTo(expected);
        }
      }
    }
  }

  @Test
  void reusesSameBufferAcrossMultipleSequentialWrites() {
    byte[] buffer = new byte[512];
    int offset = 0;
    StringBuilder expected = new StringBuilder();

    int[] intValues = {
      0, 1, -1, 42, -42, 100, -100, 12345, -12345, Integer.MIN_VALUE, Integer.MAX_VALUE
    };
    for (int v : intValues) {
      int len = NumberFormatting.formatInt(v, buffer, offset);
      expected.append(v);
      offset += len;
    }

    long[] longValues = {0L, 1L, -1L, 9999999999L, -9999999999L, Long.MIN_VALUE, Long.MAX_VALUE};
    for (long v : longValues) {
      int len = NumberFormatting.formatLong(v, buffer, offset);
      expected.append(v);
      offset += len;
    }

    String actual = new String(buffer, 0, offset, StandardCharsets.US_ASCII);
    assertThat(actual).isEqualTo(expected.toString());
  }
}
