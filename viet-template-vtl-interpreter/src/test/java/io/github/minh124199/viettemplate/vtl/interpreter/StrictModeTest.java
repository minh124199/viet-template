package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateRenderException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrictModeTest extends AbstractInterpreterTest {

  @Test
  void throwsOnUndefinedReferenceInStrictMode() {
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().strictReferences(true).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    // Both normal and quiet undefined references throw on render in strict mode
    assertThatThrownBy(() -> render("Hello $missingUser!", Map.of(), interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("missingUser");

    assertThatThrownBy(() -> render("Hello $!missingUser!", Map.of(), interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("missingUser");
  }

  @Test
  void throwsOnDefinedNullReferenceInNonQuietStrictMode() {
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().strictReferences(true).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("user", null);

    // Non-quiet defined null throws in strict mode
    assertThatThrownBy(() -> render("Hello $user!", ctx, interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("user");

    // Quiet defined null succeeds and renders empty string
    assertThat(render("User: [$!user]", ctx, interpreter)).isEqualTo("User: []");
  }

  @Test
  void throwsOnNavigatingNullOrUndefinedInStrictMode() {
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().strictReferences(true).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("user", null);

    assertThatThrownBy(() -> render("City: $user.address.city", ctx, interpreter))
        .isInstanceOf(TemplateRenderException.class)
        .hasMessageContaining("Cannot navigate property/method on null or undefined reference");
  }

  @Test
  void conditionalAndAlternateValueDoNotThrowInStrictMode() {
    VtlInterpreterOptions options = VtlInterpreterOptions.builder().strictReferences(true).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = new HashMap<>();
    ctx.put("nullUser", null);

    // Conditionals evaluate cleanly to false without throwing
    assertThat(render("#if($missingUser)yes#{else}no#end", Map.of(), interpreter)).isEqualTo("no");
    assertThat(render("#if($nullUser)yes#{else}no#end", ctx, interpreter)).isEqualTo("no");

    // Alternate value fallback evaluates cleanly without throwing
    assertThat(render("[${missingUser|'fallback'}]", Map.of(), interpreter))
        .isEqualTo("[fallback]");
    assertThat(render("[${nullUser|'fallback'}]", ctx, interpreter)).isEqualTo("[fallback]");
  }
}
