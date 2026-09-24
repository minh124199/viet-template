package io.github.minh124199.viettemplate.spring.boot.autoconfigure.aot;

import io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * {@link RuntimeHintsRegistrar} for Viet Template Spring Boot Auto-Configuration.
 *
 * <p>Registers:
 *
 * <ul>
 *   <li>The AOT template index resource ({@code META-INF/viet-template/templates.idx} and {@code
 *       META-INF/viet-template/*}).
 *   <li>Generated compiled template classes discovered from {@code templates.idx} on the AOT
 *       classpath for reflection (constructors and public methods).
 *   <li>Configuration properties ({@link VietTemplateProperties}) for reflection.
 *   <li>The reference {@code VtlTemplateEngineProvider} fallback provider for reflection.
 * </ul>
 */
public class VietTemplateRuntimeHints implements RuntimeHintsRegistrar {

  private static final Log logger = LogFactory.getLog(VietTemplateRuntimeHints.class);
  private static final String INDEX_RESOURCE = "META-INF/viet-template/templates.idx";
  private static final String VTL_PROVIDER_CLASS =
      "io.github.minh124199.viettemplate.vtl.engine.VtlTemplateEngineProvider";

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    // 1. Register resource patterns for template index
    hints.resources().registerPattern("META-INF/viet-template/templates.idx");
    hints.resources().registerPattern("META-INF/viet-template/*");

    // 2. Register properties
    hints
        .reflection()
        .registerType(
            VietTemplateProperties.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS);
    hints
        .reflection()
        .registerType(
            VietTemplateProperties.Security.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS);

    // 3. Register VTL provider fallback if present
    try {
      hints
          .reflection()
          .registerType(
              TypeReference.of(VTL_PROVIDER_CLASS),
              MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
              MemberCategory.INVOKE_PUBLIC_METHODS);
    } catch (IllegalArgumentException | IllegalStateException ignored) {
    }

    // 4. Discover and register all generated template classes from templates.idx on the AOT
    // classpath
    if (classLoader != null) {
      Set<String> templateClassNames = discoverGeneratedClassNames(classLoader);
      for (String className : templateClassNames) {
        try {
          hints
              .reflection()
              .registerType(
                  TypeReference.of(className),
                  MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                  MemberCategory.INVOKE_PUBLIC_METHODS);
        } catch (IllegalArgumentException | IllegalStateException e) {
          if (logger.isDebugEnabled()) {
            logger.debug("Could not register reflection hint for template class: " + className, e);
          }
        }
      }
    }
  }

  public static Set<String> discoverGeneratedClassNames(ClassLoader classLoader) {
    Set<String> classNames = new LinkedHashSet<>();
    try {
      Enumeration<URL> resources = classLoader.getResources(INDEX_RESOURCE);
      while (resources != null && resources.hasMoreElements()) {
        URL url = resources.nextElement();
        parseIndexUrl(url, classNames);
      }
    } catch (IOException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Failed to read " + INDEX_RESOURCE + " from classLoader", e);
      }
    }
    return classNames;
  }

  public static void parseIndexUrl(URL url, Set<String> classNames) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq > 0) {
          String fqcn = line.substring(eq + 1).trim();
          if (!fqcn.isEmpty()) {
            classNames.add(fqcn);
          }
        }
      }
    } catch (IOException | IllegalArgumentException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Failed to parse index at " + url, e);
      }
    }
  }
}
