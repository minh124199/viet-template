package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.TemplateLimitException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MacroAndDefineTest extends AbstractInterpreterTest {

  @Test
  void callBeforeDefinitionSucceedsViaDiscoveryPass() {
    String template = "#greet('World')\n#macro(greet $who)Hello $who!#end";
    assertThat(render(template).trim()).isEqualTo("Hello World!");
  }

  @Test
  void macroWithDefaultParameters() {
    String template =
        """
        #macro(badge $label $color='blue')[$color:$label]#end
        #badge('Info')
        #badge('Warning', 'amber')
        """;
    assertThat(render(template).trim()).isEqualTo("[blue:Info]\n[amber:Warning]");
  }

  @Test
  void macroRecursionLimitGuardsAgainstInfiniteLoops() {
    String template =
        """
        #macro(infinite $n)#infinite($n)#end
        #infinite(1)
        """;
    VtlInterpreterOptions options =
        VtlInterpreterOptions.builder()
            .limits(ExecutionLimits.builder().maxMacroDepth(5).build())
            .build();
    VtlInterpreter interpreter = new VtlInterpreter(options);

    assertThatThrownBy(() -> render(template, Map.of(), interpreter))
        .isInstanceOf(TemplateLimitException.class)
        .hasMessageContaining("macro recursion depth");
  }

  @Test
  void blockMacroReceivesBodyContent() {
    String template =
        """
        #macro(box $title)
        <box title="$title">$bodyContent</box>
        #end
        #@box('MyTitle')Inner text from caller#end
        """;
    assertThat(render(template).trim())
        .isEqualTo("<box title=\"MyTitle\">Inner text from caller</box>");
  }

  @Test
  void defineDirectiveCapturesRenderableBlock() {
    String template =
        """
        #define($greeting)Hello $user!#end
        #set($user = 'Alice')$greeting
        #set($user = 'Bob')$greeting
        """;
    assertThat(render(template).trim()).isEqualTo("Hello Alice!\nHello Bob!");
  }
}
