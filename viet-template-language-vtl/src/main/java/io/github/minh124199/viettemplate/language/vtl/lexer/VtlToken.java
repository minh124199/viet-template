package io.github.minh124199.viettemplate.language.vtl.lexer;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.io.Serializable;
import java.util.Objects;

/** Immutable lexical token representing a slice of {@link SourceText}. */
public record VtlToken(VtlTokenKind kind, SourceSpan span) implements Serializable {

  public VtlToken {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlToken of(VtlTokenKind kind, SourceSpan span) {
    return new VtlToken(kind, span);
  }

  public CharSequence rawText(SourceText source) {
    Objects.requireNonNull(source, "source must not be null");
    return source.slice(span.startOffset(), span.endOffset());
  }

  public String text(SourceText source) {
    Objects.requireNonNull(source, "source must not be null");
    return source.substring(span.startOffset(), span.endOffset());
  }

  public int startOffset() {
    return span.startOffset();
  }

  public int endOffset() {
    return span.endOffset();
  }

  public int length() {
    return span.length();
  }

  public boolean isTrivia() {
    return kind.isTrivia();
  }

  @Override
  public String toString() {
    return String.format("%s[%d..%d]", kind, span.startOffset(), span.endOffset());
  }
}
