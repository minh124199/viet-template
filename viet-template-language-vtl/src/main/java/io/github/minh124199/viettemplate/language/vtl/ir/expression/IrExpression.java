package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;

/**
 * Base interface for all value-producing expressions in Template IR.
 *
 * <p>Separates value computation from output side effects. Retains both static {@link #type()} and
 * source mapping via {@link #span()}.
 */
public interface IrExpression {

  /** Static type of this expression computed during semantic analysis. */
  VType type();

  /** Source span corresponding to this expression in the original template source. */
  SourceSpan span();
}
