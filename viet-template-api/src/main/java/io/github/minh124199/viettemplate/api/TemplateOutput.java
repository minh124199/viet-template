package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.util.Objects;

/**
 * Output destination for streaming template rendering.
 *
 * <p>Supports character-based, primitive, and UTF-8 pre-encoded byte writes without forcing
 * intermediate string allocation.
 */
public interface TemplateOutput {

  void write(CharSequence value) throws IOException;

  /**
   * Writes a subsequence of the specified {@link CharSequence} over the half-open interval {@code
   * [start, end)}.
   *
   * <p>If {@code value} is {@code null}, this method performs no operation and returns immediately
   * without throwing an exception.
   *
   * <p>If {@code start == end}, this method writes nothing and completes normally.
   *
   * <p>Indices are validated using {@link Objects#checkFromToIndex(int, int, int)}. If {@code value
   * != null} and {@code start < 0}, {@code end < start}, or {@code end > value.length()}, an {@link
   * IndexOutOfBoundsException} is thrown.
   *
   * <p><strong>Backward Compatibility &amp; Implementation Note:</strong> This method is defined as
   * a default interface method to preserve binary and source compatibility with custom {@link
   * TemplateOutput} implementors. The default implementation iterates through the range {@code
   * [start, end)} and delegates each character to {@link #write(char)}. Implementors are strongly
   * encouraged to override this method with specialized, zero-allocation slicing logic (such as
   * intrinsic buffer transfers or zero-copy encoding) to avoid per-character dispatch overhead.
   *
   * @param value the character sequence to write, or {@code null} for a safe no-op
   * @param start the beginning index of the subsequence, inclusive
   * @param end the ending index of the subsequence, exclusive
   * @throws IOException if an I/O error occurs during writing
   * @throws IndexOutOfBoundsException if {@code value != null} and the range {@code [start, end)}
   *     is out of bounds ({@code start < 0}, {@code end < start}, or {@code end > value.length()})
   */
  default void write(CharSequence value, int start, int end) throws IOException {
    if (value == null) {
      return;
    }
    Objects.checkFromToIndex(start, end, value.length());
    for (int i = start; i < end; i++) {
      write(value.charAt(i));
    }
  }

  void write(char value) throws IOException;

  void writeUtf8(byte[] bytes) throws IOException;

  default void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
    byte[] copy = new byte[length];
    System.arraycopy(bytes, offset, copy, 0, length);
    writeUtf8(copy);
  }

  void writeInt(int value) throws IOException;

  void writeLong(long value) throws IOException;

  void writeDouble(double value) throws IOException;

  default void writeFloat(float value) throws IOException {
    writeDouble(value);
  }

  default void writeShort(short value) throws IOException {
    writeInt(value);
  }

  default void writeByte(byte value) throws IOException {
    writeInt(value);
  }

  void writeBoolean(boolean value) throws IOException;

  default void flush() throws IOException {}
}
