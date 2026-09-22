package io.github.minh124199.viettemplate.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical framework-neutral configuration for template file suffixes.
 *
 * <p>Supports ordered multi-suffix evaluation with deterministic deduplication, strict security
 * validation (rejecting path traversal, slashes, URI schemes, and encoded variations), and
 * candidate template resolution helpers.
 */
public record TemplateSuffixConfiguration(
    String primarySuffix, List<String> additionalSuffixes, List<String> effectiveSuffixes)
    implements Serializable {

  public TemplateSuffixConfiguration {
    Objects.requireNonNull(primarySuffix, "primarySuffix must not be null");
    Objects.requireNonNull(additionalSuffixes, "additionalSuffixes must not be null");
    Objects.requireNonNull(effectiveSuffixes, "effectiveSuffixes must not be null");
    additionalSuffixes = List.copyOf(additionalSuffixes);
    effectiveSuffixes = List.copyOf(effectiveSuffixes);
  }

  /**
   * Creates a {@link TemplateSuffixConfiguration} with a single primary suffix and no additional
   * suffixes.
   *
   * @param suffix the primary suffix (must not be null)
   * @return the normalized suffix configuration
   * @throws IllegalArgumentException if the suffix is null or contains invalid/traversal characters
   */
  public static TemplateSuffixConfiguration of(String suffix) {
    return of(suffix, List.of());
  }

  /**
   * Creates a {@link TemplateSuffixConfiguration} with a primary suffix and varargs additional
   * suffixes.
   *
   * @param suffix the primary suffix (must not be null)
   * @param additional additional suffixes in priority order
   * @return the normalized suffix configuration
   * @throws IllegalArgumentException if any suffix is null or contains invalid/traversal characters
   */
  public static TemplateSuffixConfiguration of(String suffix, String... additional) {
    Objects.requireNonNull(additional, "additional suffixes must not be null");
    return of(suffix, Arrays.asList(additional));
  }

  /**
   * Creates a {@link TemplateSuffixConfiguration} with a primary suffix and a list of additional
   * suffixes.
   *
   * @param suffix the primary suffix (must not be null)
   * @param additionalSuffixes additional suffixes in priority order
   * @return the normalized suffix configuration
   * @throws IllegalArgumentException if any suffix is null or contains invalid/traversal characters
   */
  public static TemplateSuffixConfiguration of(String suffix, List<String> additionalSuffixes) {
    if (suffix == null) {
      throw new IllegalArgumentException("Suffix must not be null");
    }
    Objects.requireNonNull(additionalSuffixes, "additionalSuffixes must not be null");

    String normalizedPrimary = normalizeAndValidate(suffix);
    Set<String> unique = new LinkedHashSet<>();
    unique.add(normalizedPrimary);

    List<String> additionalList = new ArrayList<>();
    for (String s : additionalSuffixes) {
      if (s == null) {
        throw new IllegalArgumentException("Suffix element must not be null");
      }
      String normalized = normalizeAndValidate(s);
      if (unique.add(normalized)) {
        additionalList.add(normalized);
      }
    }

    return new TemplateSuffixConfiguration(
        normalizedPrimary,
        Collections.unmodifiableList(additionalList),
        Collections.unmodifiableList(new ArrayList<>(unique)));
  }

  /**
   * Finds the first matching configured non-empty suffix that the given path ends with as a suffix
   * of a filename component.
   *
   * @param path the template path or view name (must not be null)
   * @return the matching suffix, or empty if none matches
   */
  public Optional<String> findMatchingSuffix(String path) {
    Objects.requireNonNull(path, "path must not be null");
    if (path.isEmpty()) {
      return Optional.empty();
    }
    for (String s : this.effectiveSuffixes) {
      if (!s.isEmpty() && path.endsWith(s)) {
        int suffixStart = path.length() - s.length();
        if (suffixStart > 0) {
          char prev = path.charAt(suffixStart - 1);
          if (prev != '/' && prev != '\\') {
            return Optional.of(s);
          }
        } else if (suffixStart == 0) {
          return Optional.of(s);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Resolves candidate {@link TemplateId} values for the given prefix and viewName in priority
   * order.
   *
   * <p>If the viewName already ends with a configured suffix (as determined by {@link
   * #findMatchingSuffix(String)}), that exact candidate is returned.
   *
   * @param prefix directory prefix (must not be null)
   * @param viewName the raw template path or logical view name (must not be null)
   * @return ordered, deduplicated list of candidate {@link TemplateId} instances
   * @throws IllegalArgumentException if viewName or prefix contains invalid traversal characters
   */
  public List<TemplateId> resolveCandidates(String prefix, String viewName) {
    Objects.requireNonNull(prefix, "prefix must not be null");
    Objects.requireNonNull(viewName, "viewName must not be null");

    validatePathInput("viewName", viewName);
    if (!prefix.isEmpty()) {
      validatePathInput("prefix", prefix);
    }

    String fullPath;
    if (prefix.isEmpty()) {
      fullPath = viewName;
    } else if (prefix.endsWith("/")
        || prefix.endsWith("\\")
        || viewName.startsWith("/")
        || viewName.startsWith("\\")) {
      fullPath = prefix + viewName;
    } else {
      fullPath = prefix + "/" + viewName;
    }

    List<TemplateId> candidates = new ArrayList<>();
    Set<TemplateId> seen = new LinkedHashSet<>();

    Optional<String> matching = findMatchingSuffix(viewName);
    if (matching.isPresent()) {
      TemplateId exact = TemplateId.normalize(fullPath);
      candidates.add(exact);
      return Collections.unmodifiableList(candidates);
    }

    for (String s : this.effectiveSuffixes) {
      TemplateId candidate = TemplateId.normalize(fullPath + s);
      if (seen.add(candidate)) {
        candidates.add(candidate);
      }
    }

    return Collections.unmodifiableList(candidates);
  }

  private static void validatePathInput(String name, String value) {
    if (value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException(name + " must not contain null bytes: " + value);
    }
    if (value.contains("..")) {
      throw new IllegalArgumentException(
          name + " contains path traversal sequence ('..'): " + value);
    }
    if (value.contains(":")) {
      throw new IllegalArgumentException(
          name + " contains URI scheme or drive letter (':'): " + value);
    }
    String lower = value.toLowerCase(Locale.ROOT);
    if (lower.contains("%2e")
        || lower.contains("%2f")
        || lower.contains("%5c")
        || lower.contains("%00")) {
      throw new IllegalArgumentException(
          name + " contains encoded path separators or traversal sequences: " + value);
    }
  }

  /**
   * Resolves candidate {@link TemplateId} values for the given path with no prefix.
   *
   * @param path the raw template path or logical view name (must not be null)
   * @return ordered, deduplicated list of candidate {@link TemplateId} instances
   */
  public List<TemplateId> resolveCandidates(String path) {
    return resolveCandidates("", path);
  }

  private static String normalizeAndValidate(String suffix) {
    if (suffix == null) {
      throw new IllegalArgumentException("Suffix must not be null");
    }
    if (suffix.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Suffix must not contain null bytes: " + suffix);
    }
    String trimmed = suffix.trim();
    if (trimmed.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Suffix must not contain null bytes: " + suffix);
    }
    if (trimmed.contains("..")) {
      throw new IllegalArgumentException(
          "Suffix must not contain path traversal ('..'): " + suffix);
    }
    if (trimmed.contains("/") || trimmed.contains("\\")) {
      throw new IllegalArgumentException("Suffix must not contain path separators: " + suffix);
    }
    if (trimmed.contains(":")) {
      throw new IllegalArgumentException(
          "Suffix must not contain URI schemes or drive letters (':'): " + suffix);
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (lower.contains("%2e")
        || lower.contains("%2f")
        || lower.contains("%5c")
        || lower.contains("%00")) {
      throw new IllegalArgumentException(
          "Suffix contains encoded path separators or traversal sequences: " + suffix);
    }
    return trimmed;
  }
}
