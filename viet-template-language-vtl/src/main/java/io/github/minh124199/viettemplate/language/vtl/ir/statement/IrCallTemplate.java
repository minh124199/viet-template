package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.Objects;
import java.util.Optional;

/**
 * Statement representing an include or parse directive ({@code #include} or {@code #parse}).
 *
 * <p>Implements the {@code include/static-call} IR operation. When {@code staticTemplateName} is
 * present, the template target was resolved at compile time for direct linking or inlining.
 */
public record IrCallTemplate(
    IrExpression templateNameExpr,
    Optional<String> staticTemplateName,
    boolean isParse,
    SourceSpan span)
    implements IrStatement {

  public IrCallTemplate {
    Objects.requireNonNull(templateNameExpr, "templateNameExpr must not be null");
    Objects.requireNonNull(staticTemplateName, "staticTemplateName must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrCallTemplate staticCall(String templateName, boolean isParse, SourceSpan span) {
    return new IrCallTemplate(
        new io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst(
            templateName,
            io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes.STRING,
            span),
        Optional.of(templateName),
        isParse,
        span);
  }

  public static IrCallTemplate dynamicCall(
      IrExpression templateNameExpr, boolean isParse, SourceSpan span) {
    return new IrCallTemplate(templateNameExpr, Optional.empty(), isParse, span);
  }
}
