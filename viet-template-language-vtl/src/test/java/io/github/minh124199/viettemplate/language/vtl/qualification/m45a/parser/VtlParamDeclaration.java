package io.github.minh124199.viettemplate.language.vtl.qualification.m45a.parser;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.Objects;
import java.util.Optional;

/**
 * Prototype AST declaration node for an optional typed template parameter.
 *
 * <p>Represents the parse output of a {@code #param} declaration with precise source spans for
 * type, variable name, and optional default expression.
 */
public record VtlParamDeclaration(
    String name,
    String typeText,
    boolean nullable,
    Optional<String> defaultValue,
    SourceSpan span,
    SourceSpan typeSpan,
    SourceSpan nameSpan) {

  public VtlParamDeclaration {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(typeText, "typeText must not be null");
    Objects.requireNonNull(defaultValue, "defaultValue must not be null");
    Objects.requireNonNull(span, "span must not be null");
    Objects.requireNonNull(typeSpan, "typeSpan must not be null");
    Objects.requireNonNull(nameSpan, "nameSpan must not be null");
  }

  public static VtlParamDeclaration of(
      String name,
      String typeText,
      boolean nullable,
      SourceSpan span,
      SourceSpan typeSpan,
      SourceSpan nameSpan) {
    return new VtlParamDeclaration(
        name, typeText, nullable, Optional.empty(), span, typeSpan, nameSpan);
  }

  public static VtlParamDeclaration of(
      String name,
      String typeText,
      boolean nullable,
      String defaultValue,
      SourceSpan span,
      SourceSpan typeSpan,
      SourceSpan nameSpan) {
    return new VtlParamDeclaration(
        name, typeText, nullable, Optional.ofNullable(defaultValue), span, typeSpan, nameSpan);
  }
}
