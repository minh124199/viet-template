package io.github.minh124199.viettemplate.vtl.interpreter;

import java.util.Objects;

/**
 * Represents the 3-state evaluation result in the reference interpreter:
 *
 * <ul>
 *   <li>{@code UNDEFINED}: Reference or property was not present in the context.
 *   <li>{@code DEFINED_NULL}: Key or property exists explicitly with a {@code null} value.
 *   <li>{@code DEFINED_VALUE}: Non-null value.
 * </ul>
 */
public final class EvaluationValue {

  public enum State {
    UNDEFINED,
    DEFINED_NULL,
    DEFINED_VALUE
  }

  private static final EvaluationValue UNDEFINED_INSTANCE =
      new EvaluationValue(State.UNDEFINED, null);
  private static final EvaluationValue DEFINED_NULL_INSTANCE =
      new EvaluationValue(State.DEFINED_NULL, null);

  private final State state;
  private final Object value;

  private EvaluationValue(State state, Object value) {
    this.state = state;
    this.value = value;
  }

  public static EvaluationValue undefined() {
    return UNDEFINED_INSTANCE;
  }

  public static EvaluationValue definedNull() {
    return DEFINED_NULL_INSTANCE;
  }

  public static EvaluationValue of(Object value) {
    if (value == null) {
      return DEFINED_NULL_INSTANCE;
    }
    if (value instanceof EvaluationValue ev) {
      return ev;
    }
    return new EvaluationValue(State.DEFINED_VALUE, value);
  }

  public State state() {
    return state;
  }

  public boolean isDefined() {
    return state != State.UNDEFINED;
  }

  public boolean isUndefined() {
    return state == State.UNDEFINED;
  }

  public boolean isNull() {
    return state == State.DEFINED_NULL;
  }

  public boolean isNonNull() {
    return state == State.DEFINED_VALUE;
  }

  public Object value() {
    return value;
  }

  public Object asObjectOrNull() {
    return state == State.DEFINED_VALUE ? value : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EvaluationValue that)) return false;
    return state == that.state && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(state, value);
  }

  @Override
  public String toString() {
    return switch (state) {
      case UNDEFINED -> "<UNDEFINED>";
      case DEFINED_NULL -> "<NULL>";
      case DEFINED_VALUE -> String.valueOf(value);
    };
  }
}
