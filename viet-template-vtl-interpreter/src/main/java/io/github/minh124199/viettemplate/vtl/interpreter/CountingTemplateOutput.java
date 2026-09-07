package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;
import java.util.Objects;

/** Output wrapper that counts written characters and enforces character budget limits. */
final class CountingTemplateOutput implements TemplateOutput {

  private final TemplateOutput delegate;
  private final long maxChars;
  private final TemplateId templateId;
  private long written = 0;

  CountingTemplateOutput(TemplateOutput delegate, long maxChars, TemplateId templateId) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.maxChars = maxChars;
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
  }

  private void checkLimit(int added) {
    written += added;
    if (written > maxChars) {
      throw new TemplateLimitException(
          "Exceeded maximum rendered output characters limit: " + maxChars,
          templateId,
          SourceSpan.UNKNOWN,
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }
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
  public void writeInt(int value) throws IOException {
    String s = Integer.toString(value);
    checkLimit(s.length());
    delegate.writeInt(value);
  }

  @Override
  public void writeLong(long value) throws IOException {
    String s = Long.toString(value);
    checkLimit(s.length());
    delegate.writeLong(value);
  }

  @Override
  public void writeDouble(double value) throws IOException {
    String s = Double.toString(value);
    checkLimit(s.length());
    delegate.writeDouble(value);
  }

  @Override
  public void writeBoolean(boolean value) throws IOException {
    checkLimit(value ? 4 : 5);
    delegate.writeBoolean(value);
  }

  long writtenCharacters() {
    return written;
  }
}
