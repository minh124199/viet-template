package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** AST node for an {@code #if ... #elseif ... #else ... #end} conditional structure. */
public record VtlIfDirectiveNode(
    List<VtlIfBranch> branches, Optional<List<VtlNode>> elseBody, SourceSpan span)
    implements VtlDirectiveNode {

  public VtlIfDirectiveNode {
    branches = List.copyOf(Objects.requireNonNull(branches, "branches must not be null"));
    if (branches.isEmpty()) {
      throw new IllegalArgumentException("branches must contain at least one branch (#if)");
    }
    Objects.requireNonNull(elseBody, "elseBody must not be null");
    elseBody = elseBody.map(List::copyOf);
    Objects.requireNonNull(span, "span must not be null");
  }

  public VtlIfBranch primaryBranch() {
    return branches.get(0);
  }
}
