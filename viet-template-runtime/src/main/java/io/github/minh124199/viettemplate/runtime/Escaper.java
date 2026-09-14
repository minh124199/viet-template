package io.github.minh124199.viettemplate.runtime;

import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

/**
 * Service Provider Interface (SPI) for escaping dynamic content into the output stream according to
 * an {@link EscapeMode}.
 *
 * <p><strong>Threading Contract:</strong> Implementations must be stateless and strictly
 * thread-safe. A single {@link Escaper} instance is shared across concurrent render threads and
 * multiple template executions.
 *
 * <p><strong>Input Handling:</strong> If the {@code input} argument is {@code null}, the escaper
 * must treat it as a safe no-op and perform no writes to {@code output}.
 *
 * <p><strong>Output Stream Propagation:</strong> The escaper streams escaped content directly to
 * the provided {@link TemplateOutput} without allocating intermediate strings whenever possible.
 *
 * <p><strong>Exception Semantics:</strong> Any {@link IOException} thrown by the destination {@link
 * TemplateOutput} must be propagated directly to the caller without being swallowed or wrapped in
 * an unchecked exception. Implementations must not throw runtime exceptions for valid or expected
 * dynamic inputs.
 */
public interface Escaper {

  EscapeMode mode();

  void escape(CharSequence input, TemplateOutput output) throws IOException;
}
