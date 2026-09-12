package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExecutionFrameTest {

  @Test
  void preservesUndefinedDefinedNullAndDefinedValue() {
    ExecutionFrame frame = new ExecutionFrame(3);

    assertThat(frame.get(0)).isSameAs(EvaluationValue.undefined());
    frame.set(1, EvaluationValue.definedNull());
    frame.set(2, EvaluationValue.of("value"));

    assertThat(frame.get(0).state()).isEqualTo(EvaluationValue.State.UNDEFINED);
    assertThat(frame.get(1).state()).isEqualTo(EvaluationValue.State.DEFINED_NULL);
    assertThat(frame.get(2).state()).isEqualTo(EvaluationValue.State.DEFINED_VALUE);
    assertThat(frame.get(2).value()).isEqualTo("value");
  }

  @Test
  void growthInitializesEveryNewSlotAsUndefined() {
    ExecutionFrame frame = new ExecutionFrame(1);
    frame.set(4, EvaluationValue.of(42));

    assertThat(frame.size()).isGreaterThanOrEqualTo(5);
    assertThat(frame.get(1)).isSameAs(EvaluationValue.undefined());
    assertThat(frame.get(3)).isSameAs(EvaluationValue.undefined());
    assertThat(frame.get(4).value()).isEqualTo(42);
  }

  @Test
  void resetResetsSlotToUndefined() {
    ExecutionFrame frame = new ExecutionFrame(3);
    frame.set(1, EvaluationValue.of("hello"));
    assertThat(frame.get(1).value()).isEqualTo("hello");

    frame.reset(1);
    assertThat(frame.get(1).isUndefined()).isTrue();
  }

  @Test
  void resetMultipleSlots() {
    ExecutionFrame frame = new ExecutionFrame(4);
    frame.set(1, EvaluationValue.of("a"));
    frame.set(2, EvaluationValue.of("b"));
    frame.set(3, EvaluationValue.of("c"));

    frame.reset(new int[] {1, 3});
    assertThat(frame.get(1).isUndefined()).isTrue();
    assertThat(frame.get(2).value()).isEqualTo("b");
    assertThat(frame.get(3).isUndefined()).isTrue();
  }

  @Test
  void seedInitializesSlotWithValue() {
    ExecutionFrame frame = new ExecutionFrame(2);
    frame.seed(0, EvaluationValue.of("seeded"));
    frame.seed(1, EvaluationValue.definedNull());

    assertThat(frame.get(0).value()).isEqualTo("seeded");
    assertThat(frame.get(1).isNull()).isTrue();
  }
}
