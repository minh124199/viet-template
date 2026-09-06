package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;
import java.util.Optional;

/** Parameter declaration in a {@code #macro} definition, with optional default value expression. */
public record VtlMacroParameter(
    String name, Optional<VtlExpression> defaultValue, SourceSpan span) {

  public VtlMacroParameter {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(defaultValue, "defaultValue must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlMacroParameter of(String name, SourceSpan span) {
    return new VtlMacroParameter(name, Optional.empty(), span);
  }

  public static VtlMacroParameter of(String name, VtlExpression defaultValue, SourceSpan span) {
    return new VtlMacroParameter(name, Optional.of(defaultValue), span);
  }
}
