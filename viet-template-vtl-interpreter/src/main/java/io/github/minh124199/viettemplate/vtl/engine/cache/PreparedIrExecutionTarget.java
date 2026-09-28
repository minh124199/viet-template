package io.github.minh124199.viettemplate.vtl.engine.cache;

import io.github.minh124199.viettemplate.api.CompiledTemplate;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

record PreparedIrExecutionTarget(CompiledTemplate preparedIr, Optional<IrTemplate> irTemplate)
    implements ExecutionTarget {

  PreparedIrExecutionTarget {
    Objects.requireNonNull(preparedIr, "preparedIr must not be null");
    Objects.requireNonNull(irTemplate, "irTemplate must not be null");
  }

  @Override
  @SuppressWarnings("removal")
  public void render(RenderContext context, TemplateOutput output) throws IOException {
    try {
      preparedIr.render(context, output);
    } catch (VirtualMachineError | ThreadDeath fatal) {
      throw fatal;
    } catch (ClassCastException cce) {
      io.github.minh124199.viettemplate.api.TemplateId realId =
          irTemplate
              .map(IrTemplate::id)
              .orElseGet(() -> io.github.minh124199.viettemplate.api.TemplateId.of("<generated>"));
      throw new io.github.minh124199.viettemplate.api.TemplateRenderException(
          "Type mismatch during template execution: " + cce.getMessage(),
          realId,
          io.github.minh124199.viettemplate.api.SourceSpan.UNKNOWN,
          io.github.minh124199.viettemplate.api.DiagnosticCode.of("INTERPRETER", "ERROR"),
          cce);
    }
  }
}
