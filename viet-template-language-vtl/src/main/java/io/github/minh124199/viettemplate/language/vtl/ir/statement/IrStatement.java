package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;

/**
 * Base interface for all effectful statements and control flow operations in Template IR.
 *
 * <p>Every statement retains source mapping via {@link #span()} for precise diagnostic and error
 * reporting.
 */
public interface IrStatement {

  /** Source span corresponding to this statement in the original template source. */
  SourceSpan span();
}
