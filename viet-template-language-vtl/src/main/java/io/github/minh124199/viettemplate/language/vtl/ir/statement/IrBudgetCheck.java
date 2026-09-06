package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BudgetKind;
import java.util.Objects;

/** Statement verifying that runtime execution does not exceed configured safety budgets. */
public record IrBudgetCheck(BudgetKind kind, SourceSpan span) implements IrStatement {

  public IrBudgetCheck {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
