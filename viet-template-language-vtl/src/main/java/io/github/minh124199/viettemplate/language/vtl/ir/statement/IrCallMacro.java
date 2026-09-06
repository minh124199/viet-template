package io.github.minh124199.viettemplate.language.vtl.ir.statement;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Statement invoking a macro or function.
 *
 * <p>Implements the {@code macro call} IR operation, supporting both standard macros and block
 * macros with body content.
 */
public record IrCallMacro(
    String macroName, List<IrExpression> arguments, Optional<IrBlock> bodyContent, SourceSpan span)
    implements IrStatement {

  public IrCallMacro {
    Objects.requireNonNull(macroName, "macroName must not be null");
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    Objects.requireNonNull(bodyContent, "bodyContent must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrCallMacro of(String macroName, List<IrExpression> arguments, SourceSpan span) {
    return new IrCallMacro(macroName, arguments, Optional.empty(), span);
  }

  public static IrCallMacro of(
      String macroName, List<IrExpression> arguments, IrBlock bodyContent, SourceSpan span) {
    return new IrCallMacro(macroName, arguments, Optional.of(bodyContent), span);
  }
}
