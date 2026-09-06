package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/**
 * Double-quoted string literal expression with support for interpolated references, e.g. {@code
 * "Hello $user.name!"}.
 */
public record VtlInterpolatedStringExpression(
    List<VtlInterpolatedStringPart> parts, SourceSpan span) implements VtlExpression {

  public VtlInterpolatedStringExpression {
    parts = List.copyOf(Objects.requireNonNull(parts, "parts must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }

  /** Common sealed interface for constituent parts of an interpolated string. */
  public sealed interface VtlInterpolatedStringPart
      permits VtlInterpolatedStringPart.TextPart, VtlInterpolatedStringPart.ReferencePart {

    SourceSpan span();

    /** Literal static text chunk within the interpolated string. */
    record TextPart(String text, SourceSpan span) implements VtlInterpolatedStringPart {
      public TextPart {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(span, "span must not be null");
      }
    }

    /** Interpolated reference within the string, e.g. {@code $user.name} or {@code ${foo}}. */
    record ReferencePart(VtlReference reference, SourceSpan span)
        implements VtlInterpolatedStringPart {
      public ReferencePart {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(span, "span must not be null");
      }
    }
  }
}
