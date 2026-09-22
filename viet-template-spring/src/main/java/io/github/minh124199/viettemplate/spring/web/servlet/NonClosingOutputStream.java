package io.github.minh124199.viettemplate.spring.web.servlet;

import java.io.OutputStream;

/**
 * Delegating stream that executes flush on close while preventing closure of the underlying servlet
 * response output stream.
 *
 * <p>Retained for binary and backward compatibility; delegates directly to {@link
 * io.github.minh124199.viettemplate.runtime.stream.NonClosingOutputStream}.
 */
final class NonClosingOutputStream
    extends io.github.minh124199.viettemplate.runtime.stream.NonClosingOutputStream {

  NonClosingOutputStream(OutputStream out) {
    super(out);
  }
}
