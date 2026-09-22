package io.github.minh124199.viettemplate.runtime.stream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Delegating {@link FilterOutputStream} that executes {@link OutputStream#flush()} on {@link
 * #close()} while preventing closure of the underlying stream.
 *
 * <p>Used to wrap response output streams (e.g. servlet responses) where lifecycle and closure are
 * managed by the outer container or pipeline rather than the template rendering engine.
 */
public class NonClosingOutputStream extends FilterOutputStream {

  public NonClosingOutputStream(OutputStream out) {
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
