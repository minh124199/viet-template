package io.github.minh124199.viettemplate.assets;

import java.io.Serial;
import java.util.Objects;

/**
 * Exception thrown when client data serialization or safe encoding fails.
 *
 * <p>Carries a stable diagnostic code (e.g. {@link AssetDiagnosticCode#VT_CLIENT_001}).
 */
public class ClientDataSerializationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;
  private final String dataId;

  public ClientDataSerializationException(String code, String message) {
    this(code, null, message, null);
  }

  public ClientDataSerializationException(String code, String dataId, String message) {
    this(code, dataId, message, null);
  }

  public ClientDataSerializationException(
      String code, String dataId, String message, Throwable cause) {
    super(formatMessage(code, dataId, message), cause);
    this.code = Objects.requireNonNull(code, "code must not be null");
    this.dataId = dataId;
  }

  private static String formatMessage(String code, String dataId, String message) {
    StringBuilder sb = new StringBuilder();
    sb.append("[").append(code).append("] ");
    if (dataId != null && !dataId.isBlank()) {
      sb.append("'").append(dataId).append("': ");
    }
    sb.append(message);
    return sb.toString();
  }

  public String code() {
    return code;
  }

  public String dataId() {
    return dataId;
  }
}
