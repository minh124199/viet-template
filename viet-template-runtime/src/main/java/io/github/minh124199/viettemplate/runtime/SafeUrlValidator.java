package io.github.minh124199.viettemplate.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates URLs against dangerous schemes and obfuscation patterns to ensure safety before
 * constructing {@link SafeUrl} instances.
 */
public final class SafeUrlValidator {

  private static final Set<String> ALLOWED_ABSOLUTE_SCHEMES =
      Set.of("http", "https", "mailto", "tel");

  private static final Set<String> DANGEROUS_SCHEMES =
      Set.of("javascript", "vbscript", "data", "file", "blob");

  private static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+(/.*)?$");

  private SafeUrlValidator() {}

  /**
   * Checks whether the given character sequence is a valid, safe URL.
   *
   * @param url the candidate URL
   * @return true if valid and safe, false otherwise
   */
  public static boolean isValid(CharSequence url) {
    if (url == null) {
      return false;
    }
    try {
      sanitizeOrValidate(url);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Validates and returns the sanitized URL string, stripping leading and trailing whitespace and
   * control characters.
   *
   * @param url the candidate URL
   * @return the validated URL string
   * @throws IllegalArgumentException if the URL contains dangerous schemes or obfuscation
   * @throws NullPointerException if url is null
   */
  public static String sanitizeOrValidate(CharSequence url) {
    Objects.requireNonNull(url, "url must not be null");
    String raw = url.toString();

    // 1. Strip leading and trailing ASCII whitespace and control characters (<= 32)
    int start = 0;
    int end = raw.length();
    while (start < end && raw.charAt(start) <= ' ') {
      start++;
    }
    while (end > start && raw.charAt(end - 1) <= ' ') {
      end--;
    }
    if (start >= end) {
      throw new IllegalArgumentException("URL must not be empty or blank");
    }
    String trimmed = raw.substring(start, end);

    // 2. Reject control characters (< 32 except \t, \r, \n) and null bytes across all URLs
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c < 32 && c != '\t' && c != '\r' && c != '\n') {
        throw new IllegalArgumentException("URL contains control characters: " + trimmed);
      }
    }

    // 3. Reject HTML entity references (e.g. &#106;, &#x6a;, &colon;)
    if (containsHtmlEntity(trimmed)) {
      throw new IllegalArgumentException("URL contains HTML entity references: " + trimmed);
    }

    // 3. Find first colon and first path/query/fragment delimiter ('/', '?', '#')
    int colonIdx = -1;
    int firstDelimIdx = -1;
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c == ':' && colonIdx == -1) {
        colonIdx = i;
      }
      if ((c == '/' || c == '?' || c == '#') && firstDelimIdx == -1) {
        firstDelimIdx = i;
      }
      if (colonIdx != -1 && firstDelimIdx != -1) {
        break;
      }
    }

    // 4. Scheme check: if colon exists and appears before any '/', '?', or '#'
    if (colonIdx != -1 && (firstDelimIdx == -1 || colonIdx < firstDelimIdx)) {
      String rawScheme = trimmed.substring(0, colonIdx);

      // Reject URL-encoded colons or dangerous characters in scheme prefix
      if (containsEncodedColon(rawScheme)) {
        throw new IllegalArgumentException("URL contains URL-encoded colon in scheme: " + trimmed);
      }

      // Reject internal control characters and whitespace in scheme
      for (int i = 0; i < rawScheme.length(); i++) {
        char c = rawScheme.charAt(i);
        if (c <= ' ') {
          throw new IllegalArgumentException(
              "URL scheme contains internal whitespace or control characters: " + trimmed);
        }
      }

      String scheme = rawScheme.toLowerCase(Locale.ROOT);
      if (DANGEROUS_SCHEMES.contains(scheme)) {
        throw new IllegalArgumentException("Dangerous URL scheme rejected: " + scheme);
      }
      if (!ALLOWED_ABSOLUTE_SCHEMES.contains(scheme)) {
        throw new IllegalArgumentException("Disallowed URL scheme: " + scheme);
      }

      // Validate http/https structure
      if ("http".equals(scheme) || "https".equals(scheme)) {
        String rest = trimmed.substring(colonIdx + 1);
        if (!rest.startsWith("//") || rest.length() <= 2) {
          throw new IllegalArgumentException("Malformed http/https URL: " + trimmed);
        }
      }

      // Validate mailto/tel structure
      if ("mailto".equals(scheme) || "tel".equals(scheme)) {
        String rest = trimmed.substring(colonIdx + 1);
        if (rest.isEmpty()) {
          throw new IllegalArgumentException("Empty target for mailto/tel URL: " + trimmed);
        }
      }

      return trimmed;
    }

    // 5. No scheme: check for URL-encoded colon in relative path candidate
    int checkEnd = (firstDelimIdx != -1) ? firstDelimIdx : trimmed.length();
    String prefix = trimmed.substring(0, checkEnd);
    if (containsEncodedColon(prefix)) {
      throw new IllegalArgumentException("URL contains URL-encoded colon: " + trimmed);
    }

    // Reject internal control characters in path candidate
    for (int i = 0; i < trimmed.length(); i++) {
      char c = trimmed.charAt(i);
      if (c < 32 && c != '\t' && c != '\r' && c != '\n') {
        throw new IllegalArgumentException("URL contains control characters: " + trimmed);
      }
    }

    // 6. Relative URL validation
    if ((trimmed.startsWith("/") && !trimmed.startsWith("//"))
        || trimmed.startsWith("./")
        || trimmed.startsWith("../")
        || trimmed.startsWith("?")
        || trimmed.startsWith("#")) {
      return trimmed;
    }

    if (RELATIVE_PATH_PATTERN.matcher(trimmed).matches()) {
      return trimmed;
    }

    throw new IllegalArgumentException("URL format not allowed (fail-closed): " + trimmed);
  }

  private static boolean containsEncodedColon(String s) {
    if (s == null) {
      return false;
    }
    String lower = s.toLowerCase(Locale.ROOT);
    return lower.contains("%3a");
  }

  private static boolean containsHtmlEntity(String s) {
    if (s == null) {
      return false;
    }
    String lower = s.toLowerCase(Locale.ROOT);
    if (lower.contains("&#")) {
      return true;
    }
    int qIdx = lower.indexOf('?');
    String check = (qIdx != -1) ? lower.substring(0, qIdx) : lower;
    return check.contains("&colon") || check.contains("&tab") || check.contains("&newline");
  }
}
