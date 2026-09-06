package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TruthinessMatrixTest extends AbstractInterpreterTest {

  @Test
  void verifiesStandardTruthinessMatrixWithEmptyCheckEnabled() {
    // Default: emptyCheck = true
    VtlInterpreter interpreter = new VtlInterpreter(VtlInterpreterOptions.DEFAULT);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullVal", null);
    ctx.put("boolTrue", Boolean.TRUE);
    ctx.put("boolFalse", Boolean.FALSE);
    ctx.put("emptyStr", "");
    ctx.put("blankStr", "   ");
    ctx.put("nonEmptyStr", "abc");
    ctx.put("emptyList", List.of());
    ctx.put("nonEmptyList", List.of("item"));
    ctx.put("emptyMap", Map.of());
    ctx.put("emptyArray", new String[0]);
    ctx.put("zeroInt", 0);
    ctx.put("zeroDouble", 0.0);
    ctx.put("zeroBigInt", java.math.BigInteger.ZERO);
    ctx.put("zeroBigDec", java.math.BigDecimal.ZERO);
    ctx.put("positiveInt", 42);

    assertThat(render("#if($nullVal)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($boolTrue)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($boolFalse)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($emptyStr)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($blankStr)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($nonEmptyStr)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($emptyList)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($nonEmptyList)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($emptyMap)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($emptyArray)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    // Under default emptyCheck = true (Velocity 2.x), 0 and 0.0 are falsy!
    assertThat(render("#if($zeroInt)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($zeroDouble)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($zeroBigInt)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($zeroBigDec)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");
    assertThat(render("#if($positiveInt)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
  }

  @Test
  void verifiesTruthinessMatrixWithEmptyCheckDisabled() {
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().emptyCheck(false).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("emptyStr", "");
    ctx.put("emptyList", List.of());
    ctx.put("emptyMap", Map.of());
    ctx.put("zeroInt", 0);
    ctx.put("zeroDouble", 0.0);
    ctx.put("zeroBigDec", java.math.BigDecimal.ZERO);

    // When emptyCheck = false, any non-null non-false object is truthy
    assertThat(render("#if($emptyStr)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($emptyList)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($emptyMap)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($zeroInt)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($zeroDouble)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
    assertThat(render("#if($zeroBigDec)yes#{else}no#end", ctx, interpreter)).isEqualTo("yes");
  }
}
