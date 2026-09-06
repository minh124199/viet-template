package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** Represents an {@code #if(condition)} or {@code #elseif(condition)} branch and its body. */
public record VtlIfBranch(VtlExpression condition, List<VtlNode> body, SourceSpan span) {

  public VtlIfBranch {
    Objects.requireNonNull(condition, "condition must not be null");
    body = List.copyOf(Objects.requireNonNull(body, "body must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
