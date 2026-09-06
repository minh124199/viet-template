package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Streaming {@link TemplateOutput} implementation backed by a {@link Writer}. */
public final class WriterTemplateOutput implements TemplateOutput {

  private final Writer writer;

  public WriterTemplateOutput(Writer writer) {
    this.writer = Objects.requireNonNull(writer, "writer must not be null");
  }

  @Override
  public void write(CharSequence value) throws IOException {
    if (value != null) {
      writer.append(value);
    }
  }

  @Override
  public void write(char value) throws IOException {
    writer.write(value);
  }

  @Override
  public void writeUtf8(byte[] bytes) throws IOException {
    Objects.requireNonNull(bytes, "bytes must not be null");
    writer.write(new String(bytes, StandardCharsets.UTF_8));
  }

  @Override
  public void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
    Objects.requireNonNull(bytes, "bytes must not be null");
    writer.write(new String(bytes, offset, length, StandardCharsets.UTF_8));
  }

  @Override
  public void writeInt(int value) throws IOException {
    writer.write(Integer.toString(value));
  }

  @Override
  public void writeLong(long value) throws IOException {
    writer.write(Long.toString(value));
  }

  @Override
  public void writeDouble(double value) throws IOException {
    writer.write(Double.toString(value));
  }

  @Override
  public void writeBoolean(boolean value) throws IOException {
    writer.write(Boolean.toString(value));
  }
}
