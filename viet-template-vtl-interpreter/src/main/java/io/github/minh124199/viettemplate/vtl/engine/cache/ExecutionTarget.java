package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import java.io.IOException;

sealed interface ExecutionTarget
    permits AotExecutionTarget, PreparedIrExecutionTarget, PreparedAstExecutionTarget {
  void render(RenderContext context, TemplateOutput output) throws IOException;
}
