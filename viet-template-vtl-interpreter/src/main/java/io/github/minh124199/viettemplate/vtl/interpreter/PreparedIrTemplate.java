package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, generation-scoped execution metadata for a final optimized IR template. */
final class PreparedIrTemplate {

  record PreparedFunction(IrFunction function, IrSlotLayout.SlotLayout layout) {
    PreparedFunction {
      Objects.requireNonNull(function, "function must not be null");
      Objects.requireNonNull(layout, "layout must not be null");
    }
  }

  private final IrTemplate template;
  private final IrSlotLayout.SlotLayout rootLayout;
  private final Map<String, PreparedFunction> functions;

  private PreparedIrTemplate(
      IrTemplate template,
      IrSlotLayout.SlotLayout rootLayout,
      Map<String, PreparedFunction> functions) {
    this.template = template;
    this.rootLayout = rootLayout;
    this.functions = functions;
  }

  static PreparedIrTemplate prepare(IrTemplate template) {
    Objects.requireNonNull(template, "template must not be null");
    IrSlotLayout.SlotLayout rootLayout = IrSlotLayout.layout(template);
    if (template.functions().isEmpty()) {
      return new PreparedIrTemplate(template, rootLayout, Map.of());
    }

    Map<String, PreparedFunction> functions = new HashMap<>(template.functions().size());
    for (IrFunction function : template.functions()) {
      functions.put(function.name(), new PreparedFunction(function, IrSlotLayout.layout(function)));
    }
    return new PreparedIrTemplate(template, rootLayout, Map.copyOf(functions));
  }

  IrTemplate template() {
    return template;
  }

  IrSlotLayout.SlotLayout rootLayout() {
    return rootLayout;
  }

  Map<String, PreparedFunction> functions() {
    return functions;
  }
}
