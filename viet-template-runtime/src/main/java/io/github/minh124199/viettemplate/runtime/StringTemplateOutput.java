package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** In-memory string-backed implementation of {@link TemplateOutput}. */
public final class StringTemplateOutput implements TemplateOutput {

  private final StringBuilder builder;

  public StringTemplateOutput() {
    this(128);
  }

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

  @Override
  public void flush() {
    // In-memory buffer no-op
  }

  public int length() {
    return builder.length();
  }

  public void reset() {
    builder.setLength(0);
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
