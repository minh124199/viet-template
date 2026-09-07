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
}
