package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NullBehaviorPreflightTest extends AbstractInterpreterTest {

  @Test
  void bareNullLiteralInVietExtensionProfile() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().allowBareNullLiteral(true).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    String template = "#set($val = null)#if($val)truthy#{else}falsy#end";
    assertThat(render(template, Map.of(), interpreter)).isEqualTo("falsy");
  }

  @Test
  void setNullRhsPermittedByDefaultVelocitySemantics() {
    // Default options: setNullAllowed = true (Velocity 2.x default)
    VtlInterpreter interpreter = new VtlInterpreter(VtlInterpreterOptions.DEFAULT);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullVal", null);

    // Initial value is overwritten by null/undefined
    String template = "#set($x = 'initial')#set($x = $nullVal)[$!x]";
    assertThat(render(template, ctx, interpreter)).isEqualTo("[]");

    String template2 = "#set($x = 'initial')#set($x = $undefinedVal)[$!x]";
    assertThat(render(template2, ctx, interpreter)).isEqualTo("[]");

    // In non-quiet mode, null evaluates to literal reference
    String template3 = "#set($x = 'initial')#set($x = $nullVal)[$x]";
    assertThat(render(template3, ctx, interpreter)).isEqualTo("[$x]");
  }

  @Test
  void setNullRhsIgnoredWhenConfiguredForLegacy1x() {
    // Legacy 1.x mode: setNullAllowed = false
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().setNullAllowed(false).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullVal", null);

    // Initial value is preserved when RHS evaluates to null or undefined
    String template = "#set($x = 'initial')#set($x = $nullVal)$x";
    assertThat(render(template, ctx, interpreter)).isEqualTo("initial");

    String template2 = "#set($x = 'initial')#set($x = $undefinedVal)$x";
    assertThat(render(template2, ctx, interpreter)).isEqualTo("initial");
  }
}
