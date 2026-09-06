package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ast.VtlNode;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import java.util.Objects;

/**
 * Represents a lazily executed template block, used for {@code #define} and block macro bodies
 * ({@code $bodyContent}).
 */
public final class RenderableBlock {

  private final List<VtlNode> body;
  private final ExecutionContext capturedContext;
  private final SourceText capturedSource;
  private final TemplateId capturedTemplateId;

  public RenderableBlock(List<VtlNode> body, ExecutionContext capturedContext) {
    this(body, capturedContext, null, null);
  }

  public RenderableBlock(
      List<VtlNode> body,
      ExecutionContext capturedContext,
      SourceText capturedSource,
      TemplateId capturedTemplateId) {
    this.body = List.copyOf(Objects.requireNonNull(body, "body must not be null"));
    this.capturedContext =
        Objects.requireNonNull(capturedContext, "capturedContext must not be null");
    this.capturedSource = capturedSource;
    this.capturedTemplateId = capturedTemplateId;
  }

  public List<VtlNode> body() {
    return body;
  }

  public ExecutionContext capturedContext() {
    return capturedContext;
  }

  public SourceText capturedSource() {
    return capturedSource;
  }

  public TemplateId capturedTemplateId() {
    return capturedTemplateId;
  }

  @Override
  public String toString() {
    return "[RenderableBlock nodes=" + body.size() + "]";
  }
}
