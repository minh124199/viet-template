package io.github.minh124199.viettemplate.language.vtl.ir;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import java.util.List;
import java.util.Objects;

/**
 * Root intermediate representation of an analyzed and lowered template.
 *
 * <p>Serves as the contract between the frontend analyzer and execution backends (interpreter,
 * dynamic linker, AOT bytecode compiler).
 */
public record IrTemplate(
    TemplateId id,
    List<IrParameter> parameters,
    IrBlock root,
    IrConstantPool constants,
    TemplateCapabilities capabilities,
    List<IrFunction> functions,
    SourceSpan span) {

  public IrTemplate {
    Objects.requireNonNull(id, "id must not be null");
    parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
    Objects.requireNonNull(root, "root must not be null");
    Objects.requireNonNull(constants, "constants must not be null");
    Objects.requireNonNull(capabilities, "capabilities must not be null");
    functions = List.copyOf(Objects.requireNonNull(functions, "functions must not be null"));
    Objects.requireNonNull(span, "span must not be null");
  }
}
