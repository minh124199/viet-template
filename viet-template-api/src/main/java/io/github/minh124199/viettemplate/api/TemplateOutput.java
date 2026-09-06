package io.github.minh124199.viettemplate.api;

import java.io.IOException;

/**
 * Output destination for streaming template rendering.
 *
 * <p>Supports character-based, primitive, and UTF-8 pre-encoded byte writes without forcing
 * intermediate string allocation.
 */
public interface TemplateOutput {

  void write(CharSequence value) throws IOException;

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

  void writeBoolean(boolean value) throws IOException;
}
