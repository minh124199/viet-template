package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Streaming {@link TemplateOutput} implementation backed by a {@link Writer}. */
public final class WriterTemplateOutput implements TemplateOutput, Flushable {

  private final Writer writer;

  public WriterTemplateOutput(Writer writer) {
    this.writer = Objects.requireNonNull(writer, "writer must not be null");
  }

  @Override
  public void write(CharSequence value) throws IOException {
    if (value != null) {
      if (value instanceof String s) {
        writer.write(s);
      } else {
        int len = value.length();
        char[] buf = new char[Math.min(len, 1024)];
        int srcIdx = 0;
        while (srcIdx < len) {
          int chunk = Math.min(buf.length, len - srcIdx);
          for (int i = 0; i < chunk; i++) {
            buf[i] = value.charAt(srcIdx + i);
          }
          writer.write(buf, 0, chunk);
          srcIdx += chunk;
        }
      }
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
    NumberFormatting.write(writer, value);
  }

  @Override
  public void writeLong(long value) throws IOException {
    NumberFormatting.write(writer, value);
  }

  @Override
  public void writeDouble(double value) throws IOException {
    NumberFormatting.write(writer, value);
  }

  @Override
  public void writeFloat(float value) throws IOException {
    NumberFormatting.write(writer, value);
  }

  @Override
  public void writeShort(short value) throws IOException {
    NumberFormatting.write(writer, value);
  }

  @Override
  public void writeByte(byte value) throws IOException {
    NumberFormatting.write(writer, value);
  }

  @Override
  public void writeBoolean(boolean value) throws IOException {
    NumberFormatting.write(writer, value);
  }

  @Override
  public void flush() throws IOException {
    writer.flush();
  }
}
