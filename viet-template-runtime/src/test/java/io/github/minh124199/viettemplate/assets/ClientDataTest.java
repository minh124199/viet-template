package io.github.minh124199.viettemplate.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.runtime.SafeHtml;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientDataTest {

  @Test
  void scriptProducesSafeJsonScriptContainer() {
    ClientData clientData = new ClientData();
    SafeHtml html = clientData.script("employees", Map.of("id", 42, "name", "Jane"));

    String rendered = html.toString();
    assertThat(rendered)
        .startsWith("<script type=\"application/json\" data-vt-client-data=\"employees\">")
        .endsWith("</script>");
    assertThat(rendered).contains("\"id\":42");
    assertThat(rendered).contains("\"name\":\"Jane\"");
  }

  @Test
  void neutralizesHostileScriptBreakoutPayload() {
    ClientData clientData = new ClientData();
    String hostilePayload = "</script><script>alert(1)</script><!-- comment -->";
    SafeHtml html = clientData.script("hostile", Map.of("attack", hostilePayload));

    String rendered = html.toString();
    // The rendered string MUST NOT contain any literal </script> other than the closing tag of the
    // container
    int firstClose = rendered.indexOf("</script>");
    int lastClose = rendered.lastIndexOf("</script>");
    assertThat(firstClose).isEqualTo(lastClose);

    // Payload characters must be unicode-escaped
    assertThat(rendered)
        .contains("\\u003C/script\\u003E\\u003Cscript\\u003Ealert(1)\\u003C/script\\u003E");
    assertThat(rendered).doesNotContain("<script>alert(1)");
    assertThat(rendered).doesNotContain("<!--");
  }

  @Test
  void handlesNullPayloadSafely() {
    ClientData clientData = new ClientData();
    SafeHtml html = clientData.script("nullData", null);

    assertThat(html.toString())
        .isEqualTo(
            "<script type=\"application/json\" data-vt-client-data=\"nullData\">null</script>");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "bad id with spaces",
        "bad<tag>",
        "bad\"quote",
        "bad'single",
        "bad\nnewline",
        "bad/slash"
      })
  void rejectsInvalidClientDataId(String invalidId) {
    ClientData clientData = new ClientData();
    assertThatThrownBy(() -> clientData.script(invalidId, "data"))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e ->
                assertThat(((ClientDataSerializationException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_CLIENT_003));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "counter",
        "counter-island",
        "feature:counter",
        "app:island.counter_v2",
        "scope:sub.module-1_beta:counter"
      })
  void acceptsValidClientDataIdWithColonsAndDelimiters(String validId) {
    ClientData clientData = new ClientData();
    SafeHtml html = clientData.script(validId, Map.of("ready", true));
    assertThat(html.toString()).contains("data-vt-client-data=\"" + validId + "\"");
  }

  @Test
  void failsWhenSerializerIsUnavailable() {
    ClientData clientData = new ClientData(null);
    assertThatThrownBy(() -> clientData.script("users", "data"))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e ->
                assertThat(((ClientDataSerializationException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_CLIENT_001));
  }

  @Test
  void wrapsSerializationExceptionWithStableCode() {
    ClientDataSerializer failingSerializer =
        (value, target) -> {
          throw new RuntimeException("Simulated serializer bug");
        };

    ClientData clientData = new ClientData(failingSerializer);
    assertThatThrownBy(() -> clientData.script("failing", "data"))
        .isInstanceOf(ClientDataSerializationException.class)
        .satisfies(
            e ->
                assertThat(((ClientDataSerializationException) e).code())
                    .isEqualTo(AssetDiagnosticCode.VT_CLIENT_002));
  }

  @Test
  void validatesScriptSafetyAgainstUnescapedCharacters() throws Exception {
    var method =
        ClientData.class.getDeclaredMethod("validateScriptSafety", String.class, String.class);
    method.setAccessible(true);

    method.invoke(
        null,
        "id",
        "<script type=\"application/json\" data-vt-client-data=\"id\">{\"ok\":true}</script>");

    try {
      method.invoke(
          null,
          "id",
          "<script type=\"application/json\" data-vt-client-data=\"id\"><alert></script>");
      org.junit.jupiter.api.Assertions.fail("Expected exception");
    } catch (java.lang.reflect.InvocationTargetException e) {
      assertThat(e.getCause()).isInstanceOf(ClientDataSerializationException.class);
      ClientDataSerializationException ce = (ClientDataSerializationException) e.getCause();
      assertThat(ce.code()).isEqualTo(AssetDiagnosticCode.VT_CLIENT_004);
    }

    try {
      method.invoke(
          null,
          "id",
          "<script type=\"application/json\" data-vt-client-data=\"id\">\u2028</script>");
      org.junit.jupiter.api.Assertions.fail("Expected exception");
    } catch (java.lang.reflect.InvocationTargetException e) {
      assertThat(e.getCause()).isInstanceOf(ClientDataSerializationException.class);
      ClientDataSerializationException ce = (ClientDataSerializationException) e.getCause();
      assertThat(ce.code()).isEqualTo(AssetDiagnosticCode.VT_CLIENT_004);
    }
  }
}
