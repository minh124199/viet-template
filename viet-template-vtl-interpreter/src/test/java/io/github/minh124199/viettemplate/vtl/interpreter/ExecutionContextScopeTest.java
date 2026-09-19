package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.RenderContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutionContextScopeTest {

  @Test
  void publicScopePushRetainsCopySemantics() {
    ExecutionContext context = new ExecutionContext(RenderContext.empty());
    Map<String, EvaluationValue> callerBindings = new HashMap<>();
    callerBindings.put("value", EvaluationValue.of("before"));

    context.pushScope(callerBindings, false);
    callerBindings.put("value", EvaluationValue.of("after"));

    assertThat(context.lookup("value").value()).isEqualTo("before");
  }

  @Test
  void definedNullShadowsAnOuterValue() {
    ExecutionContext context =
        new ExecutionContext(RenderContext.builder().put("value", "outer").build());
    context.pushScope(Map.of("value", EvaluationValue.definedNull()), false);

    EvaluationValue value = context.lookup("value");

    assertThat(value.isDefined()).isTrue();
    assertThat(value.isNull()).isTrue();
  }

  @Test
  void removingALocalBindingFallsThroughToOuterScope() {
    ExecutionContext context =
        new ExecutionContext(RenderContext.builder().put("value", "outer").build());
    context.pushScope(Map.of("value", EvaluationValue.of("inner")), false);

    context.clearLocalScope("value");

    assertThat(context.lookup("value").value()).isEqualTo("outer");
  }

  @Test
  void foreachScopeUpdatesBothDynamicBindingsWithoutChangingParentMetadata() {
    ExecutionContext context = new ExecutionContext(RenderContext.empty());
    ForeachMetadata parent = new ForeachMetadata(0, 1, true, true, false, null);
    ForeachMetadata current = new ForeachMetadata(1, 2, false, true, false, parent);

    context.pushForeachScope("item", EvaluationValue.undefined(), parent);
    context.updateLoopVariable("item", EvaluationValue.of("second"), current);

    assertThat(context.lookup("item").value()).isEqualTo("second");
    assertThat(context.lookup("foreach").value()).isSameAs(current);
    assertThat(((ForeachMetadata) context.lookup("foreach").value()).parent()).isSameAs(parent);
  }
}
