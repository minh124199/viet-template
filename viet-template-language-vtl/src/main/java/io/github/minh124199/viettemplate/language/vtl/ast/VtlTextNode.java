package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Objects;

/** Leaf AST node representing a contiguous chunk of static template text. */
public record VtlTextNode(SourceSpan span) implements VtlNode {

  public VtlTextNode {
    Objects.requireNonNull(span, "span must not be null");
  }

  public String text(SourceText source) {
    Objects.requireNonNull(source, "source must not be null");
    return source.slice(span.startOffset(), span.endOffset()).toString();
  }
}
