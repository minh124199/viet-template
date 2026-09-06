package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TextAndEscapingTest extends AbstractInterpreterTest {

  @Test
  void rendersPlainTextVerbatim() {
    String input = "Hello, World! Welcome to Viet Template.\nLine 2: Special chars <>&\"'";
    assertThat(render(input)).isEqualTo(input);
  }

  @Test
  void rendersUnicodeAndVietnameseText() {
    String input = "Xin chào Việt Nam! Cảm ơn bạn đã sử dụng bộ máy sinh mẫu này. 🌟🇻🇳";
    assertThat(render(input)).isEqualTo(input);
  }

  @Test
  void rendersRawBlockContentUnparsed() {
    String input = "Prefix #[[ Inside raw block: $foo.bar() and #if(true) nested #end ]]# Suffix";
    assertThat(render(input))
        .isEqualTo("Prefix  Inside raw block: $foo.bar() and #if(true) nested #end  Suffix");
  }

  @Test
  void handlesReferenceEscaping() {
    Map<String, Object> ctx = Map.of("name", "World");

    // Defined reference escaping
    assertThat(render("\\$name", ctx)).isEqualTo("$name");
    assertThat(render("\\\\$name", ctx)).isEqualTo("\\World");
    assertThat(render("\\\\\\$name", ctx)).isEqualTo("\\$name");
    assertThat(render("\\\\\\\\$name", ctx)).isEqualTo("\\\\World");

    // Formal reference escaping
    assertThat(render("\\${name}", ctx)).isEqualTo("${name}");
    assertThat(render("\\\\${name}", ctx)).isEqualTo("\\World");
  }

  @Test
  void handlesUndefinedReferenceEscapingInNonStrictMode() {
    // When reference does not resolve, Velocity leaves single backslash as literal
    assertThat(render("\\$undefined")).isEqualTo("\\$undefined");
    assertThat(render("\\\\$undefined")).isEqualTo("\\\\$undefined");
  }

  @Test
  void handlesDirectiveEscaping() {
    // Escaped directive does not execute
    assertThat(render("\\#if(true)visible\\#end")).isEqualTo("#if(true)visible#end");

    // Even number of backslashes escapes backslashes, directive executes
    assertThat(render("\\\\#if(true)visible#end")).isEqualTo("\\visible");
    assertThat(render("\\\\\\#if(true)visible\\#end")).isEqualTo("\\#if(true)visible#end");
  }
}
