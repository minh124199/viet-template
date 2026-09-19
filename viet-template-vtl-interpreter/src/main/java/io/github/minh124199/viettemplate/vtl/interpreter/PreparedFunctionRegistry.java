package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Request-scoped lookup over immutable prepared functions with a lazy dynamic-template overlay. */
final class PreparedFunctionRegistry {

  private final Map<String, PreparedIrTemplate.PreparedFunction> prepared;
  private Map<String, PreparedIrTemplate.PreparedFunction> dynamic;

  PreparedFunctionRegistry(Map<String, PreparedIrTemplate.PreparedFunction> prepared) {
    this.prepared = Objects.requireNonNull(prepared, "prepared must not be null");
  }

  PreparedIrTemplate.PreparedFunction get(String name) {
    if (dynamic != null) {
      PreparedIrTemplate.PreparedFunction function = dynamic.get(name);
      if (function != null) {
        return function;
      }
    }
    return prepared.get(name);
  }

  void add(IrFunction function) {
    if (dynamic == null) {
      dynamic = new HashMap<>();
    }
    dynamic.put(
        function.name(),
        new PreparedIrTemplate.PreparedFunction(function, IrSlotLayout.layout(function)));
  }
}
