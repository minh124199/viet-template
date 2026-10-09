package io.github.minh124199.viettemplate.assets;

import io.github.minh124199.viettemplate.runtime.HtmlAttributeEscaper;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.SafeUrl;
import io.github.minh124199.viettemplate.runtime.SafeUrlValidator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Template-facing facade providing {@code $assets} helper methods in Viet Template.
 *
 * <p>Emits pre-validated, contextually safe HTML tags for stylesheets, module preloads, entry
 * scripts, and static asset URLs. Deduplicates CSS, preloads, and development HMR scripts within a
 * single helper call.
 */
public final class FrontendAssets {

  private final AssetResolver resolver;

  public FrontendAssets(AssetResolver resolver) {
    this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
  }

  /**
   * Emits HTML tags for the document {@code <head>}, including stylesheets, module preloads, and
   * any development-mode HMR client script.
   *
   * @param entry the logical entry name (e.g. {@code "src/pages/employees/index.ts"})
   * @return safe HTML tags ready to be rendered in the document head
   */
  public SafeHtml head(String entry) {
    if (entry == null || entry.isBlank()) {
      return SafeHtml.of("");
    }
    return head(List.of(entry));
  }

  /**
   * Emits HTML tags for multiple entries in the document {@code <head>}, deduplicating shared
   * stylesheets, module preloads, and dev-server client scripts.
   *
   * @param entries logical entry names
   * @return safe HTML tags ready to be rendered in the document head
   */
  public SafeHtml head(String... entries) {
    if (entries == null || entries.length == 0) {
      return SafeHtml.of("");
    }
    return head(Arrays.asList(entries));
  }

  /**
   * Emits HTML tags for a collection of entries in the document {@code <head>}.
   *
   * @param entries list of logical entry names or objects with string representation
   * @return safe HTML tags ready to be rendered in the document head
   */
  public SafeHtml head(List<?> entries) {
    if (entries == null || entries.isEmpty()) {
      return SafeHtml.of("");
    }

    List<String> entryNames = normalizeEntries(entries);
    if (entryNames.isEmpty()) {
      return SafeHtml.of("");
    }

    String devClientScriptUrl = null;
    Set<String> stylesheets = new LinkedHashSet<>();
    Set<String> preloads = new LinkedHashSet<>();

    for (String name : entryNames) {
      ResolvedFrontendEntry resolved = resolver.resolveEntry(name);
      if (resolved.devClientScript() != null && devClientScriptUrl == null) {
        devClientScriptUrl = resolved.devClientScript().url();
      }
      for (AssetStylesheet ss : resolved.stylesheets()) {
        stylesheets.add(ss.url());
      }
      for (AssetModulePreload mp : resolved.modulePreloads()) {
        preloads.add(mp.url());
      }
    }

    StringBuilder sb = new StringBuilder();
    if (devClientScriptUrl != null) {
      sb.append("<script type=\"module\" src=\"")
          .append(escapeAttribute(devClientScriptUrl))
          .append("\"></script>\n");
    }
    for (String cssUrl : stylesheets) {
      sb.append("<link rel=\"stylesheet\" href=\"").append(escapeAttribute(cssUrl)).append("\">\n");
    }
    for (String preloadUrl : preloads) {
      sb.append("<link rel=\"modulepreload\" href=\"")
          .append(escapeAttribute(preloadUrl))
          .append("\">\n");
    }

    return SafeHtml.of(sb.toString());
  }

  /**
   * Emits entry {@code <script>} tag(s) for the document {@code <body>}.
   *
   * @param entry the logical entry name (e.g. {@code "src/pages/employees/index.ts"})
   * @return safe HTML script tag(s)
   */
  public SafeHtml body(String entry) {
    if (entry == null || entry.isBlank()) {
      return SafeHtml.of("");
    }
    return body(List.of(entry));
  }

  /**
   * Emits entry {@code <script>} tags for multiple entries in the document {@code <body>},
   * deduplicating repeated scripts within the invocation.
   *
   * @param entries logical entry names
   * @return safe HTML script tag(s)
   */
  public SafeHtml body(String... entries) {
    if (entries == null || entries.length == 0) {
      return SafeHtml.of("");
    }
    return body(Arrays.asList(entries));
  }

  /**
   * Emits entry {@code <script>} tags for a collection of entries in the document {@code <body>}.
   *
   * @param entries list of logical entry names
   * @return safe HTML script tag(s)
   */
  public SafeHtml body(List<?> entries) {
    if (entries == null || entries.isEmpty()) {
      return SafeHtml.of("");
    }

    List<String> entryNames = normalizeEntries(entries);
    if (entryNames.isEmpty()) {
      return SafeHtml.of("");
    }

    Set<AssetScript> scripts = new LinkedHashSet<>();
    for (String name : entryNames) {
      ResolvedFrontendEntry resolved = resolver.resolveEntry(name);
      if (resolved.script() != null) {
        scripts.add(resolved.script());
      }
    }

    StringBuilder sb = new StringBuilder();
    for (AssetScript script : scripts) {
      sb.append("<script");
      if (script.module()) {
        sb.append(" type=\"module\"");
      }
      sb.append(" src=\"").append(escapeAttribute(script.url())).append("\"></script>\n");
    }

    return SafeHtml.of(sb.toString());
  }

  /**
   * Resolves a logical static asset (e.g. {@code "src/images/logo.svg"}) to a {@link SafeUrl}.
   *
   * @param logicalAsset the logical asset name
   * @return safe validated URL suitable for {@code <img src="...">} attributes
   */
  public SafeUrl url(String logicalAsset) {
    Objects.requireNonNull(logicalAsset, "logicalAsset must not be null");
    ResolvedAsset resolved = resolver.resolveAsset(logicalAsset);
    return SafeUrl.of(SafeUrlValidator.sanitizeOrValidate(resolved.url()));
  }

  private static List<String> normalizeEntries(List<?> raw) {
    List<String> result = new ArrayList<>(raw.size());
    for (Object item : raw) {
      if (item != null) {
        String s = item.toString().trim();
        if (!s.isEmpty()) {
          result.add(s);
        }
      }
    }
    return result;
  }

  private static String escapeAttribute(String value) {
    io.github.minh124199.viettemplate.runtime.StringTemplateOutput output =
        new io.github.minh124199.viettemplate.runtime.StringTemplateOutput(value.length() + 8);
    try {
      HtmlAttributeEscaper.INSTANCE.escape(value, output);
    } catch (java.io.IOException ignored) {
    }
    return output.toString();
  }
}
