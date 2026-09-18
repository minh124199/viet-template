package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;

/**
 * Base sealed interface for all effectful statements and control flow operations in Template IR.
 *
 * <p>Every statement retains source mapping via {@link #span()} for precise diagnostic and error
 * reporting.
 */
public sealed interface IrStatement
    permits IrBranch,
        IrBranchIf,
        IrBreak,
        IrBudgetCheck,
        IrCallMacro,
        IrCallTemplate,
        IrEvaluate,
        IrIf,
        IrLoop,
        IrLoopEnd,
        IrLoopNext,
        IrLoopSetup,
        IrNoOp,
        IrReturn,
        IrSetIndex,
        IrSetProperty,
        IrStop,
        IrStoreLocal,
        IrWriteConst,
        IrWriteValue {

  /** Source span corresponding to this statement in the original template source. */
  SourceSpan span();
}
