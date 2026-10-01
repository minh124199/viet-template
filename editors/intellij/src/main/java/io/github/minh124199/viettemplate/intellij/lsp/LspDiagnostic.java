package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.Objects;

/**
 * Diagnostic item published by the Viet Template Language Server.
 */
public record LspDiagnostic(
    LspRange range,
    int severity,
    String code,
    String source,
    String message
) {
  public static final int SEVERITY_ERROR = 1;
  public static final int SEVERITY_WARNING = 2;
  public static final int SEVERITY_INFORMATION = 3;
  public static final int SEVERITY_HINT = 4;

  public LspDiagnostic {
    Objects.requireNonNull(range, "range must not be null");
    code = (code != null) ? code : "";
    source = (source != null) ? source : "viet-template";
    message = (message != null) ? message : "";
  }
}
