package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluateDirectiveTest extends AbstractInterpreterTest {

  @Test
  void evaluateExecutesDynamicSnippetInCurrentScope() {
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).build());
    Map<String, Object> ctx = Map.of("var", "World", "expr", "Hello $var!");
    assertThat(render("#evaluate($expr)", ctx, interpreter)).isEqualTo("Hello World!");
  }

  @Test
  void evaluateIsDeniedInSafeProfile() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_SAFE).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = Map.of("expr", "Hello World");
    assertThatThrownBy(() -> render("#evaluate($expr)", ctx, interpreter))
        .isInstanceOf(TemplateSecurityException.class)
        .hasMessageContaining("#evaluate is disabled");
  }

  @Test
  void evaluateRespectsLengthAndDepthLimits() {
    ExecutionLimits limits =
        ExecutionLimits.builder().maxDynamicSourceLength(10).maxEvaluateDepth(2).build();
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().profile(VtlProfile.VTL_DYNAMIC).limits(limits).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    Map<String, Object> ctx = Map.of("longExpr", "123456789012345");
    assertThatThrownBy(() -> render("#evaluate($longExpr)", ctx, interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("Dynamic template source exceeds maximum length");
  }
}
