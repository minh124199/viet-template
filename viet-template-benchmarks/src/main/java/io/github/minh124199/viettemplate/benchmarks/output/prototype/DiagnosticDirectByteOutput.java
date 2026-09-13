package io.github.minh124199.viettemplate.benchmarks.output.prototype;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Benchmark-only diagnostic prototype evaluating direct in-memory byte buffer accumulation.
 *
 * <p>Avoids the double-buffering and synchronized write overhead of pairing {@link
 * java.io.ByteArrayOutputStream} with {@link
 * io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput}.
 */
public final class DiagnosticDirectByteOutput implements TemplateOutput {

  private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.US_ASCII);

  private byte[] buf;
  private int count;

  public DiagnosticDirectByteOutput() {
    this(256);
  }

  public DiagnosticDirectByteOutput(int initialCapacity) {
    this.buf = new byte[Math.max(64, initialCapacity)];
    this.count = 0;
  }

  private void ensureCapacity(int minCapacity) {
    if (minCapacity > buf.length) {
      int newCapacity = Math.max(buf.length << 1, minCapacity);
      buf = Arrays.copyOf(buf, newCapacity);
    }
  }

  @Override
  public void write(CharSequence value) throws IOException {
    if (value == null) {
      return;
    }
    int len = value.length();
    ensureCapacity(count + len * 3); // Max UTF-8 expansion for BMP
    for (int i = 0; i < len; i++) {
      char c = value.charAt(i);
      if (c <= 0x7F) {
        buf[count++] = (byte) c;
      } else if (c <= 0x7FF) {
        buf[count++] = (byte) (0xC0 | (c >> 6));
        buf[count++] = (byte) (0x80 | (c & 0x3F));
      } else if (Character.isHighSurrogate(c)
          && i + 1 < len
          && Character.isLowSurrogate(value.charAt(i + 1))) {
        char low = value.charAt(++i);
        int codePoint = Character.toCodePoint(c, low);
        ensureCapacity(count + 4);
        buf[count++] = (byte) (0xF0 | (codePoint >> 18));
        buf[count++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
        buf[count++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
        buf[count++] = (byte) (0x80 | (codePoint & 0x3F));
      } else {
        buf[count++] = (byte) (0xE0 | (c >> 12));
        buf[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
        buf[count++] = (byte) (0x80 | (c & 0x3F));
      }
    }
  }

  @Override
  public void write(char value) throws IOException {
    if (value <= 0x7F) {
      ensureCapacity(count + 1);
      buf[count++] = (byte) value;
    } else if (value <= 0x7FF) {
      ensureCapacity(count + 2);
      buf[count++] = (byte) (0xC0 | (value >> 6));
      buf[count++] = (byte) (0x80 | (value & 0x3F));
    } else {
      ensureCapacity(count + 3);
      buf[count++] = (byte) (0xE0 | (value >> 12));
      buf[count++] = (byte) (0x80 | ((value >> 6) & 0x3F));
      buf[count++] = (byte) (0x80 | (value & 0x3F));
    }
  }

  @Override
  public void writeUtf8(byte[] bytes) throws IOException {
    if (bytes != null) {
      writeUtf8(bytes, 0, bytes.length);
    }
  }

  @Override
  public void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
    if (bytes == null || length <= 0) {
      return;
    }
    Objects.checkFromIndexSize(offset, length, bytes.length);
    ensureCapacity(count + length);
    System.arraycopy(bytes, offset, buf, count, length);
    count += length;
  }

  @Override
  public void writeInt(int value) throws IOException {
    ensureCapacity(count + 11);
    count += DiagnosticFastNumberFormatting.formatInt(value, buf, count);
  }

  @Override
  public void writeLong(long value) throws IOException {
    ensureCapacity(count + 20);
    count += DiagnosticFastNumberFormatting.formatLong(value, buf, count);
  }

  @Override
  public void writeDouble(double value) throws IOException {
    if (value == (long) value
        && value >= Long.MIN_VALUE
        && value <= Long.MAX_VALUE
        && !Double.isNaN(value)
        && !Double.isInfinite(value)) {
      writeLong((long) value);
      return;
    }
    write(Double.toString(value));
  }

  @Override
  public void writeBoolean(boolean value) throws IOException {
    writeUtf8(value ? TRUE_BYTES : FALSE_BYTES);
  }

  public int size() {
    return count;
  }

  public void reset() {
    count = 0;
  }

  public byte[] toByteArray() {
    return Arrays.copyOf(buf, count);
  }

  public String toUtf8String() {
    return new String(buf, 0, count, StandardCharsets.UTF_8);
  }
}
