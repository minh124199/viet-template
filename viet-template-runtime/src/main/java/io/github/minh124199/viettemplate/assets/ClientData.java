package io.github.minh124199.viettemplate.assets;

import io.github.minh124199.viettemplate.runtime.HtmlAttributeEscaper;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Template-facing facade providing {@code $clientData} helper methods in Viet Template.
 *
 * <p>Safely bridges server-side Java models to client-side JavaScript/TypeScript applications and
 * framework islands by emitting non-executable {@code <script type="application/json">} elements
 * protected by script-safe unicode encoding against HTML injection breakouts.
 */
public final class ClientData {

  private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.:-]+$");

  private final ClientDataSerializer serializer;

  public ClientData() {
    this(SimpleJsonSerializer.INSTANCE);
  }

  public ClientData(ClientDataSerializer serializer) {
    this.serializer = serializer;
  }

  /**
   * Emits a script-safe {@code <script type="application/json">} container for the provided data.
   *
   * @param id logical client-data identifier (e.g. {@code "employees"})
   * @param data the Java object to serialize
   * @return safe HTML element containing script-safe JSON payload
   * @throws ClientDataSerializationException if serialization fails, id is invalid, or serializer
   *     is missing
   */
  public SafeHtml script(String id, Object data) {
    StringBuilder sb = new StringBuilder(128);
    try {
      writeScript(id, data, sb);
    } catch (IOException e) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          id,
          "Failed writing client data script: " + e.getMessage(),
          e);
    }
    String rendered = sb.toString();
    validateScriptSafety(id, rendered);
    return SafeHtml.of(rendered);
  }

  private static void validateScriptSafety(String id, String renderedHtml) {
    int start = renderedHtml.indexOf('>') + 1;
    int end = renderedHtml.lastIndexOf("</script>");
    if (start > 0 && end >= start) {
      String payload = renderedHtml.substring(start, end);
      if (payload.indexOf('<') != -1 || payload.indexOf('>') != -1) {
        throw new ClientDataSerializationException(
            AssetDiagnosticCode.VT_CLIENT_004,
            id,
            "Serialized payload contains unescaped markup characters ('<' or '>').");
      }
      if (payload.indexOf('\u2028') != -1 || payload.indexOf('\u2029') != -1) {
        throw new ClientDataSerializationException(
            AssetDiagnosticCode.VT_CLIENT_004,
            id,
            "Serialized payload contains unescaped JavaScript newline separators.");
      }
    }
  }

  /**
   * Writes the script-safe JSON container directly into the provided appendable.
   *
   * @param id logical client-data identifier
   * @param data the Java object to serialize
   * @param target the target appendable
   * @throws IOException if appendable fails
   * @throws ClientDataSerializationException if serializer is missing or id is invalid
   */
  public void writeScript(String id, Object data, Appendable target) throws IOException {
    validateId(id);

    if (serializer == null) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_001,
          id,
          "No ClientDataSerializer is configured. Please provide a serializer bean or adapter.");
    }

    target.append("<script type=\"application/json\" data-vt-client-data=\"");
    escapeAttribute(id, target);
    target.append("\">");

    ScriptSafeAppendable safeTarget = new ScriptSafeAppendable(target);
    try {
      serializer.serialize(data, safeTarget);
    } catch (ClientDataSerializationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_002,
          id,
          "Failed to serialize client data: " + e.getMessage(),
          e);
    }

    target.append("</script>");
  }

  private static void validateId(String id) {
    if (id == null || id.isBlank()) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_003,
          id,
          "Client data identifier must not be null or blank.");
    }
    if (!SAFE_ID_PATTERN.matcher(id).matches()) {
      throw new ClientDataSerializationException(
          AssetDiagnosticCode.VT_CLIENT_003,
          id,
          "Client data identifier contains invalid characters: '"
              + id
              + "'. Allowed characters are letters, numbers, underscores, hyphens, dots, and"
              + " colons.");
    }
  }

  private static void escapeAttribute(String value, Appendable out) throws IOException {
    io.github.minh124199.viettemplate.runtime.StringTemplateOutput output =
        new io.github.minh124199.viettemplate.runtime.StringTemplateOutput(value.length() + 8);
    HtmlAttributeEscaper.INSTANCE.escape(value, output);
    out.append(output.toString());
  }
}
