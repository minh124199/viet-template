package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;

/** Common sealed interface representing a single navigation step in a reference access chain. */
public sealed interface VtlAccessStep
    permits VtlAccessStep.PropertyAccess, VtlAccessStep.MethodCall, VtlAccessStep.IndexAccess {

  SourceSpan span();

  /** Property access: {@code .propertyName} */
  record PropertyAccess(String propertyName, SourceSpan span) implements VtlAccessStep {
    public PropertyAccess {
      Objects.requireNonNull(propertyName, "propertyName must not be null");
      Objects.requireNonNull(span, "span must not be null");
    }
  }

  /** Method invocation: {@code .methodName(arg1, arg2, ...)} */
  record MethodCall(String methodName, List<VtlExpression> arguments, SourceSpan span)
      implements VtlAccessStep {
    public MethodCall {
      Objects.requireNonNull(methodName, "methodName must not be null");
      Objects.requireNonNull(span, "span must not be null");
      arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
    }
  }

  /** Index access: {@code [indexExpression]} */
  record IndexAccess(VtlExpression indexExpression, SourceSpan span) implements VtlAccessStep {
    public IndexAccess {
      Objects.requireNonNull(indexExpression, "indexExpression must not be null");
      Objects.requireNonNull(span, "span must not be null");
    }
  }
}
