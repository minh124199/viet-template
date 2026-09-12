package io.github.minh124199.viettemplate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateIdPropertyTest {

  private static final long SEED = 0x7E391D10L;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  private static final String[] VALID_SEGMENTS = {
    "views", "users", "admin", "partials", "layout", "item_123", "sub-dir", "test", "a", "index.vm"
  };

  private static final String[] DRIVE_OR_URI_PREFIXES = {
    "C:", "C:/", "C:\\", "D:\\templates\\", "file:///", "http://", "jar:file:"
  };

  private static final String[] WINDOWS_DEVICE_NAMES = {
    "CON",
    "con.vm",
    "PRN",
    "prn.txt",
    "AUX",
    "aux.html",
    "NUL",
    "nul.vtl",
    "COM1",
    "com9",
    "LPT1",
    "lpt5"
  };

  private static final String[] ENCODED_TRAVERSAL_AND_NULLS = {
    "\0",
    "%00",
    "template\0.vm",
    "%2e%2e/file.vm",
    "path/%2e%2e/secret.vm",
    "path%2ffile.vm",
    "path%5cfile.vm"
  };

  @Test
  @DisplayName("P10: Normalized valid relative paths never escape logical repository namespace")
  void normalizedPathsRemainWithinNamespace() {
    SplittableRandom rng = new SplittableRandom(SEED);
    int iterations = isDeepMode() ? 5000 : 500;

    for (int i = 0; i < iterations; i++) {
      int segmentCount = 1 + rng.nextInt(6);
      StringBuilder pathBuilder = new StringBuilder();
      for (int s = 0; s < segmentCount; s++) {
        if (s > 0) {
          pathBuilder.append(rng.nextBoolean() ? "/" : "\\");
        }
        pathBuilder.append(VALID_SEGMENTS[rng.nextInt(VALID_SEGMENTS.length)]);
      }

      String rawPath = pathBuilder.toString();
      TemplateId normalized;
      try {
        normalized = TemplateId.normalize(rawPath);
      } catch (Exception e) {
        throw new AssertionError("Valid generated path failed normalization: " + rawPath, e);
      }

      assertThat(normalized.value()).isNotBlank();
      assertThat(normalized.value()).doesNotStartWith("/");
      assertThat(normalized.value()).doesNotContain("\\");
      assertThat(normalized.value()).doesNotContain("..");
      assertThat(normalized.value()).doesNotContain(":");
      assertThat(normalized.value()).doesNotContain("\0");

      // Invariant: normalization is strictly idempotent
      TemplateId secondPass = TemplateId.normalize(normalized.value());
      assertThat(secondPass).isEqualTo(normalized);

      // Invariant: isTraversalSafe agrees with normalize
      assertThat(TemplateId.isTraversalSafe(rawPath)).isTrue();
    }
  }

  @Test
  @DisplayName("P10: Path traversal escaping above root is strictly rejected")
  void escapingTraversalsAreRejected() {
    SplittableRandom rng = new SplittableRandom(SEED + 1);
    int iterations = isDeepMode() ? 3000 : 300;

    for (int i = 0; i < iterations; i++) {
      int depth = rng.nextInt(4); // 0 to 3 directories deep
      StringBuilder sb = new StringBuilder();
      for (int d = 0; d < depth; d++) {
        sb.append("dir").append(d).append("/");
      }
      // Add more '..' than directory depth to force escaping above root
      for (int t = 0; t <= depth; t++) {
        sb.append(rng.nextBoolean() ? "../" : "..\\");
      }
      sb.append("target.vm");
      String escapingPath = sb.toString();

      assertThat(TemplateId.isTraversalSafe(escapingPath))
          .as("isTraversalSafe must be false for escaping traversal: %s", escapingPath)
          .isFalse();

      assertThatThrownBy(() -> TemplateId.normalize(escapingPath))
          .as("normalize must throw for escaping traversal: %s", escapingPath)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("P10: Drive letters and URI schemes are rejected by normalize and of")
  void driveLettersAndUriSchemesRejected() {
    for (String prefix : DRIVE_OR_URI_PREFIXES) {
      String rawPath = prefix + "views/list.vm";
      assertThat(TemplateId.isTraversalSafe(rawPath))
          .as("isTraversalSafe must reject %s", rawPath)
          .isFalse();
      assertThatThrownBy(() -> TemplateId.normalize(rawPath))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> TemplateId.of(rawPath)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("P10: Windows device names, encoded traversal, and null bytes are rejected")
  void windowsDeviceNamesAndNullBytesRejected() {
    for (String dev : WINDOWS_DEVICE_NAMES) {
      String rawPath = "views/" + dev;
      assertThat(TemplateId.isTraversalSafe(rawPath))
          .as("isTraversalSafe must reject device name %s", rawPath)
          .isFalse();
      assertThatThrownBy(() -> TemplateId.normalize(rawPath))
          .isInstanceOf(IllegalArgumentException.class);
    }

    for (String bad : ENCODED_TRAVERSAL_AND_NULLS) {
      assertThat(TemplateId.isTraversalSafe(bad))
          .as("isTraversalSafe must reject encoded or null path %s", bad)
          .isFalse();
      assertThatThrownBy(() -> TemplateId.normalize(bad))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("Direct TemplateId construction validates invariants")
  void directConstructionInvariants() {
    List<String> invalidIds =
        List.of(
            "",
            "   ",
            "/absolute.vm",
            "users\\list.vm",
            "users/../list.vm",
            "\\\\server\\share\\template.vm",
            "C:file.vm",
            "file.vm\0",
            "path%2efile.vm",
            "path%2ffile.vm",
            "path%5cfile.vm",
            "path%00file.vm");

    for (String invalid : invalidIds) {
      assertThatThrownBy(() -> TemplateId.of(invalid))
          .as("Direct construction of TemplateId.of(%s) must be rejected", invalid)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
