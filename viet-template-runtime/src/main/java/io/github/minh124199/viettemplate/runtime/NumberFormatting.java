package io.github.minh124199.viettemplate.runtime;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/** Utility for low-overhead, allocation-free formatting of primitive numbers and values. */
public final class NumberFormatting {

  private static final byte[] INT_MIN_BYTES = "-2147483648".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] LONG_MIN_BYTES =
      "-9223372036854775808".getBytes(StandardCharsets.US_ASCII);

  private NumberFormatting() {}

  /** Writes an integer to a {@link Writer} without creating a heap {@link String}. */
  public static void write(Writer writer, int value) throws IOException {
    if (value == Integer.MIN_VALUE) {
      writer.write("-2147483648");
      return;
    }
    if (value == 0) {
      writer.write('0');
      return;
    }
    char[] buf = new char[11];
    int pos = 11;
    boolean negative = value < 0;
    int v = negative ? -value : value;
    while (v > 0) {
      buf[--pos] = (char) ('0' + (v % 10));
      v /= 10;
    }
    if (negative) {
      buf[--pos] = '-';
    }
    writer.write(buf, pos, 11 - pos);
  }

  /** Writes a long to a {@link Writer} without creating a heap {@link String}. */
  public static void write(Writer writer, long value) throws IOException {
    if (value == Long.MIN_VALUE) {
      writer.write("-9223372036854775808");
      return;
    }
    if (value == 0) {
      writer.write('0');
      return;
    }
    char[] buf = new char[20];
    int pos = 20;
    boolean negative = value < 0;
    long v = negative ? -value : value;
    while (v > 0) {
      buf[--pos] = (char) ('0' + (v % 10));
      v /= 10;
    }
    if (negative) {
      buf[--pos] = '-';
    }
    writer.write(buf, pos, 20 - pos);
  }

  /** Writes a double to a {@link Writer}. */
  public static void write(Writer writer, double value) throws IOException {
    if (value == (long) value
        && value >= Long.MIN_VALUE
        && value <= Long.MAX_VALUE
        && !Double.isNaN(value)
        && !Double.isInfinite(value)) {
      write(writer, (long) value);
      return;
    }
    writer.write(Double.toString(value));
  }

  /** Writes a boolean to a {@link Writer} without allocating a string. */
  public static void write(Writer writer, boolean value) throws IOException {
    writer.write(value ? "true" : "false");
  }

  /**
   * Formats an integer directly into a byte buffer as ASCII digits.
   *
   * @param value the int value
   * @param target the destination byte array
   * @param offset the starting offset
   * @return the number of bytes written
   */
  public static int formatInt(int value, byte[] target, int offset) {
    if (value == Integer.MIN_VALUE) {
      System.arraycopy(INT_MIN_BYTES, 0, target, offset, INT_MIN_BYTES.length);
      return INT_MIN_BYTES.length;
    }
    if (value == 0) {
      target[offset] = '0';
      return 1;
    }
    byte[] temp = new byte[11];
    int pos = 11;
    boolean negative = value < 0;
    int v = negative ? -value : value;
    while (v > 0) {
      temp[--pos] = (byte) ('0' + (v % 10));
      v /= 10;
    }
    if (negative) {
      temp[--pos] = '-';
    }
    int len = 11 - pos;
    System.arraycopy(temp, pos, target, offset, len);
    return len;
  }

  /**
   * Formats a long directly into a byte buffer as ASCII digits.
   *
   * @param value the long value
   * @param target the destination byte array
   * @param offset the starting offset
   * @return the number of bytes written
   */
  public static int formatLong(long value, byte[] target, int offset) {
    if (value == Long.MIN_VALUE) {
      System.arraycopy(LONG_MIN_BYTES, 0, target, offset, LONG_MIN_BYTES.length);
      return LONG_MIN_BYTES.length;
    }
    if (value == 0) {
      target[offset] = '0';
      return 1;
    }
    byte[] temp = new byte[20];
    int pos = 20;
    boolean negative = value < 0;
    long v = negative ? -value : value;
    while (v > 0) {
      temp[--pos] = (byte) ('0' + (v % 10));
      v /= 10;
    }
    if (negative) {
      temp[--pos] = '-';
    }
    int len = 20 - pos;
    System.arraycopy(temp, pos, target, offset, len);
    return len;
  }

  /**
   * Returns the string length of an integer when formatted as ASCII decimal.
   *
   * @param x the integer value
   * @return character length
   */
  public static int stringSize(int x) {
    int d = 1;
    if (x >= 0) {
      d = 0;
      x = -x;
    }
    int p = -10;
    for (int i = 1; i < 10; i++) {
      if (x > p) {
        return i + d;
      }
      if (p < -214748364) {
        return 10 + d;
      }
      p = 10 * p;
    }
    return 10 + d;
  }

  /**
   * Returns the string length of a long when formatted as ASCII decimal.
   *
   * @param x the long value
   * @return character length
   */
  public static int stringSize(long x) {
    int d = 1;
    if (x >= 0) {
      d = 0;
      x = -x;
    }
    long p = -10;
    for (int i = 1; i < 19; i++) {
      if (x > p) {
        return i + d;
      }
      if (p < -922337203685477580L) {
        return 19 + d;
      }
      p = 10 * p;
    }
    return 19 + d;
  }
}
