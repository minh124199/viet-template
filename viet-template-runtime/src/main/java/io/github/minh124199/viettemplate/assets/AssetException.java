package io.github.minh124199.viettemplate.assets;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unchecked exception thrown when asset resolution or manifest processing fails.
 *
 * <p>Carries a stable diagnostic code (e.g. {@code VT-ASSET-003}) and actionable remediation
 * details, including closest entry suggestions when applicable.
 */
public class AssetException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;
  private final String logicalTarget;
  private final ArrayList<String> suggestions;

  public AssetException(String code, String message) {
    this(code, null, message, null, null);
  }

  public AssetException(String code, String logicalTarget, String message) {
    this(code, logicalTarget, message, null, null);
  }

  public AssetException(String code, String logicalTarget, String message, Throwable cause) {
    this(code, logicalTarget, message, null, cause);
  }

  public AssetException(
      String code,
      String logicalTarget,
      String message,
      List<String> suggestions,
      Throwable cause) {
    super(formatFullMessage(code, logicalTarget, message, suggestions), cause);
    this.code = Objects.requireNonNull(code, "code must not be null");
    this.logicalTarget = logicalTarget;
    this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
  }

  private static String formatFullMessage(
      String code, String logicalTarget, String message, List<String> suggestions) {
    StringBuilder sb = new StringBuilder();
    sb.append("[").append(code).append("] ");
    if (logicalTarget != null && !logicalTarget.isBlank()) {
      sb.append("'").append(logicalTarget).append("': ");
    }
    sb.append(message);
    if (suggestions != null && !suggestions.isEmpty()) {
      sb.append(" (Did you mean: ").append(String.join(", ", suggestions)).append("?)");
    }
    return sb.toString();
  }

  /** Returns the stable diagnostic code (e.g. {@link AssetDiagnosticCode#VT_ASSET_003}). */
  public String code() {
    return code;
  }

  /** Returns the logical entry or asset name that failed resolution, if known. */
  public String logicalTarget() {
    return logicalTarget;
  }

  /** Returns suggestions of closest available entrypoints, if available. */
  public List<String> suggestions() {
    return suggestions;
  }
}
