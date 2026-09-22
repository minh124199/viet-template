package io.github.minh124199.viettemplate.spring.web.servlet;

import io.github.minh124199.viettemplate.api.Template;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSource;
import io.github.minh124199.viettemplate.api.TemplateSuffixConfiguration;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * Spring MVC {@link ViewResolver} implementation resolving view names to {@link VietTemplateView}
 * instances.
 *
 * <p>Resolves logical view names to normalized {@link TemplateId} values, strictly validates
 * against path traversal, checks template existence in the repository, and caches view instances.
 */
public class VietTemplateViewResolver
    implements ViewResolver, Ordered, InitializingBean, ApplicationContextAware {

  private TemplateEngine engine;
  private String prefix = "";
  private String suffix = "";
  private List<String> suffixes = List.of();
  private volatile TemplateSuffixConfiguration suffixConfig =
      TemplateSuffixConfiguration.of("", List.of());
  private String contentType = VietTemplateView.DEFAULT_CONTENT_TYPE;
  private Charset charset = VietTemplateView.DEFAULT_CHARSET;
  private boolean checkTemplateLocation = true;
  private int order = Ordered.LOWEST_PRECEDENCE;
  private boolean cache = true;
  private final ConcurrentMap<String, View> viewCache = new ConcurrentHashMap<>();
  private ApplicationContext applicationContext;

  public VietTemplateViewResolver() {}

  public VietTemplateViewResolver(TemplateEngine engine) {
    this.engine = Objects.requireNonNull(engine, "engine must not be null");
  }

  public TemplateEngine getEngine() {
    return this.engine;
  }

  public void setEngine(TemplateEngine engine) {
    this.engine = engine;
    clearCache();
  }

  public String getPrefix() {
    return this.prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix != null ? prefix : "";
    clearCache();
  }

  public String getSuffix() {
    return this.suffix;
  }

  public void setSuffix(String suffix) {
    String normalized = suffix != null ? suffix : "";
    TemplateSuffixConfiguration.of(normalized);
    synchronized (this) {
      this.suffix = normalized;
      if (!this.suffixes.isEmpty()) {
        this.suffixConfig =
            TemplateSuffixConfiguration.of(
                this.suffixes.get(0), this.suffixes.subList(1, this.suffixes.size()));
      } else {
        this.suffixConfig = TemplateSuffixConfiguration.of(normalized);
      }
    }
    clearCache();
  }

  public List<String> getSuffixes() {
    return this.suffixes;
  }

  public void setSuffixes(List<String> suffixes) {
    synchronized (this) {
      if (suffixes == null || suffixes.isEmpty()) {
        this.suffixes = List.of();
        this.suffixConfig = TemplateSuffixConfiguration.of(this.suffix != null ? this.suffix : "");
      } else {
        TemplateSuffixConfiguration cfg =
            TemplateSuffixConfiguration.of(suffixes.get(0), suffixes.subList(1, suffixes.size()));
        this.suffixes = cfg.effectiveSuffixes();
        this.suffixConfig = cfg;
      }
    }
    clearCache();
  }

  public TemplateSuffixConfiguration getSuffixConfiguration() {
    return this.suffixConfig;
  }

  public void setSuffixConfiguration(TemplateSuffixConfiguration suffixConfiguration) {
    Objects.requireNonNull(suffixConfiguration, "suffixConfiguration must not be null");
    synchronized (this) {
      this.suffixConfig = suffixConfiguration;
      this.suffix = suffixConfiguration.primarySuffix();
      this.suffixes = suffixConfiguration.effectiveSuffixes();
    }
    clearCache();
  }

  List<String> getEffectiveSuffixes() {
    return this.suffixConfig.effectiveSuffixes();
  }

  public String getContentType() {
    return this.contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
    clearCache();
  }

  public Charset getCharset() {
    return this.charset;
  }

  public void setCharset(Charset charset) {
    this.charset = Objects.requireNonNull(charset, "charset must not be null");
    clearCache();
  }

  public void setCharset(String charsetName) {
    Objects.requireNonNull(charsetName, "charsetName must not be null");
    this.charset = Charset.forName(charsetName);
    clearCache();
  }

  public boolean isCheckTemplateLocation() {
    return this.checkTemplateLocation;
  }

  public void setCheckTemplateLocation(boolean checkTemplateLocation) {
    this.checkTemplateLocation = checkTemplateLocation;
    clearCache();
  }

  @Override
  public int getOrder() {
    return this.order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public boolean isCache() {
    return this.cache;
  }

  public void setCache(boolean cache) {
    this.cache = cache;
    if (!cache) {
      clearCache();
    }
  }

  public void clearCache() {
    this.viewCache.clear();
  }

  public boolean removeFromCache(String viewName) {
    if (viewName == null) {
      return false;
    }
    return this.viewCache.remove(viewName) != null;
  }

  public boolean removeFromCache(String viewName, Locale locale) {
    return removeFromCache(viewName);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (this.engine == null && this.applicationContext != null) {
      Map<String, TemplateEngine> beans =
          this.applicationContext.getBeansOfType(TemplateEngine.class);
      if (!beans.isEmpty()) {
        this.engine = beans.values().iterator().next();
      }
    }
    if (this.engine == null) {
      throw new IllegalArgumentException("Property 'engine' must not be null");
    }
  }

  @Override
  public View resolveViewName(String viewName, Locale locale) throws Exception {
    if (viewName == null) {
      return null;
    }
    validateViewName(viewName);

    if (this.cache) {
      View cachedView = this.viewCache.get(viewName);
      if (cachedView != null) {
        return cachedView;
      }
    }

    TemplateSuffixConfiguration config = this.suffixConfig;
    List<String> effective = config.effectiveSuffixes();
    String matchingSuffix = config.findMatchingSuffix(viewName).orElse(null);

    if (!this.checkTemplateLocation) {
      TemplateId candidate;
      if (matchingSuffix != null) {
        candidate = TemplateId.normalize(this.prefix + viewName);
      } else {
        candidate = TemplateId.normalize(this.prefix + viewName + effective.get(0));
      }
      View view = buildView(candidate);
      if (this.cache) {
        View existing = this.viewCache.putIfAbsent(viewName, view);
        return existing != null ? existing : view;
      }
      return view;
    }

    List<TemplateId> candidates = config.resolveCandidates(this.prefix, viewName);
    for (TemplateId candidate : candidates) {
      if (templateExists(candidate)) {
        View view = buildView(candidate);
        if (this.cache) {
          View existing = this.viewCache.putIfAbsent(viewName, view);
          return existing != null ? existing : view;
        }
        return view;
      }
    }

    return null;
  }

  protected View buildView(TemplateId templateId) throws Exception {
    if (this.engine == null) {
      throw new IllegalStateException(
          "TemplateEngine is not configured on VietTemplateViewResolver");
    }
    return new VietTemplateView(this.engine, templateId, this.contentType, this.charset);
  }

  protected boolean templateExists(TemplateId templateId) {
    if (this.engine == null) {
      return false;
    }
    TemplateRepository repository = this.engine.repository();
    if (repository != null) {
      try {
        Optional<TemplateSource> source = repository.find(templateId);
        if (source != null && source.isPresent()) {
          return true;
        }
      } catch (TemplateResourceException e) {
        // Fall through to engine.get()
      } catch (Exception ignored) {
        // Fall through to engine.get()
      }
    }
    try {
      Template template = this.engine.get(templateId);
      return template != null;
    } catch (TemplateResourceException e) {
      return false;
    } catch (Exception e) {
      // Syntax, compilation or semantic exceptions indicate the template was located
      return true;
    }
  }

  private void validateViewName(String viewName) {
    if (viewName.isBlank()) {
      throw new IllegalArgumentException("View name must not be blank");
    }
    if (viewName.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("View name must not contain null bytes: " + viewName);
    }
    if (viewName.contains("\\")) {
      throw new IllegalArgumentException("View name must not contain backslashes: " + viewName);
    }
    if (viewName.contains("..")) {
      throw new IllegalArgumentException(
          "View name must not contain path traversal ('..'): " + viewName);
    }
    String lower = viewName.toLowerCase(Locale.ROOT);
    if (lower.contains("%2e")
        || lower.contains("%2f")
        || lower.contains("%5c")
        || lower.contains("%00")) {
      throw new IllegalArgumentException(
          "View name contains encoded path separators or traversal sequences: " + viewName);
    }
  }
}
