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
 *
 * <h2>Lifecycle and Resource Ownership</h2>
 *
 * <ul>
 *   <li><strong>Thread Confinement:</strong> Instances are single-threaded, render-scoped, and
 *       <strong>not</strong> thread-safe. Concurrent writes from multiple threads are prohibited.
 *       Each template render operation must use its own dedicated {@code
 *       Utf8OutputStreamTemplateOutput}.
 *   <li><strong>Underlying Stream Ownership on {@link #close()}:</strong> When {@link #close()} is
 *       invoked, this output flushes any remaining buffered content to the underlying stream,
 *       returns the pooled buffer to {@code Utf8BufferPool} (if using a pooled buffer), and
 *       <strong>closes the wrapped {@link OutputStream}</strong>.
 *   <li><strong>Flush Semantics:</strong> {@link #flush()} flushes buffered bytes to the underlying
 *       stream and calls {@link OutputStream#flush()} without closing the stream or releasing the
 *       buffer. Subsequent writes after {@code flush()} continue normally.
 *   <li><strong>Idempotent Close:</strong> Invoking {@link #close()} multiple times is safe and
 *       idempotent; subsequent close calls are immediate no-ops.
 *   <li><strong>Post-Close Failures:</strong> Any attempt to write or flush after {@link #close()}
 *       unconditionally throws {@link IOException} with message {@code "Output is closed"}.
 *   <li><strong>Managed / Servlet Response Streams:</strong> When rendering to container-managed
 *       streams (such as {@code HttpServletResponse.getOutputStream()}) where the container manages
 *       the stream lifecycle and closing the stream prematurely would break filter chains or
 *       response trailers, callers must wrap the target stream in a non-closing {@link
 *       OutputStream} delegate before passing it to this constructor.
 * </ul>
 */
public final class Utf8OutputStreamTemplateOutput
    implements TemplateOutput, Flushable, AutoCloseable {

  private static final int DEFAULT_BUFFER_SIZE = 8192;
  private static final byte[] TRUE_BYTES = "true".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] FALSE_BYTES = "false".getBytes(StandardCharsets.US_ASCII);

  private final OutputStream out;
  private final boolean isPooled;
  private byte[] buffer;
  private int position;
  private boolean closed;

  /**
   * Constructs an instance backed by the specified {@link OutputStream} using the default 8 KiB
   * buffer acquired from {@code Utf8BufferPool}.
   *
   * @param out target output stream to write UTF-8 bytes to; owned and closed on {@link #close()}
   */
  public Utf8OutputStreamTemplateOutput(OutputStream out) {
    this(out, DEFAULT_BUFFER_SIZE);
  }

  /**
   * Constructs an instance backed by the specified {@link OutputStream} with a custom buffer size.
   * If {@code bufferSize} equals 8192, a pooled buffer is acquired from {@code Utf8BufferPool};
   * otherwise an unpooled byte array is allocated.
   *
   * @param out target output stream to write UTF-8 bytes to; owned and closed on {@link #close()}
   * @param bufferSize buffer size in bytes (minimum 64)
   */
  public Utf8OutputStreamTemplateOutput(OutputStream out, int bufferSize) {
    this.out = Objects.requireNonNull(out, "out must not be null");
    if (bufferSize < 64) {
      throw new IllegalArgumentException("bufferSize must be at least 64 bytes");
    }
    if (bufferSize == DEFAULT_BUFFER_SIZE) {
      byte[] b = Utf8BufferPool.tryAcquire();
      this.buffer = b != null ? b : new byte[DEFAULT_BUFFER_SIZE];
      this.isPooled = true;
    } else {
      this.buffer = new byte[bufferSize];
      this.isPooled = false;
    }
    this.position = 0;
  }

  private void ensureOpen() throws IOException {
    if (closed || buffer == null) {
      throw new IOException("Output is closed");
    }
  }

  private void flushBuffer() throws IOException {
    ensureOpen();
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
    ensureOpen();
    if (value != null) {
      write(value, 0, value.length());
    }
  }

  @Override
  public void write(CharSequence value, int start, int end) throws IOException {
    ensureOpen();
    if (value == null) {
      return;
    }
    Objects.checkFromToIndex(start, end, value.length());
    for (int i = start; i < end; i++) {
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
          && i + 1 < end
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
    ensureOpen();
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
    ensureOpen();
    if (bytes != null) {
      writeUtf8(bytes, 0, bytes.length);
    }
  }

  @Override
  public void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
    ensureOpen();
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
    ensureOpen();
    ensureCapacity(11);
    position += NumberFormatting.formatInt(value, buffer, position);
  }

  @Override
  public void writeLong(long value) throws IOException {
    ensureOpen();
    ensureCapacity(20);
    position += NumberFormatting.formatLong(value, buffer, position);
  }

  @Override
  public void writeDouble(double value) throws IOException {
    ensureOpen();
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
    ensureOpen();
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

  /**
   * Flushes any pending buffered bytes to the underlying stream and calls {@link
   * OutputStream#flush()} on the wrapped output stream.
   *
   * <p>This method does <em>not</em> close the stream or release the buffer; subsequent write
   * operations may continue normally.
   *
   * @throws IOException if this output is closed or an I/O error occurs
   */
  @Override
  public void flush() throws IOException {
    ensureOpen();
    flushBuffer();
    out.flush();
  }

  /**
   * Closes this template output, releasing internal buffers and closing the underlying stream.
   *
   * <p>The close operation executes the following sequence:
   *
   * <ol>
   *   <li>Flushes any unwritten buffered bytes to the wrapped {@link OutputStream}.
   *   <li>Marks this instance as closed (preventing future writes or flushes).
   *   <li>Releases the internal byte buffer back to {@code Utf8BufferPool} if acquired from the
   *       pool.
   *   <li>Closes the wrapped {@link OutputStream}.
   * </ol>
   *
   * <p>This method is idempotent. If this output is already closed, subsequent invocations have no
   * effect.
   *
   * @throws IOException if an I/O error occurs while flushing or closing the underlying stream
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    try {
      flush();
    } finally {
      closed = true;
      byte[] b = this.buffer;
      this.buffer = null;
      try {
        if (isPooled && b != null) {
          Utf8BufferPool.release(b);
        }
      } finally {
        out.close();
      }
    }
  }
}
