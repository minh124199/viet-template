package io.github.minh124199.viettemplate.api;

import java.io.IOException;

/**
 * Public template rendering contract.
 *
 * <p>Compiled templates are immutable and thread-safe.
 */
public interface Template {

  TemplateDescriptor descriptor();

  void render(RenderContext context, TemplateOutput output) throws IOException;
}
