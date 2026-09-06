package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateLimitException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionLimitsTest extends AbstractInterpreterTest {

  @Test
  void enforcesMaxRangeSize() {
    ExecutionLimits limits = ExecutionLimits.builder().maxRangeSize(100).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    assertThatThrownBy(() -> render("#foreach($i in [1..1000])$i#end", Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Range size exceeds maximum limit");
  }

  @Test
  void enforcesMaxLoopIterations() {
    ExecutionLimits limits = ExecutionLimits.builder().maxLoopIterations(5).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    Map<String, Object> ctx = Map.of("nums", numbers);

    assertThatThrownBy(() -> render("#foreach($i in $nums)$i#end", ctx, interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maximum foreach iterations");
  }

  @Test
  void enforcesMaxOutputCharacters() {
    ExecutionLimits limits = ExecutionLimits.builder().maxOutputCharacters(20).build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().limits(limits).build());

    assertThatThrownBy(
            () -> render("This string is way longer than twenty characters", Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("rendered output characters limit");
  }
}
