package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SpaceGobblingTest extends AbstractInterpreterTest {

  @Test
  void linesModeGobblesStandaloneDirectiveLines() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().spaceGobbling(SpaceGobbler.Mode.LINES).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    String template =
        """
        Line 1
          #if(true)
        Line 2
          #end
        Line 3
        """;

    // Lines mode should swallow whitespace around #if and #end lines
    assertThat(render(template, Map.of(), interpreter)).isEqualTo("Line 1\nLine 2\nLine 3\n");
  }

  @Test
  void noneModePreservesAllWhitespace() {
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder().spaceGobbling(SpaceGobbler.Mode.NONE).build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    String template = "Line 1\n  #if(true)\nLine 2\n  #end\nLine 3\n";

    assertThat(render(template, Map.of(), interpreter))
        .isEqualTo("Line 1\n  \nLine 2\n  \nLine 3\n");
  }
}
