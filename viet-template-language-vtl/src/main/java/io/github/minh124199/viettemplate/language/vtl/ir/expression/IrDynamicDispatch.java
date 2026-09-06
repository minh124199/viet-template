package io.github.minh124199.viettemplate.language.vtl.ir.expression;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicAccessSite;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Expression performing dynamic dispatch at runtime through an indy / PIC call site.
 *
 * <p>Implements the {@code dynamic dispatch callsite} IR operation.
 */
public record IrDynamicDispatch(
    DynamicAccessSite callSite,
    Optional<IrExpression> receiver,
    String targetName,
    List<IrExpression> arguments,
    VType type,
    SourceSpan span)
    implements IrExpression {

  public IrDynamicDispatch {
    Objects.requireNonNull(callSite, "callSite must not be null");
    Objects.requireNonNull(receiver, "receiver must not be null");
    Objects.requireNonNull(targetName, "targetName must not be null");
    arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static IrDynamicDispatch onReceiver(
      DynamicAccessSite callSite,
      IrExpression receiver,
      String targetName,
      List<IrExpression> arguments,
      VType type,
      SourceSpan span) {
    return new IrDynamicDispatch(
        callSite, Optional.of(receiver), targetName, arguments, type, span);
  }

  public static IrDynamicDispatch root(
      DynamicAccessSite callSite, String targetName, VType type, SourceSpan span) {
    return new IrDynamicDispatch(callSite, Optional.empty(), targetName, List.of(), type, span);
  }
}
