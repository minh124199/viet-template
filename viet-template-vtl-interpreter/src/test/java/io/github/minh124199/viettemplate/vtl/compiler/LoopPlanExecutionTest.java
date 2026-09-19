package io.github.minh124199.viettemplate.vtl.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.InMemoryTemplateRepository;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import io.github.minh124199.viettemplate.vtl.compiler.bytecode.BytecodeRuntimeBridge;
import io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngine;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionTier;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoopPlanExecutionTest {

  @Test
  void arraysMapsAndRangesPreserveBackendParity() throws IOException {
    assertParity(new Object[] {"a", "b", "c"}, "[0:1:a:false][1:2:b:false][2:3:c:true]");
    assertParity(new int[] {1, 2, 3}, "[0:1:1:false][1:2:2:false][2:3:3:true]");
    assertParity(new long[] {4, 5, 6}, "[0:1:4:false][1:2:5:false][2:3:6:true]");

    Map<String, Integer> values = new LinkedHashMap<>();
    values.put("a", 7);
    values.put("b", 8);
    assertParity(values, "[0:1:7:false][1:2:8:true]");

    assertParity(
        "#foreach($item in [3..1])[$item:$foreach.last]#end",
        Map.of(),
        "[3:false][2:false][1:true]");
  }

  @Test
  void callerIteratorIsStreamingAndBreakDoesNotDrainIt() throws IOException {
    for (ExecutionTier tier : List.of(ExecutionTier.IR, ExecutionTier.AOT_BYTECODE)) {
      CountingIterator iterator = new CountingIterator(5);
      String output =
          render(
              tier,
              "#foreach($item in $items)$item#if($foreach.count == 2)#break#end#end",
              Map.of("items", iterator));

      assertThat(output).as(tier.name()).isEqualTo("12");
      assertThat(iterator.nextCalls).as(tier.name()).isEqualTo(2);
    }
  }

  @Test
  void iteratorConsumptionInterleavesWithRendering() throws IOException {
    for (ExecutionTier tier : List.of(ExecutionTier.IR, ExecutionTier.AOT_BYTECODE)) {
      ObservableIterator iterator = new ObservableIterator(3);
      String output = render(tier, "#foreach($item in $items)$item#end", Map.of("items", iterator));
      assertThat(output).as(tier.name()).isEqualTo("123");
    }
  }

  @Test
  void rangeIteratorChecksLimitsAndTerminatesSafelyAtIntegerBounds() {
    Iterator<?> ascending =
        BytecodeRuntimeBridge.rangeIterator(
            Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 2, "range.vm", 1, 1, 1, 10);
    assertThat(List.of(ascending.next(), ascending.next()))
        .containsExactly(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
    assertThat(ascending.hasNext()).isFalse();

    Iterator<?> descending =
        BytecodeRuntimeBridge.rangeIterator(
            Integer.MIN_VALUE + 1, Integer.MIN_VALUE, 2, "range.vm", 1, 1, 1, 10);
    assertThat(List.of(descending.next(), descending.next()))
        .containsExactly(Integer.MIN_VALUE + 1, Integer.MIN_VALUE);
    assertThat(descending.hasNext()).isFalse();

    assertThatThrownBy(
            () ->
                BytecodeRuntimeBridge.rangeIterator(
                    Integer.MIN_VALUE, Integer.MAX_VALUE, 10_000, "range.vm", 1, 1, 1, 10))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("4294967296");
  }

  private static void assertParity(Object items, String expected) throws IOException {
    assertParity(
        "#foreach($item in $items)" + "[$foreach.index:$foreach.count:$item:$foreach.last]#end",
        Map.of("items", items),
        expected);
  }

  private static void assertParity(String template, Map<String, Object> model, String expected)
      throws IOException {
    for (ExecutionTier tier : ExecutionTier.values()) {
      assertThat(render(tier, template, model)).as(tier.name()).isEqualTo(expected);
    }
  }

  private static String render(ExecutionTier tier, String source, Map<String, Object> model)
      throws IOException {
    InMemoryTemplateRepository repository = InMemoryTemplateRepository.create();
    repository.put("loop-plan.vm", source);
    try (VtlTemplateEngine engine =
        VtlTemplateEngine.builder().repository(repository).executionTier(tier).build()) {
      Template template = engine.get("loop-plan.vm");
      StringTemplateOutput output = new StringTemplateOutput();
      template.render(RenderContext.of(model), output);
      return output.toString();
    }
  }

  private static final class CountingIterator implements Iterator<Integer> {
    private final int size;
    private int nextCalls;

    private CountingIterator(int size) {
      this.size = size;
    }

    @Override
    public boolean hasNext() {
      return nextCalls < size;
    }

    @Override
    public Integer next() {
      return ++nextCalls;
    }
  }

  private static final class ObservableIterator implements Iterator<Object> {
    private final int size;
    private int nextCalls;

    private ObservableIterator(int size) {
      this.size = size;
    }

    @Override
    public boolean hasNext() {
      return nextCalls < size;
    }

    @Override
    public Object next() {
      nextCalls++;
      return new Object() {
        @Override
        public String toString() {
          return Integer.toString(nextCalls);
        }
      };
    }
  }
}
