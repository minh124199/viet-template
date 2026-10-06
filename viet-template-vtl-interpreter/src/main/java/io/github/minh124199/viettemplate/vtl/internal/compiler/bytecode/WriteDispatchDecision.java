package io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode;

import java.util.List;
import java.util.Objects;

/**
 * Immutable decision record indicating the output write dispatch path chosen by the compiler, along
 * with any rejection reasons explaining why specialization was bypassed.
 *
 * @param kind the selected dispatch kind
 * @param selectedPath textual representation of the selected runtime bridge method
 * @param specializationRejections immutable list of structured reasons why specialization was
 *     rejected
 */
public record WriteDispatchDecision(
    WriteDispatchKind kind, String selectedPath, List<String> specializationRejections) {

  public WriteDispatchDecision {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(selectedPath, "selectedPath must not be null");
    specializationRejections =
        specializationRejections == null ? List.of() : List.copyOf(specializationRejections);
  }
}
