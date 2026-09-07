package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * High-performance, low-allocation streaming {@link TemplateOutput} implementation backed by an
 * {@link OutputStream} using UTF-8 encoding.
 *
 * <p>Emits static pre-encoded UTF-8 byte chunks with zero-copy buffer transfer, formats primitive
 * values directly into byte buffers without intermediate heap object allocations, and iterates
 * {@link CharSequence} inputs without forcing {@link String} creation.
 */
public final class Utf8OutputStreamTemplateOutput
    implements TemplateOutput, Flushable, AutoCloseable {

  private static final int DEFAULT_BUFFER_SIZE = 8192;
  private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.US_ASCII);

  private final OutputStream out;
  private final byte[] buffer;
  private int position;

  public Utf8OutputStreamTemplateOutput(OutputStream out) {
    this(out, DEFAULT_BUFFER_SIZE);
  }

  public Utf8OutputStreamTemplateOutput(OutputStream out, int bufferSize) {
    this.out = Objects.requireNonNull(out, "out must not be null");
    if (bufferSize < 64) {
      throw new IllegalArgumentException("bufferSize must be at least 64 bytes");
    }
    this.buffer = new byte[bufferSize];
    this.position = 0;
  }

  private void flushBuffer() throws IOException {
    if (position > 0) {
      out.write(buffer, 0, position);
      position = 0;
    }
  }

  private void ensureCapacity(int needed) throws IOException {
    if (position + needed > buffer.length) {
      flushBuffer();
    }
  }

  @Override
  public void write(CharSequence value) throws IOException {
    if (value == null) {
      return;
    }
    int len = value.length();
    for (int i = 0; i < len; i++) {
      char c = value.charAt(i);
      if (c <= 0x7F) {
        if (position >= buffer.length) {
          flushBuffer();
        }
        buffer[position++] = (byte) c;
      } else if (c <= 0x7FF) {
        ensureCapacity(2);
        buffer[position++] = (byte) (0xC0 | (c >> 6));
        buffer[position++] = (byte) (0x80 | (c & 0x3F));
      } else if (Character.isHighSurrogate(c)
          && i + 1 < len
          && Character.isLowSurrogate(value.charAt(i + 1))) {
        char low = value.charAt(++i);
        int codePoint = Character.toCodePoint(c, low);
        ensureCapacity(4);
        buffer[position++] = (byte) (0xF0 | (codePoint >> 18));
        buffer[position++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
        buffer[position++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
        buffer[position++] = (byte) (0x80 | (codePoint & 0x3F));
      } else {
        ensureCapacity(3);
        buffer[position++] = (byte) (0xE0 | (c >> 12));
        buffer[position++] = (byte) (0x80 | ((c >> 6) & 0x3F));
        buffer[position++] = (byte) (0x80 | (c & 0x3F));
      }
    }
  }

  @Override
  public void write(char value) throws IOException {
    if (value <= 0x7F) {
      if (position >= buffer.length) {
        flushBuffer();
      }
      buffer[position++] = (byte) value;
    } else if (value <= 0x7FF) {
      ensureCapacity(2);
      buffer[position++] = (byte) (0xC0 | (value >> 6));
      buffer[position++] = (byte) (0x80 | (value & 0x3F));
    } else {
      ensureCapacity(3);
      buffer[position++] = (byte) (0xE0 | (value >> 12));
      buffer[position++] = (byte) (0x80 | ((value >> 6) & 0x3F));
      buffer[position++] = (byte) (0x80 | (value & 0x3F));
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
    if (length >= buffer.length) {
      flushBuffer();
      out.write(bytes, offset, length);
    } else {
      if (position + length > buffer.length) {
        flushBuffer();
      }
      System.arraycopy(bytes, offset, buffer, position, length);
      position += length;
    }
  }

  @Override
  public void writeInt(int value) throws IOException {
    ensureCapacity(11);
    position += NumberFormatting.formatInt(value, buffer, position);
  }

  @Override
  public void writeLong(long value) throws IOException {
    ensureCapacity(20);
    position += NumberFormatting.formatLong(value, buffer, position);
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
  public void writeFloat(float value) throws IOException {
    if (value == (long) value
        && value >= Long.MIN_VALUE
        && value <= Long.MAX_VALUE
        && !Float.isNaN(value)
        && !Float.isInfinite(value)) {
      writeLong((long) value);
      return;
    }
    write(Float.toString(value));
  }

  @Override
  public void writeShort(short value) throws IOException {
    writeInt(value);
  }

  @Override
  public void writeByte(byte value) throws IOException {
    writeInt(value);
  }

  @Override
  public void writeBoolean(boolean value) throws IOException {
    writeUtf8(value ? TRUE_BYTES : FALSE_BYTES);
  }

  @Override
  public void flush() throws IOException {
    flushBuffer();
    out.flush();
  }

  @Override
  public void close() throws IOException {
    flush();
    out.close();
  }
}
