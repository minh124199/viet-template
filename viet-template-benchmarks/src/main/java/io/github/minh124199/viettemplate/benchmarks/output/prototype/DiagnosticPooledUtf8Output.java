package io.github.minh124199.viettemplate.benchmarks.output.prototype;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Benchmark-only diagnostic prototype evaluating a bounded buffer pool and direct primitive
 * formatting for streaming UTF-8 output.
 */
public final class DiagnosticPooledUtf8Output implements TemplateOutput, Flushable, AutoCloseable {

  public static final int BUFFER_SIZE = 8192;
  private static final int MAX_POOL_SIZE = 64; // Max 512 KB retained globally
  private static final ConcurrentLinkedQueue<byte[]> BUFFER_POOL = new ConcurrentLinkedQueue<>();

  private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.US_ASCII);

  private final OutputStream out;
  private final byte[] buffer;
  private int position;
  private boolean closed;

  public static byte[] acquireBuffer() {
    byte[] buf = BUFFER_POOL.poll();
    return buf != null ? buf : new byte[BUFFER_SIZE];
  }

  public static void releaseBuffer(byte[] buf) {
    if (buf != null && buf.length == BUFFER_SIZE && BUFFER_POOL.size() < MAX_POOL_SIZE) {
      BUFFER_POOL.offer(buf);
    }
  }

  public DiagnosticPooledUtf8Output(OutputStream out) {
    this(out, acquireBuffer());
  }

  private DiagnosticPooledUtf8Output(OutputStream out, byte[] buffer) {
    this.out = Objects.requireNonNull(out, "out must not be null");
    this.buffer = buffer;
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
    position += DiagnosticFastNumberFormatting.formatInt(value, buffer, position);
  }

  @Override
  public void writeLong(long value) throws IOException {
    ensureCapacity(20);
    position += DiagnosticFastNumberFormatting.formatLong(value, buffer, position);
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

  @Override
  public void flush() throws IOException {
    flushBuffer();
    out.flush();
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      flush();
      releaseBuffer(buffer);
      closed = true;
    }
  }
}
