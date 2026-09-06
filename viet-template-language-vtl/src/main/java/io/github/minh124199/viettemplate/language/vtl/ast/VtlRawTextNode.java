package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Objects;

/** Leaf AST node representing an unparsed raw content block (#[[ ... ]]#). */
public record VtlRawTextNode(SourceSpan span) implements VtlNode {

  public VtlRawTextNode {
    Objects.requireNonNull(span, "span must not be null");
  }

  public String text(SourceText source) {
    Objects.requireNonNull(source, "source must not be null");
    return source.slice(span.startOffset(), span.endOffset()).toString();
  }

  /** Returns the inner content of the raw block without the surrounding '#[[' and ']]#'. */
  public String innerContent(SourceText source) {
    Objects.requireNonNull(source, "source must not be null");
    int start = span.startOffset() + 3;
    int end = Math.max(start, span.endOffset() - 3);
    return source.slice(start, end).toString();
  }

  /** Returns the SourceSpan of the inner content excluding the '#[[' and ']]#' delimiters. */
  public SourceSpan contentSpan(SourceText source) {
    Objects.requireNonNull(source, "source must not be null");
    int start = span.startOffset() + 3;
    int end = Math.max(start, span.endOffset() - 3);
    return source.spanAt(start, end);
  }
}
