package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Streaming {@link TemplateOutput} implementation backed by a {@link Writer}. */
public final class WriterTemplateOutput implements TemplateOutput, Flushable {

  private static final int SCRATCH_BUFFER_SIZE = 1024;

  private final Writer writer;
  private char[] scratchBuffer;

  public WriterTemplateOutput(Writer writer) {
    this.writer = Objects.requireNonNull(writer, "writer must not be null");
  }

  @Override
  public void write(CharSequence value) throws IOException {
    if (value != null) {
      write(value, 0, value.length());
    }
  }

  @Override
  public void write(CharSequence value, int start, int end) throws IOException {
    if (value != null) {
      Objects.checkFromToIndex(start, end, value.length());
      if (value instanceof String s) {
        writer.write(s, start, end - start);
      } else {
        int srcIdx = start;
        if (srcIdx < end) {
          if (scratchBuffer == null) {
            scratchBuffer = new char[SCRATCH_BUFFER_SIZE];
          }
          while (srcIdx < end) {
            int chunk = Math.min(scratchBuffer.length, end - srcIdx);
            for (int i = 0; i < chunk; i++) {
              scratchBuffer[i] = value.charAt(srcIdx + i);
            }
            writer.write(scratchBuffer, 0, chunk);
            srcIdx += chunk;
          }
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
