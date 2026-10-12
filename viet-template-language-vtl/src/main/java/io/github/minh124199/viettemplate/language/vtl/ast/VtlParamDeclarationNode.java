package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;
import java.util.Optional;

/**
 * AST node representing a typed template parameter declaration {@code #param(Type $variable)}.
 *
 * <p>Preserves declared type syntax, variable identifier, nullability marker, optional default
 * value expression, and accurate source spans for compiler diagnostics and IDE tooling.
 */
public record VtlParamDeclarationNode(
    String name,
    String typeText,
    boolean nullable,
    Optional<VtlExpression> defaultValue,
    SourceSpan span,
    SourceSpan typeSpan,
    SourceSpan nameSpan)
    implements VtlDirectiveNode {

  public VtlParamDeclarationNode {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(typeText, "typeText must not be null");
    Objects.requireNonNull(defaultValue, "defaultValue must not be null");
    Objects.requireNonNull(span, "span must not be null");
    Objects.requireNonNull(typeSpan, "typeSpan must not be null");
    Objects.requireNonNull(nameSpan, "nameSpan must not be null");
  }

  public static VtlParamDeclarationNode of(
      String name,
      String typeText,
      boolean nullable,
      Optional<VtlExpression> defaultValue,
      SourceSpan span,
      SourceSpan typeSpan,
      SourceSpan nameSpan) {
    return new VtlParamDeclarationNode(
        name, typeText, nullable, defaultValue, span, typeSpan, nameSpan);
  }
}
