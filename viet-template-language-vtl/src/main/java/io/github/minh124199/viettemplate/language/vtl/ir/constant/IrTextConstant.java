package io.github.minh124199.viettemplate.language.vtl.ir.constant;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * A static text chunk stored in the template constant pool.
 *
 * <p>May include pre-encoded UTF-8 bytes for zero-copy streaming output.
 */
public record IrTextConstant(int id, String text, Optional<byte[]> utf8Bytes, SourceSpan span) {

  public IrTextConstant {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(utf8Bytes, "utf8Bytes must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrTextConstant of(int id, String text, SourceSpan span) {
    return new IrTextConstant(id, text, Optional.of(text.getBytes(StandardCharsets.UTF_8)), span);
  }

  public static IrTextConstant withoutUtf8(int id, String text, SourceSpan span) {
    return new IrTextConstant(id, text, Optional.empty(), span);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IrTextConstant that = (IrTextConstant) o;
    return id == that.id && text.equals(that.text) && span.equals(that.span);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, text, span);
  }
}
