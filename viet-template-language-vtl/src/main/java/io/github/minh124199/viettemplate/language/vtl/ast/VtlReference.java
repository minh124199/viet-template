package io.github.minh124199.viettemplate.language.vtl.ast;

import io.github.minh124199.viettemplate.api.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a complete VTL reference, such as {@code $user.name}, {@code $!{service.lookup($id)}},
 * or {@code ${name|'default'}}.
 */
public record VtlReference(
    ReferenceNotation notation,
    String rootName,
    List<VtlAccessStep> steps,
    Optional<VtlExpression> alternateValue,
    SourceSpan span) {

  public VtlReference {
    Objects.requireNonNull(notation, "notation must not be null");
    Objects.requireNonNull(rootName, "rootName must not be null");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
    Objects.requireNonNull(alternateValue, "alternateValue must not be null");
    Objects.requireNonNull(span, "span must not be null");
  }

  public static VtlReference of(
      ReferenceNotation notation,
      String rootName,
      List<VtlAccessStep> steps,
      Optional<VtlExpression> alternateValue,
      SourceSpan span) {
    return new VtlReference(notation, rootName, steps, alternateValue, span);
  }

  public static VtlReference of(
      ReferenceNotation notation, String rootName, List<VtlAccessStep> steps, SourceSpan span) {
    return new VtlReference(notation, rootName, steps, Optional.empty(), span);
  }

  public boolean isQuiet() {
    return notation.quiet();
  }

  public boolean isFormal() {
    return notation.formal();
  }

  public List<VtlAccessStep> accessSteps() {
    return steps();
  }
}
