package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScriptSafeAppendableTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "</script>",
        "</SCRIPT>",
        "</ScRiPt>",
        "<script>alert(1)</script>",
        "<!--",
        "-->",
        "<img src=x onerror=alert(1)>",
        "<",
        ">",
        "&"
      })
  void neutralizesHtmlMarkupAndEndTags(String hostile) throws IOException {
    StringBuilder sb = new StringBuilder();
    ScriptSafeAppendable safe = new ScriptSafeAppendable(sb);
    safe.append(hostile);

    String result = sb.toString();
    assertThat(result).doesNotContain("<").doesNotContain(">").doesNotContain("&");
    assertThat(result.toLowerCase()).doesNotContain("</script>");
  }

  @Test
  void escapesU2028AndU2029() throws IOException {
    StringBuilder sb = new StringBuilder();
    ScriptSafeAppendable safe = new ScriptSafeAppendable(sb);
    safe.append("line\u2028separator and paragraph\u2029separator");

    String result = sb.toString();
    assertThat(result).contains("\\u2028").contains("\\u2029");
    assertThat(result).doesNotContain("\u2028").doesNotContain("\u2029");
  }

  @Test
  void preservesNormalTextAndUnicode() throws IOException {
    StringBuilder sb = new StringBuilder();
    ScriptSafeAppendable safe = new ScriptSafeAppendable(sb);
    safe.append("Normal text 123 !@#$^*()_+{}[]:;\"' |\\/? 🚀 Xin chào");

    assertThat(sb.toString()).isEqualTo("Normal text 123 !@#$^*()_+{}[]:;\"' |\\/? 🚀 Xin chào");
  }
}
