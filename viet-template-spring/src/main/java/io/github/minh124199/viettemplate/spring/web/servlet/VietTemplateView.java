package io.github.minh124199.viettemplate.spring.web.servlet;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.RenderRequest;
import io.github.minh124199.viettemplate.api.TemplateEngine;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.servlet.View;

/**
 * Spring MVC {@link View} implementation that renders a Viet Template directly to the servlet
 * response output stream.
 *
 * <p>Instances of this class are immutable, thread-safe, and request-stateless.
 */
public final class VietTemplateView implements View {

  public static final String DEFAULT_CONTENT_TYPE = "text/html;charset=UTF-8";
  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private final TemplateEngine engine;
  private final TemplateId templateId;
  private final String contentType;
  private final Charset charset;

  public VietTemplateView(TemplateEngine engine, TemplateId templateId) {
    this(engine, templateId, DEFAULT_CONTENT_TYPE, DEFAULT_CHARSET);
  }

  public VietTemplateView(
      TemplateEngine engine, TemplateId templateId, String contentType, Charset charset) {
    this.engine = Objects.requireNonNull(engine, "engine must not be null");
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    this.contentType = contentType != null ? contentType : DEFAULT_CONTENT_TYPE;
    this.charset = charset != null ? charset : DEFAULT_CHARSET;
    if (!StandardCharsets.UTF_8.equals(this.charset)) {
      throw new IllegalArgumentException(
          "Unsupported charset: "
              + this.charset
              + ". Utf8OutputStreamTemplateOutput requires UTF-8");
    }
  }

  @Override
  public String getContentType() {
    return this.contentType;
  }

  public TemplateEngine getEngine() {
    return this.engine;
  }

  public TemplateId getTemplateId() {
    return this.templateId;
  }

  public Charset getCharset() {
    return this.charset;
  }

  @Override
  public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
      throws Exception {
    if (this.contentType != null) {
      response.setContentType(this.contentType);
    }
    if (this.charset != null) {
      response.setCharacterEncoding(this.charset.name());
    }

    Map<String, Object> attributes = createIntegrationAttributes(request);
    RenderRequest renderRequest =
        RenderRequest.of(this.templateId, toRenderContext(model), attributes);
    OutputStream responseStream = response.getOutputStream();
    try (Utf8OutputStreamTemplateOutput output =
        new Utf8OutputStreamTemplateOutput(new NonClosingOutputStream(responseStream))) {
      this.engine.render(renderRequest, output);
    }
  }

  private static Map<String, Object> createIntegrationAttributes(HttpServletRequest request) {
    if (request == null) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(SpringRenderAttributes.SERVLET_REQUEST, request);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VietTemplateView other)) {
      return false;
    }
    return Objects.equals(this.engine, other.engine)
        && Objects.equals(this.templateId, other.templateId)
        && Objects.equals(this.contentType, other.contentType)
        && Objects.equals(this.charset, other.charset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.engine, this.templateId, this.contentType, this.charset);
  }

  @Override
  public String toString() {
    return "VietTemplateView[templateId="
        + this.templateId
        + ", contentType="
        + this.contentType
        + "]";
  }
}
