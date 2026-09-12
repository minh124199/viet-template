package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * O45: freezes deterministic semantic-variable slots assigned during lexical lowering.
 *
 * <p>The lowerer assigns a unique monotonically increasing slot to each lexical binding. This
 * mandatory pass establishes that mapping as the backend contract and rejects malformed slot
 * declarations before optional optimizations can reach an execution backend. Slots are stable and
 * are intentionally not reused or lifetime-packed.
 */
public final class AssignVariableSlots implements IrOptimizationPass {

  @Override
  public String name() {
    return "AssignVariableSlots";
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    IrSlotLayout.SlotLayout templateLayout = IrSlotLayout.layout(template);
    validateDeclarations("template", template.parameters(), java.util.List.of());
    for (IrSlotLayout.SlotMetadata meta : templateLayout.slots().values()) {
      if (meta.slot() < 0) {
        throw new IllegalStateException(
            "template binding '" + meta.name() + "' has negative slot " + meta.slot());
      }
    }
    for (IrFunction function : template.functions()) {
      IrSlotLayout.SlotLayout fnLayout = IrSlotLayout.layout(function);
      validateDeclarations(function.name(), function.parameters(), function.locals());
      for (IrSlotLayout.SlotMetadata meta : fnLayout.slots().values()) {
        if (meta.slot() < 0) {
          throw new IllegalStateException(
              function.name() + " binding '" + meta.name() + "' has negative slot " + meta.slot());
        }
      }
    }
    return template;
  }

  private static void validateDeclarations(
      String owner, Iterable<IrParameter> parameters, Iterable<IrLocal> locals) {
    Map<Integer, String> bindings = new HashMap<>();
    for (IrParameter parameter : parameters) {
      record(owner, bindings, parameter.slot(), parameter.name());
    }
    for (IrLocal local : locals) {
      record(owner, bindings, local.slot(), local.name());
    }
  }

  private static void record(String owner, Map<Integer, String> bindings, int slot, String name) {
    if (slot < 0)
      throw new IllegalStateException(owner + " binding '" + name + "' has negative slot " + slot);
    String previous = bindings.putIfAbsent(slot, name);
    if (previous != null && !previous.equals(name)) {
      throw new IllegalStateException(
          owner + " slot " + slot + " aliases bindings '" + previous + "' and '" + name + "'");
    }
  }
}
