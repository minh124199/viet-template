package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * Expression invoking an explicitly allowed method verified by security policy and overload
 * resolution.
 *
 * <p>Implements the {@code InvokeAllowedMethod} IR operation.
 */
public record IrInvokeAllowedMethod(
    IrExpression receiver,
    String methodName,
    List<IrExpression> arguments,
    Method targetMethod,
    VType type,
    SourceSpan span)
    implements IrExpression {

  public IrInvokeAllowedMethod {
    Objects.requireNonNull(receiver, "receiver must not be null");
    Objects.requireNonNull(methodName, "methodName must not be null");
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    Objects.requireNonNull(targetMethod, "targetMethod must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }
}
