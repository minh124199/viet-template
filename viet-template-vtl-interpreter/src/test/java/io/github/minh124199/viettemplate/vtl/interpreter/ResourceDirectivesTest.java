package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceDirectivesTest extends AbstractInterpreterTest {

  @Test
  void includeDirectivesInsertRawContent() {
    TemplateResourceResolver resolver =
        TemplateResourceResolver.inMemory()
            .add("banner.txt", "=== Welcome ===\n")
            .add("footer.txt", "=== Goodbye ===")
            .build();

    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().resourceResolver(resolver).build());

    String template = "#include('banner.txt')Main Body\n#include('footer.txt')";
    assertThat(render(template, Map.of(), interpreter))
        .isEqualTo("=== Welcome ===\nMain Body\n=== Goodbye ===");
  }

  @Test
  void parseDirectiveEvaluatesSubTemplateInCurrentScope() {
    TemplateResourceResolver resolver =
        TemplateResourceResolver.inMemory().add("row.vm", "Row: $rowId for $user\n").build();

    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().resourceResolver(resolver).build());

    String template = "#set($user = 'Alice')#set($rowId = 1)#parse('row.vm')";
    assertThat(render(template, Map.of(), interpreter)).isEqualTo("Row: 1 for Alice\n");
  }

  @Test
  void parseDirectiveRecursionLimitGuardsAgainstCycles() {
    TemplateResourceResolver resolver =
        TemplateResourceResolver.inMemory().add("cycle.vm", "#parse('cycle.vm')").build();

    VtlInterpreter interpreter =
        new VtlInterpreter(
            VtlInterpreterOptions.builder()
                .resourceResolver(resolver)
                .limits(ExecutionLimits.builder().maxParseDepth(3).build())
                .build());

    assertThatThrownBy(() -> render("#parse('cycle.vm')", Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("maxParseDepth");
  }

  @Test
  void missingResourceThrowsTemplateResourceException() {
    TemplateResourceResolver resolver = TemplateResourceResolver.inMemory().build();
    VtlInterpreter interpreter =
        new VtlInterpreter(VtlInterpreterOptions.builder().resourceResolver(resolver).build());

    assertThatThrownBy(() -> render("#include('missing.txt')", Map.of(), interpreter))
        .isInstanceOf(TemplateResourceException.class)
        .hasMessageContaining("missing.txt");

    assertThatThrownBy(() -> render("#parse('missing.vm')", Map.of(), interpreter))
        .isInstanceOf(TemplateResourceException.class)
        .hasMessageContaining("missing.vm");
  }
}
