package io.github.minh124199.viettemplate.aot;

import io.github.minh124199.viettemplate.api.TemplateId;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable compiled template artifact descriptor. */
public record TemplateAotArtifact(
    TemplateId templateId, String className, Path outputFile, String fingerprint)
    implements Serializable {

  public TemplateAotArtifact {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(className, "className must not be null");
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    Objects.requireNonNull(fingerprint, "fingerprint must not be null");
  }

  /** Returns the simple class name without package prefix. */
  public String simpleClassName() {
    int lastDot = className.lastIndexOf('.');
    return lastDot >= 0 ? className.substring(lastDot + 1) : className;
  }
}
