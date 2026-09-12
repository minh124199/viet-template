package io.github.minh124199.viettemplate.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeUrlValidatorPropertyTest {

  private static final long SEED = 0x5AFE071L;

  private static boolean isDeepMode() {
    return "deep".equalsIgnoreCase(System.getProperty("vietTemplate.fuzz.mode"))
        || "deep".equalsIgnoreCase(System.getenv("VIET_FUZZ_MODE"));
  }

  private static final String[] SAFE_SCHEMES = {"http", "https", "mailto", "tel"};

  private static final String[] DANGEROUS_SCHEMES = {
    "javascript", "vbscript", "data", "file", "blob"
  };

  private static final String[] VALID_RELATIVE_PATHS = {
    "/path",
    "/index.html",
    "/api/v1/resource",
    "./relative",
    "./path/to/file",
    "./logo.png",
    "../parent",
    "../dir/file.txt",
    "?query=val&other=123",
    "#section",
    "images/banner.jpg",
    "item-123_test"
  };

  private static final String[] OBFUSCATION_PREFIXES_SUFFIXES = {
    " ",
    "\t",
    "\n",
    "\r",
    "\r\n",
    "  \t  ",
    "\0"
  };

  private static final String[] DANGEROUS_COLON_VARIANTS = {
    "%3a", "%3A", "&colon;", "&#58;", "&#x3a;", "&#x3A;"
  };

  @Test
  @DisplayName("P14: SafeUrl exposes no public unchecked constructor")
  void verifySafeUrlHasNoPublicConstructors() {
    assertThat(SafeUrl.class.getConstructors())
        .as("SafeUrl must have 0 public constructors to enforce factory validation")
        .isEmpty();
  }

  @Test
  @DisplayName("P4: Valid URLs accepted deterministically and tryOf agrees with of")
  void acceptedUrlsRemainAcceptedDeterministically() {
    SplittableRandom rng = new SplittableRandom(SEED);
    int iterations = isDeepMode() ? 5000 : 500;

    for (int i = 0; i < iterations; i++) {
      String candidate;
      int choice = rng.nextInt(3);
      if (choice == 0) {
        // Safe absolute HTTP/HTTPS URL
        String scheme = rng.nextBoolean() ? "http" : "https";
        if (rng.nextBoolean()) {
          // Randomize case
          scheme = randomizeCase(scheme, rng);
        }
        candidate = scheme + "://example.com/path/" + i + "?q=" + (rng.nextInt(1000));
      } else if (choice == 1) {
        // Safe mailto or tel
        String scheme = rng.nextBoolean() ? "mailto" : "tel";
        if (rng.nextBoolean()) {
          scheme = randomizeCase(scheme, rng);
        }
        candidate = scheme + ":" + (scheme.equalsIgnoreCase("tel") ? "+123456789" : "user" + i + "@example.com");
      } else {
        // Safe relative path
        candidate = VALID_RELATIVE_PATHS[rng.nextInt(VALID_RELATIVE_PATHS.length)];
      }

      // Invariant: SafeUrlValidator.isValid agrees with tryOf and of
      assertThat(SafeUrlValidator.isValid(candidate))
          .as("Valid candidate %s must pass validation", candidate)
          .isTrue();

      Optional<SafeUrl> opt = SafeUrl.tryOf(candidate);
      assertThat(opt).as("tryOf(%s) must be present", candidate).isPresent();

      SafeUrl safe = SafeUrl.of(candidate);
      assertThat(safe).as("of(%s) must succeed", candidate).isNotNull();
      assertThat(opt.get()).isEqualTo(safe);
    }
  }

  @Test
  @DisplayName("P5: Dangerous schemes with any casing are strictly rejected")
  void dangerousSchemesAreRejected() {
    SplittableRandom rng = new SplittableRandom(SEED + 2);

    for (String dangerous : DANGEROUS_SCHEMES) {
      for (int i = 0; i < 30; i++) {
        String casedScheme = randomizeCase(dangerous, rng);
        String url = casedScheme + ":alert('xss')";

        assertThat(SafeUrlValidator.isValid(url))
            .as("Dangerous scheme %s must be rejected", url)
            .isFalse();

        assertThat(SafeUrl.tryOf(url)).as("tryOf(%s) must be empty", url).isEmpty();

        assertThatThrownBy(() -> SafeUrl.of(url))
            .as("SafeUrl.of(%s) must throw IllegalArgumentException", url)
            .isInstanceOf(IllegalArgumentException.class);

        // Invariant: SafeUrl.ofTrusted bypasses validation for explicitly trusted content
        SafeUrl trusted = SafeUrl.ofTrusted(url);
        assertThat(trusted.content().toString()).isEqualTo(url);
      }
    }
  }

  @Test
  @DisplayName("P5: Scheme obfuscation and encoded colons are rejected")
  void schemeObfuscationIsRejected() {
    SplittableRandom rng = new SplittableRandom(SEED + 3);

    for (String dangerous : DANGEROUS_SCHEMES) {
      // 1. Whitespace or control characters inside scheme
      for (int pos = 1; pos < dangerous.length(); pos++) {
        String internalWs = dangerous.substring(0, pos) + "\t" + dangerous.substring(pos) + ":evil()";
        assertThat(SafeUrlValidator.isValid(internalWs)).isFalse();
        assertThat(SafeUrl.tryOf(internalWs)).isEmpty();
        assertThatThrownBy(() -> SafeUrl.of(internalWs)).isInstanceOf(IllegalArgumentException.class);
      }

      // 2. Encoded colons: javascript%3a, etc.
      for (String colon : DANGEROUS_COLON_VARIANTS) {
        String encodedColonUrl = dangerous + colon + "alert(1)";
        assertThat(SafeUrlValidator.isValid(encodedColonUrl))
            .as("Encoded colon %s must be rejected", encodedColonUrl)
            .isFalse();
        assertThat(SafeUrl.tryOf(encodedColonUrl)).isEmpty();
        assertThatThrownBy(() -> SafeUrl.of(encodedColonUrl))
            .isInstanceOf(IllegalArgumentException.class);
      }
    }

    // 3. Null bytes in URL
    List<String> nullUrls =
        List.of(
            "https://example.com/\0evil",
            "javascript\0:alert(1)",
            "/path/\0secret",
            "mailto:user\0@example.com");
    for (String nullUrl : nullUrls) {
      assertThat(SafeUrlValidator.isValid(nullUrl)).isFalse();
      assertThat(SafeUrl.tryOf(nullUrl)).isEmpty();
      assertThatThrownBy(() -> SafeUrl.of(nullUrl)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  @DisplayName("P4: tryOf and of agree for arbitrary bounded inputs")
  void tryOfAndOfAlwaysAgree() {
    SplittableRandom rng = new SplittableRandom(SEED + 4);
    int iterations = isDeepMode() ? 5000 : 500;

    String[] fragments = {
      "http", "https", "javascript", "data", "file", "tel", "mailto", "ftp",
      "://", ":", "/", "//", "./", "../", "?", "#", " ", "\t", "\0", "%3a",
      "example.com", "alert(1)", "foo", "bar", "123", "&colon;", "&#58;"
    };

    for (int i = 0; i < iterations; i++) {
      int count = 1 + rng.nextInt(5);
      StringBuilder sb = new StringBuilder();
      for (int c = 0; c < count; c++) {
        sb.append(fragments[rng.nextInt(fragments.length)]);
      }
      String input = sb.toString();

      boolean valid = SafeUrlValidator.isValid(input);
      Optional<SafeUrl> opt = SafeUrl.tryOf(input);

      assertThat(opt.isPresent())
          .as("SafeUrl.tryOf(%s).isPresent() must match isValid()", input)
          .isEqualTo(valid);

      if (valid) {
        SafeUrl safe = SafeUrl.of(input);
        assertThat(opt.get()).isEqualTo(safe);
      } else {
        assertThatThrownBy(() -> SafeUrl.of(input))
            .as("SafeUrl.of(%s) must throw when invalid", input)
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
  }

  private static String randomizeCase(String input, SplittableRandom rng) {
    StringBuilder sb = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (rng.nextBoolean()) {
        sb.append(Character.toUpperCase(c));
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    return sb.toString();
  }
}
