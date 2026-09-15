package io.github.minh124199.viettemplate.spring.web.servlet;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Delegating {@link FilterOutputStream} that executes {@link OutputStream#flush()} on {@link
 * #close()} while preventing closure of the underlying servlet response output stream.
 */
final class NonClosingOutputStream extends FilterOutputStream {

  NonClosingOutputStream(OutputStream out) {
    super(Objects.requireNonNull(out, "out must not be null"));
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
  }

  @Override
  public void write(byte[] b) throws IOException {
    out.write(b, 0, b.length);
  }

  @Override
  public void flush() throws IOException {
    out.flush();
  }

  @Override
  public void close() throws IOException {
    out.flush();
  }
}
