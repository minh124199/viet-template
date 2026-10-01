package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.TemplateOutput;

/**
 * Lightweight, allocation-free counting output sink for microbenchmarking.
 *
 * <p>Tracks written bytes and characters via an un-synchronized counter without intermediate
 * allocations or synchronization overhead.
 */
public final class CountingTemplateOutput implements TemplateOutput {

  public long count;

  public CountingTemplateOutput() {}

  public long count() {
    return count;
  }

  public void reset() {
    count = 0L;
  }

  @Override
  public void write(CharSequence value) {
    if (value != null) {
      count += value.length();
    }
  }

  @Override
  public void write(CharSequence value, int start, int end) {
    if (value != null && end > start) {
      count += (end - start);
    }
  }

  @Override
  public void write(char value) {
    count++;
  }

  @Override
  public void writeUtf8(byte[] bytes) {
    if (bytes != null) {
      count += bytes.length;
    }
  }

  @Override
  public void writeUtf8(byte[] bytes, int offset, int length) {
    count += length;
  }

  @Override
  public void writeInt(int value) {
    count += 4;
  }

  @Override
  public void writeLong(long value) {
    count += 8;
  }

  @Override
  public void writeDouble(double value) {
    count += 8;
  }

  @Override
  public void writeFloat(float value) {
    count += 4;
  }

  @Override
  public void writeShort(short value) {
    count += 2;
  }

  @Override
  public void writeByte(byte value) {
    count += 1;
  }

  @Override
  public void writeBoolean(boolean value) {
    count += 4;
  }

  @Override
  public void flush() {}
}
