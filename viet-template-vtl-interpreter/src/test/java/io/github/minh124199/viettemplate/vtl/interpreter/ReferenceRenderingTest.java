package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceRenderingTest extends AbstractInterpreterTest {

  @Test
  void rendersSimpleAndFormalReferences() {
    Map<String, Object> ctx = Map.of("user", "Alice", "city", "Hanoi");
    assertThat(render("Hello $user from ${city}!", ctx)).isEqualTo("Hello Alice from Hanoi!");
  }

  @Test
  void rendersQuietReferences() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("defined", "Active");
    ctx.put("nullVal", null);

    // Present value renders normally
    assertThat(render("Status: $!defined", ctx)).isEqualTo("Status: Active");
    assertThat(render("Status: $!{defined}", ctx)).isEqualTo("Status: Active");

    // Null or undefined renders empty string
    assertThat(render("Missing: [$!undefined]", ctx)).isEqualTo("Missing: []");
    assertThat(render("Missing: [$!{undefined}]", ctx)).isEqualTo("Missing: []");
    assertThat(render("Null: [$!nullVal]", ctx)).isEqualTo("Null: []");
    assertThat(render("Null: [$!{nullVal}]", ctx)).isEqualTo("Null: []");
  }

  @Test
  void rendersAlternateValueWhenUndefinedOrNullOrEmpty() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("emptyStr", "");
    ctx.put("nonEmpty", "RealValue");
    ctx.put("nullVal", null);

    // Undefined fallback
    assertThat(render("Hello ${missing|'Guest'}!", ctx)).isEqualTo("Hello Guest!");

    // Null fallback
    assertThat(render("Hello ${nullVal|'Fallback'}!", ctx)).isEqualTo("Hello Fallback!");

    // Empty string fallback (with emptyCheck=true by default)
    assertThat(render("Hello ${emptyStr|'Anonymous'}!", ctx)).isEqualTo("Hello Anonymous!");

    // Provided value does not use fallback
    assertThat(render("Hello ${nonEmpty|'Fallback'}!", ctx)).isEqualTo("Hello RealValue!");
  }

  @Test
  void rendersLiteralSourceForUndefinedInNonStrictMode() {
    assertThat(render("Value: $undefined")).isEqualTo("Value: $undefined");
    assertThat(render("Value: ${undefined}")).isEqualTo("Value: ${undefined}");
    assertThat(render("Value: $undefined.property.nested"))
        .isEqualTo("Value: $undefined.property.nested");
  }

  @Test
  void rendersLiteralSourceForDefinedNullInNonStrictMode() {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullVal", null);

    assertThat(render("Value: $nullVal", ctx)).isEqualTo("Value: $nullVal");
    assertThat(render("Value: ${nullVal}", ctx)).isEqualTo("Value: ${nullVal}");
    assertThat(render("Value: [$!nullVal]", ctx)).isEqualTo("Value: []");
  }
}
