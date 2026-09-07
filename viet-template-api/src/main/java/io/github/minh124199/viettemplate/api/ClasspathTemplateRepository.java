package io.github.minh124199.viettemplate.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * Classpath-backed {@link TemplateRepository} that loads template sources using a {@link
 * ClassLoader}.
 *
 * <p>Resource lookups are traversal-safe and confined under the configured base prefix.
 */
public final class ClasspathTemplateRepository implements TemplateRepository {

  private final ClassLoader classLoader;
  private final String prefix;
  private final Charset charset;

  public ClasspathTemplateRepository(ClassLoader classLoader, String prefix, Charset charset) {
    this.classLoader =
        Objects.requireNonNullElseGet(
            classLoader,
            () ->
                Objects.requireNonNullElse(
                    Thread.currentThread().getContextClassLoader(),
                    ClasspathTemplateRepository.class.getClassLoader()));
    this.charset = Objects.requireNonNull(charset, "charset must not be null");
    this.prefix = sanitizePrefix(prefix);
  }

  public ClasspathTemplateRepository(ClassLoader classLoader, String prefix) {
    this(classLoader, prefix, StandardCharsets.UTF_8);
  }

  public static ClasspathTemplateRepository of(String prefix) {
    return new ClasspathTemplateRepository(null, prefix, StandardCharsets.UTF_8);
  }

  public static ClasspathTemplateRepository of(ClassLoader classLoader, String prefix) {
    return new ClasspathTemplateRepository(classLoader, prefix, StandardCharsets.UTF_8);
  }

  public static ClasspathTemplateRepository of(
      ClassLoader classLoader, String prefix, Charset charset) {
    return new ClasspathTemplateRepository(classLoader, prefix, charset);
  }

  @Override
  public Optional<TemplateSource> find(TemplateId id) {
    Objects.requireNonNull(id, "id must not be null");

    // Traversal safety check
    String normalizedPath = TemplateId.normalize(id.value()).value();
    String resourcePath = prefix.isEmpty() ? normalizedPath : prefix + "/" + normalizedPath;

    URL resourceUrl = classLoader.getResource(resourcePath);
    if (resourceUrl == null) {
      return Optional.empty();
    }

    try (InputStream is = resourceUrl.openStream()) {
      if (is == null) {
        return Optional.empty();
      }
      byte[] bytes = is.readAllBytes();
      String content = new String(bytes, charset);
      long lastModified = 0L;
      try {
        lastModified = resourceUrl.openConnection().getLastModified();
      } catch (Exception ignored) {
      }
      URI uri = URI.create("classpath:/" + resourcePath);
      return Optional.of(TemplateSource.of(id, uri, charset, content, lastModified));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public String prefix() {
    return prefix;
  }

  public Charset charset() {
    return charset;
  }

  private static String sanitizePrefix(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String s = raw.replace('\\', '/').trim();
    while (s.startsWith("/")) {
      s = s.substring(1);
    }
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }
}
