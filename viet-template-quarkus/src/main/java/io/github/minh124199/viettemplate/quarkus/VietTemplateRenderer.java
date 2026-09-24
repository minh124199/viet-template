package io.github.minh124199.viettemplate.quarkus;

import io.github.minh124199.viettemplate.api.DiagnosticCode;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateRepository;
import io.github.minh124199.viettemplate.api.TemplateResourceException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.api.TemplateSuffixConfiguration;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.runtime.stream.NonClosingOutputStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Convenient application-scoped template rendering helper for Quarkus applications.
 *
 * <p>Supports rendering directly to {@link String} or streaming to an {@link OutputStream} using
 * {@link NonClosingOutputStream} to protect container-managed response streams.
 */
@ApplicationScoped
public class VietTemplateRenderer {

  @Inject TemplateEngine engine;

  @Inject VietTemplateConfig config;

  private volatile TemplateSuffixConfiguration suffixConfig;

  public VietTemplateRenderer() {}

  public VietTemplateRenderer(TemplateEngine engine, VietTemplateConfig config) {
    this.engine = Objects.requireNonNull(engine, "engine must not be null");
    this.config = Objects.requireNonNull(config, "config must not be null");
  }

  /**
   * Renders the specified template to a {@link String}.
   *
   * @param templateName the logical or file template name
   * @param model the data model
   * @return rendered output string
   * @throws TemplateRenderException if rendering fails
   */
  public String render(String templateName, Map<String, ?> model) {
    Objects.requireNonNull(templateName, "templateName must not be null");
    TemplateId templateId = resolveTemplateId(templateName);
    return render(templateId, model);
  }

  /**
   * Renders the specified {@link TemplateId} to a {@link String}.
   *
   * @param templateId normalized template identifier
   * @param model the data model
   * @return rendered output string
   * @throws TemplateRenderException if rendering fails
   */
  public String render(TemplateId templateId, Map<String, ?> model) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    try {
      return engine.render(templateId, toRenderContext(model));
    } catch (IOException e) {
      throw new TemplateRenderException(
          "Failed to render template: " + templateId.value(),
          templateId,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("RENDER", "IO_ERROR"),
          e);
    }
  }

  /**
   * Renders the specified template into the provided {@link OutputStream}.
   *
   * @param templateName the logical or file template name
   * @param model the data model
   * @param output target output stream
   * @throws TemplateRenderException if rendering fails
   */
  public void render(String templateName, Map<String, ?> model, OutputStream output) {
    Objects.requireNonNull(templateName, "templateName must not be null");
    TemplateId templateId = resolveTemplateId(templateName);
    render(templateId, model, output);
  }

  /**
   * Renders the specified {@link TemplateId} into the provided {@link OutputStream}.
   *
   * @param templateId normalized template identifier
   * @param model the data model
   * @param output target output stream
   * @throws TemplateRenderException if rendering fails
   */
  public void render(TemplateId templateId, Map<String, ?> model, OutputStream output) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(output, "output must not be null");
    try (Utf8OutputStreamTemplateOutput templateOutput =
        new Utf8OutputStreamTemplateOutput(new NonClosingOutputStream(output))) {
      RenderRequest request = RenderRequest.of(templateId, toRenderContext(model));
      engine.render(request, templateOutput);
    } catch (IOException e) {
      throw new TemplateRenderException(
          "Failed to render template: " + templateId.value(),
          templateId,
          SourceSpan.UNKNOWN,
          DiagnosticCode.of("RENDER", "IO_ERROR"),
          e);
    }
  }

  /**
   * Resolves a logical or file template name to an effective {@link TemplateId}.
   *
   * @param templateName the template name
   * @return resolved template identifier
   */
  public TemplateId resolveTemplateId(String templateName) {
    Objects.requireNonNull(templateName, "templateName must not be null");
    TemplateSuffixConfiguration sc = getSuffixConfiguration();
    String path = (config != null && config.path() != null) ? config.path().trim() : "";

    // First try without path if templateName already exists directly
    List<TemplateId> candidatesDirect = sc.resolveCandidates("", templateName);
    for (TemplateId candidate : candidatesDirect) {
      if (templateExists(candidate)) {
        return candidate;
      }
    }

    // Next try with configured path prefix
    if (!path.isEmpty() && !templateName.startsWith(path + "/") && !templateName.equals(path)) {
      List<TemplateId> candidatesWithPath = sc.resolveCandidates(path, templateName);
      for (TemplateId candidate : candidatesWithPath) {
        if (templateExists(candidate)) {
          return candidate;
        }
      }
    }

    // Default to the first direct candidate
    return candidatesDirect.get(0);
  }

  @SuppressWarnings("removal")
  private boolean templateExists(TemplateId templateId) {
    if (engine == null) {
      return false;
    }
    TemplateRepository repository = engine.repository();
    if (repository != null) {
      try {
        if (repository.find(templateId).isPresent()) {
          return true;
        }
      } catch (VirtualMachineError | ThreadDeath fatal) {
        throw fatal;
      } catch (TemplateSecurityException e) {
        throw e;
      } catch (TemplateResourceException e) {
        // Fall through to engine.get()
      } catch (Exception ignored) {
      }
    }
    try {
      return engine.get(templateId) != null;
    } catch (VirtualMachineError | ThreadDeath fatal) {
      throw fatal;
    } catch (TemplateSecurityException e) {
      throw e;
    } catch (TemplateResourceException e) {
      return false;
    } catch (Exception e) {
      return true;
    }
  }

  private TemplateSuffixConfiguration getSuffixConfiguration() {
    TemplateSuffixConfiguration sc = this.suffixConfig;
    if (sc == null) {
      String primary = (config != null && config.suffix() != null) ? config.suffix() : ".vtl";
      List<String> additional = (config != null) ? config.effectiveAdditionalSuffixes() : List.of();
      sc = TemplateSuffixConfiguration.of(primary, additional);
      this.suffixConfig = sc;
    }
    return sc;
  }

  private static RenderContext toRenderContext(Map<String, ?> model) {
    if (model == null || model.isEmpty()) {
      return RenderContext.empty();
    }
    Map<String, Object> map = new LinkedHashMap<>(model.size());
    for (Map.Entry<String, ?> entry : model.entrySet()) {
      if (entry.getKey() != null) {
        map.put(entry.getKey(), entry.getValue());
      }
    }
    return RenderContext.of(map);
  }
}
