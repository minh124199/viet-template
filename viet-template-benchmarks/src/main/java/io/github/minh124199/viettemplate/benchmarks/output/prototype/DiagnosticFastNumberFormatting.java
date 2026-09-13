package io.github.minh124199.viettemplate.benchmarks.output.prototype;

import java.nio.charset.StandardCharsets;

/**
 * Benchmark-only diagnostic prototype evaluating zero-allocation direct primitive formatting.
 *
 * <p>Unlike the production {@code NumberFormatting.formatInt/formatLong} which allocates a
 * temporary {@code byte[11]} or {@code byte[20]} on every call, this prototype writes digits
 * directly into the destination buffer without any heap allocations.
 */
public final class DiagnosticFastNumberFormatting {

  private static final byte[] INT_MIN_BYTES = "-2147483648".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] LONG_MIN_BYTES =
      "-9223372036854775808".getBytes(StandardCharsets.US_ASCII);

  private DiagnosticFastNumberFormatting() {}

  public static int formatInt(int value, byte[] target, int offset) {
    if (value == Integer.MIN_VALUE) {
      System.arraycopy(INT_MIN_BYTES, 0, target, offset, INT_MIN_BYTES.length);
      return INT_MIN_BYTES.length;
    }
    if (value == 0) {
      target[offset] = '0';
      return 1;
    }
    boolean negative = value < 0;
    int v = negative ? -value : value;

    // Fast digit count
    int size = stringSize(v);
    if (negative) {
      size++;
      target[offset] = '-';
    }

    int pos = offset + size;
    while (v > 0) {
      target[--pos] = (byte) ('0' + (v % 10));
      v /= 10;
    }
    return size;
  }

  public static int formatLong(long value, byte[] target, int offset) {
    if (value == Long.MIN_VALUE) {
      System.arraycopy(LONG_MIN_BYTES, 0, target, offset, LONG_MIN_BYTES.length);
      return LONG_MIN_BYTES.length;
    }
    if (value == 0) {
      target[offset] = '0';
      return 1;
    }
    boolean negative = value < 0;
    long v = negative ? -value : value;

    int size = stringSize(v);
    if (negative) {
      size++;
      target[offset] = '-';
    }

    int pos = offset + size;
    while (v > 0) {
      target[--pos] = (byte) ('0' + (v % 10));
      v /= 10;
    }
    return size;
  }

  private static int stringSize(int x) {
    int p = 10;
    for (int i = 1; i < 10; i++) {
      if (x < p) {
        return i;
      }
      p = 10 * p;
    }
    return 10;
  }

  private static int stringSize(long x) {
    long p = 10;
    for (int i = 1; i < 19; i++) {
      if (x < p) {
        return i;
      }
      p = 10 * p;
    }
    return 19;
  }
}
