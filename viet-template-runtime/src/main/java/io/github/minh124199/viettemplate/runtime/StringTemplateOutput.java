package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * In-memory string-backed implementation of {@link TemplateOutput}.
 *
 * <h2>Lifecycle and Resource Semantics</h2>
 *
 * <ul>
 *   <li><strong>In-Memory Buffering:</strong> Backed directly by an internal {@link StringBuilder};
 *       does not allocate external streams, file handles, or system I/O resources.
 *   <li><strong>Thread Confinement:</strong> Instances are single-threaded, render-scoped, and
 *       <strong>not</strong> thread-safe. Concurrent writes from multiple threads are prohibited.
 *   <li><strong>Reset and Reuse:</strong> Calling {@link #reset()} resets the internal buffer
 *       length to zero without reallocating underlying capacity, enabling efficient reuse across
 *       sequential renders within the same thread.
 *   <li><strong>Flush Semantics:</strong> {@link #flush()} is an intentional safe no-op because all
 *       write operations append synchronously directly into the memory buffer.
 * </ul>
 */
public final class StringTemplateOutput implements TemplateOutput {

  private final StringBuilder builder;

  /** Constructs an instance with the default initial capacity of 128 characters. */
  public StringTemplateOutput() {
    this(128);
  }

  /**
   * Constructs an instance with the specified initial capacity.
   *
   * @param initialCapacity initial character capacity of the underlying builder
   */
  public StringTemplateOutput(int initialCapacity) {
    this.builder = new StringBuilder(initialCapacity);
  }

  @Override
  public void write(CharSequence value) {
    if (value != null) {
      builder.append(value);
    }
  }

  @Override
  public void write(CharSequence value, int start, int end) {
    if (value != null) {
      Objects.checkFromToIndex(start, end, value.length());
      builder.append(value, start, end);
    }
  }

  @Override
  public void write(char value) {
    builder.append(value);
  }

  @Override
  public void writeUtf8(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    builder.append(new String(bytes, StandardCharsets.UTF_8));
  }

  @Override
  public void writeUtf8(byte[] bytes, int offset, int length) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    builder.append(new String(bytes, offset, length, StandardCharsets.UTF_8));
  }

  @Override
  public void writeInt(int value) {
    builder.append(value);
  }

  @Override
  public void writeLong(long value) {
    builder.append(value);
  }

  @Override
  public void writeDouble(double value) {
    builder.append(value);
  }

  @Override
  public void writeFloat(float value) {
    builder.append(value);
  }

  @Override
  public void writeShort(short value) {
    builder.append(value);
  }

  @Override
  public void writeByte(byte value) {
    builder.append(value);
  }

  @Override
  public void writeBoolean(boolean value) {
    builder.append(value);
  }

  /** Safe no-op implementation because writes are applied synchronously to the memory buffer. */
  @Override
  public void flush() {
    // In-memory buffer no-op
  }

  /**
   * Returns the current number of characters accumulated in this output buffer.
   *
   * @return character count
   */
  public int length() {
    return builder.length();
  }

  /**
   * Clears the accumulated content by setting the buffer length to zero.
   *
   * <p>Retains existing capacity for reuse within the same render thread.
   */
  public void reset() {
    builder.setLength(0);
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
