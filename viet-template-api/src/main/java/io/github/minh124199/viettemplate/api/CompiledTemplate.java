package io.github.minh124199.viettemplate.api;

import java.io.IOException;

/**
 * Public template contract for ahead-of-time (AOT) and dynamically compiled bytecode templates.
 *
 * <p>Compiled templates are immutable and thread-safe.
 */
public interface CompiledTemplate extends Template {

  @Override
  void render(RenderContext context, TemplateOutput output) throws IOException;
}
