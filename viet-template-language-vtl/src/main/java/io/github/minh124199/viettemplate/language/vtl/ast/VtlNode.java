package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;

/**
 * Root interface for all Abstract Syntax Tree (AST) nodes in the VTL frontend.
 *
 * <p>All nodes are immutable and expose an exact {@link SourceSpan}.
 */
public sealed interface VtlNode
    permits VtlTemplate,
        VtlTextNode,
        VtlRawTextNode,
        VtlReferenceOutputNode,
        VtlDirectiveNode,
        VtlErrorNode {

  /** Returns the source span corresponding to this syntactic node. */
  SourceSpan span();
}
