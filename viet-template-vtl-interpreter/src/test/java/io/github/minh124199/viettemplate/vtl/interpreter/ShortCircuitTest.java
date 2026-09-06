package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ShortCircuitTest extends AbstractInterpreterTest {

  public static class Bomb {
    public boolean explode() {
      throw new AssertionError("Short-circuit failed: RHS should not have been evaluated!");
    }
  }

  @Test
  void logicalAndShortCircuitsOnFalse() {
    Map<String, Object> ctx = Map.of("bomb", new Bomb());

    String template = "#if(false && $bomb.explode())boom#{else}ok#end";
    assertThat(render(template, ctx)).isEqualTo("ok");
  }

  @Test
  void logicalOrShortCircuitsOnTrue() {
    Map<String, Object> ctx = Map.of("bomb", new Bomb());

    String template = "#if(true || $bomb.explode())ok#{else}boom#end";
    assertThat(render(template, ctx)).isEqualTo("ok");
  }
}
