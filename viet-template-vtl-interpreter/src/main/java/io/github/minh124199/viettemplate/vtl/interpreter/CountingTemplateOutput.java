package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.runtime.NumberFormatting;
import io.github.minh124199.viettemplate.runtime.RenderBudget;
import java.io.IOException;
import java.util.Objects;

/** Output wrapper that counts written characters and enforces character budget limits. */
public class CountingTemplateOutput implements TemplateOutput {

  private final TemplateOutput delegate;
  private final RenderBudget budget;
  private final TemplateId templateId;

  public CountingTemplateOutput(
      TemplateOutput delegate, RenderBudget budget, TemplateId templateId) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.budget = Objects.requireNonNull(budget, "budget must not be null");
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
  }

  public CountingTemplateOutput(TemplateOutput delegate, long maxChars, TemplateId templateId) {
    this(delegate, new RenderBudget(maxChars, 0L, Integer.MAX_VALUE), templateId);
  }

  public TemplateOutput delegate() {
    return delegate;
  }

  public RenderBudget budget() {
    return budget;
  }

  public TemplateId templateId() {
    return templateId;
  }

  public long written() {
    return budget.charactersWritten();
  }

  protected void checkLimit(int added) {
    budget.consumeCharacters(added, templateId, SourceSpan.UNKNOWN);
  }

  @Override
  public void write(CharSequence value) throws IOException {
    if (value != null) {
      checkLimit(value.length());
      delegate.write(value);
    }
  }

  @Override
  public void write(char value) throws IOException {
    checkLimit(1);
    delegate.write(value);
  }

  @Override
  public void writeUtf8(byte[] bytes) throws IOException {
    if (bytes != null) {
      checkLimit(bytes.length);
      delegate.writeUtf8(bytes);
    }
  }

  @Override
  public void writeUtf8(byte[] bytes, int offset, int length) throws IOException {
    if (bytes != null) {
      checkLimit(length);
      delegate.writeUtf8(bytes, offset, length);
    }
  }

  @Override
  public void writeInt(int value) throws IOException {
    checkLimit(NumberFormatting.stringSize(value));
    delegate.writeInt(value);
  }

  @Override
  public void writeLong(long value) throws IOException {
    checkLimit(NumberFormatting.stringSize(value));
    delegate.writeLong(value);
  }

  @Override
  public void writeDouble(double value) throws IOException {
    String s = Double.toString(value);
    checkLimit(s.length());
    delegate.writeDouble(value);
  }

  @Override
  public void writeFloat(float value) throws IOException {
    String s = Float.toString(value);
    checkLimit(s.length());
    delegate.writeFloat(value);
  }

  @Override
  public void writeShort(short value) throws IOException {
    checkLimit(NumberFormatting.stringSize(value));
    delegate.writeShort(value);
  }

  @Override
  public void writeByte(byte value) throws IOException {
    checkLimit(NumberFormatting.stringSize(value));
    delegate.writeByte(value);
  }

  @Override
  public void writeBoolean(boolean value) throws IOException {
    checkLimit(value ? 4 : 5);
    delegate.writeBoolean(value);
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  long writtenCharacters() {
    return written();
  }
}
