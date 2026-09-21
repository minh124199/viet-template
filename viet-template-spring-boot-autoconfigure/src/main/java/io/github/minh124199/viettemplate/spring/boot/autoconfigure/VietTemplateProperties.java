package io.github.minh124199.viettemplate.spring.boot.autoconfigure;

import io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/** Configuration properties for Viet Template Spring Boot integration. */
@ConfigurationProperties(prefix = "viet-template")
public class VietTemplateProperties {

  public static final String DEFAULT_PREFIX = "";
  public static final String DEFAULT_SUFFIX = "";
  public static final List<String> DEFAULT_SUFFIXES = List.of();
  public static final String DEFAULT_CONTENT_TYPE = VietTemplateView.DEFAULT_CONTENT_TYPE;
  public static final Charset DEFAULT_CHARSET = VietTemplateView.DEFAULT_CHARSET;

  /** Whether to enable Viet Template auto-configuration. */
  private boolean enabled = true;

  /** Prefix that gets prepended to view names when building a template identifier. */
  private String prefix = DEFAULT_PREFIX;

  /** Suffix that gets appended to view names when building a template identifier. */
  private String suffix = DEFAULT_SUFFIX;

  /**
   * Ordered list of suffixes appended to view names when resolving template identifiers. Takes
   * precedence over {@link #suffix} when non-empty.
   */
  private List<String> suffixes = new ArrayList<>();

  /** Content-Type value for template views. */
  private String contentType = DEFAULT_CONTENT_TYPE;

  /** Character encoding used for template rendering and output encoding. */
  private Charset charset = DEFAULT_CHARSET;

  /** Whether to enable caching of resolved view instances. */
  private boolean cache = true;

  /** Whether to check that the template location exists. */
  private boolean checkTemplateLocation = true;

  /**
   * Whether dynamic runtime compilation of templates is permitted. If false, only precompiled AOT
   * templates are allowed.
   */
  private boolean runtimeCompilationEnabled = true;

  /** Maximum number of entries retained in the template engine compilation cache. */
  private int maxCacheEntries = 500;

  /** Time-to-live in milliseconds for negative cache entries (non-existent template lookups). */
  private long negativeCacheTtlMillis = 5000L;

  /** Whether filesystem watching and hot reload are enabled for template source changes. */
  private boolean hotReload = false;

  /** Debounce delay in milliseconds for filesystem watch events during hot reload. */
  private long watchDebounceMillis = 50L;

  /** ViewResolver order in the Spring MVC resolver chain. */
  private int order = Ordered.LOWEST_PRECEDENCE;

  public boolean isEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getPrefix() {
    return this.prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix != null ? prefix : "";
  }

  public String getSuffix() {
    return this.suffix;
  }

  public void setSuffix(String suffix) {
    String normalized = suffix != null ? suffix : "";
    validateSuffix(normalized);
    this.suffix = normalized;
  }

  public List<String> getSuffixes() {
    return this.suffixes;
  }

  public void setSuffixes(List<String> suffixes) {
    if (suffixes == null) {
      this.suffixes = new ArrayList<>();
      return;
    }
    Set<String> unique = new LinkedHashSet<>(suffixes.size());
    for (String s : suffixes) {
      if (s == null) {
        throw new IllegalArgumentException("Suffix element must not be null");
      }
      validateSuffix(s);
      unique.add(s);
    }
    this.suffixes = new ArrayList<>(unique);
  }

  List<String> determineEffectiveSuffixes() {
    return !this.suffixes.isEmpty()
        ? List.copyOf(this.suffixes)
        : List.of(this.suffix != null ? this.suffix : DEFAULT_SUFFIX);
  }

  private static void validateSuffix(String suffix) {
    if (suffix == null) {
      throw new IllegalArgumentException("Suffix must not be null");
    }
    if (suffix.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Suffix must not contain null bytes: " + suffix);
    }
    if (suffix.contains("..")) {
      throw new IllegalArgumentException(
          "Suffix must not contain path traversal ('..'): " + suffix);
    }
    if (suffix.contains("/") || suffix.contains("\\")) {
      throw new IllegalArgumentException("Suffix must not contain path separators: " + suffix);
    }
    if (suffix.contains(":")) {
      throw new IllegalArgumentException(
          "Suffix must not contain URI schemes or drive letters (':'): " + suffix);
    }
    String lower = suffix.toLowerCase(Locale.ROOT);
    if (lower.contains("%2e")
        || lower.contains("%2f")
        || lower.contains("%5c")
        || lower.contains("%00")) {
      throw new IllegalArgumentException(
          "Suffix contains encoded path separators or traversal sequences: " + suffix);
    }
  }

  public String getContentType() {
    return this.contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public Charset getCharset() {
    return this.charset;
  }

  public void setCharset(Charset charset) {
    this.charset = charset;
  }

  public boolean isCache() {
    return this.cache;
  }

  public void setCache(boolean cache) {
    this.cache = cache;
  }

  public boolean isCheckTemplateLocation() {
    return this.checkTemplateLocation;
  }

  public void setCheckTemplateLocation(boolean checkTemplateLocation) {
    this.checkTemplateLocation = checkTemplateLocation;
  }

  public boolean isRuntimeCompilationEnabled() {
    return this.runtimeCompilationEnabled;
  }

  public void setRuntimeCompilationEnabled(boolean runtimeCompilationEnabled) {
    this.runtimeCompilationEnabled = runtimeCompilationEnabled;
  }

  public int getMaxCacheEntries() {
    return this.maxCacheEntries;
  }

  public void setMaxCacheEntries(int maxCacheEntries) {
    this.maxCacheEntries = maxCacheEntries;
  }

  public long getNegativeCacheTtlMillis() {
    return this.negativeCacheTtlMillis;
  }

  public void setNegativeCacheTtlMillis(long negativeCacheTtlMillis) {
    this.negativeCacheTtlMillis = negativeCacheTtlMillis;
  }

  public boolean isHotReload() {
    return this.hotReload;
  }

  public void setHotReload(boolean hotReload) {
    this.hotReload = hotReload;
  }

  public long getWatchDebounceMillis() {
    return this.watchDebounceMillis;
  }

  public void setWatchDebounceMillis(long watchDebounceMillis) {
    this.watchDebounceMillis = watchDebounceMillis;
  }

  public int getOrder() {
    return this.order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  /** Security-specific integration properties. */
  private final Security security = new Security();

  public Security getSecurity() {
    return this.security;
  }

  /** Configuration properties for Spring Security integration. */
  public static class Security {
    /** Whether to enable Spring Security integration for Viet Template. */
    private boolean enabled = true;

    public boolean isEnabled() {
      return this.enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
