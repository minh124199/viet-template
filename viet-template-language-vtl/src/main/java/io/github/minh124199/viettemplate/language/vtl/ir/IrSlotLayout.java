package io.github.minh124199.viettemplate.language.vtl.ir;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrAlternateValue;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIndexGet;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrInvokeAllowedMethod;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrIsNull;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBranchIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopNext;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoopSetup;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Computes the exact semantic-variable slot array size required by lowered IR. */
public final class IrSlotLayout {

  public enum BindingKind {
    TEMPLATE_PARAMETER,
    TEMPLATE_LOCAL,
    FOREACH_ITEM,
    FOREACH_METADATA,
    FOREACH_LOCAL,
    MACRO_PARAMETER,
    MACRO_LOCAL
  }

  public enum InitializationPolicy {
    SEEDED_FROM_CONTEXT,
    FRESH_UNDEFINED,
    ITERATION_MANAGED,
    INVOCATION_PARAM
  }

  public record SlotMetadata(
      int slot, String name, BindingKind kind, InitializationPolicy policy, SourceSpan span) {
    public SlotMetadata {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(kind, "kind must not be null");
      Objects.requireNonNull(policy, "policy must not be null");
    }
  }

  public record SlotLayout(
      int frameSize,
      Map<Integer, SlotMetadata> slots,
      List<SlotMetadata> seededSlots,
      Map<IrLoop, List<Integer>> loopLocals) {
    public SlotLayout {
      slots = Map.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
      seededSlots =
          List.copyOf(Objects.requireNonNull(seededSlots, "seededSlots must not be null"));
      Map<IrLoop, List<Integer>> loopCopy = new LinkedHashMap<>();
      if (loopLocals != null) {
        for (Map.Entry<IrLoop, List<Integer>> entry : loopLocals.entrySet()) {
          loopCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
      }
      loopLocals = Collections.unmodifiableMap(loopCopy);
    }
  }

  private IrSlotLayout() {}

  public static SlotLayout layout(IrTemplate template) {
    Map<Integer, SlotMetadata> slots = new LinkedHashMap<>();
    List<SlotMetadata> seededSlots = new ArrayList<>();
    Map<IrLoop, List<Integer>> loopLocals = new LinkedHashMap<>();
    Set<Integer> outerSlots = new HashSet<>();

    for (IrParameter parameter : template.parameters()) {
      SlotMetadata meta =
          new SlotMetadata(
              parameter.slot(),
              parameter.name(),
              BindingKind.TEMPLATE_PARAMETER,
              InitializationPolicy.SEEDED_FROM_CONTEXT,
              parameter.span());
      slots.put(parameter.slot(), meta);
      seededSlots.add(meta);
      outerSlots.add(parameter.slot());
    }

    collectBlockSlots(template.root(), outerSlots, slots, seededSlots, loopLocals, true);

    int size = Math.max(frameSize(template), maxSlot(slots) + 1);
    return new SlotLayout(size, slots, seededSlots, loopLocals);
  }

  public static SlotLayout layout(IrFunction function) {
    Map<Integer, SlotMetadata> slots = new LinkedHashMap<>();
    List<SlotMetadata> seededSlots = new ArrayList<>();
    Map<IrLoop, List<Integer>> loopLocals = new LinkedHashMap<>();
    Set<Integer> outerSlots = new HashSet<>();

    for (IrParameter parameter : function.parameters()) {
      SlotMetadata meta =
          new SlotMetadata(
              parameter.slot(),
              parameter.name(),
              BindingKind.MACRO_PARAMETER,
              InitializationPolicy.INVOCATION_PARAM,
              parameter.span());
      slots.put(parameter.slot(), meta);
      outerSlots.add(parameter.slot());
    }

    for (IrLocal local : function.locals()) {
      if (!slots.containsKey(local.slot())) {
        SlotMetadata meta =
            new SlotMetadata(
                local.slot(),
                local.name(),
                BindingKind.MACRO_LOCAL,
                InitializationPolicy.FRESH_UNDEFINED,
                local.span());
        slots.put(local.slot(), meta);
      }
    }

    collectBlockSlots(function.body(), outerSlots, slots, seededSlots, loopLocals, false);

    int size = Math.max(frameSize(function), maxSlot(slots) + 1);
    return new SlotLayout(size, slots, seededSlots, loopLocals);
  }

  private static void collectBlockSlots(
      IrBlock block,
      Set<Integer> outerSlots,
      Map<Integer, SlotMetadata> slots,
      List<SlotMetadata> seededSlots,
      Map<IrLoop, List<Integer>> loopLocals,
      boolean isTemplateRoot) {
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrStoreLocal store) {
        int slot = store.local().slot();
        if (!slots.containsKey(slot)) {
          BindingKind kind = isTemplateRoot ? BindingKind.TEMPLATE_LOCAL : BindingKind.MACRO_LOCAL;
          InitializationPolicy policy =
              isTemplateRoot
                  ? InitializationPolicy.SEEDED_FROM_CONTEXT
                  : InitializationPolicy.FRESH_UNDEFINED;
          SlotMetadata meta =
              new SlotMetadata(slot, store.local().name(), kind, policy, store.local().span());
          slots.put(slot, meta);
          if (policy == InitializationPolicy.SEEDED_FROM_CONTEXT) {
            seededSlots.add(meta);
          }
          outerSlots.add(slot);
        }
      } else if (stmt instanceof IrIf conditional) {
        collectBlockSlots(
            conditional.thenBlock(), outerSlots, slots, seededSlots, loopLocals, isTemplateRoot);
        if (conditional.elseBlock().isPresent()) {
          collectBlockSlots(
              conditional.elseBlock().get(),
              outerSlots,
              slots,
              seededSlots,
              loopLocals,
              isTemplateRoot);
        }
      } else if (stmt instanceof IrLoop loop) {
        collectLoopSlots(loop, outerSlots, slots, seededSlots, loopLocals);
      }
    }
  }

  private static void collectLoopSlots(
      IrLoop loop,
      Set<Integer> enclosingOuterSlots,
      Map<Integer, SlotMetadata> slots,
      List<SlotMetadata> seededSlots,
      Map<IrLoop, List<Integer>> loopLocals) {
    int elemSlot = loop.elementLocal().slot();
    if (!slots.containsKey(elemSlot)) {
      SlotMetadata elemMeta =
          new SlotMetadata(
              elemSlot,
              loop.elementLocal().name(),
              BindingKind.FOREACH_ITEM,
              InitializationPolicy.ITERATION_MANAGED,
              loop.elementLocal().span());
      slots.put(elemSlot, elemMeta);
    }

    int stateSlot = -1;
    if (loop.loopStateLocal().isPresent()) {
      IrLocal sLocal = loop.loopStateLocal().get();
      stateSlot = sLocal.slot();
      if (!slots.containsKey(stateSlot)) {
        SlotMetadata stateMeta =
            new SlotMetadata(
                stateSlot,
                sLocal.name(),
                BindingKind.FOREACH_METADATA,
                InitializationPolicy.ITERATION_MANAGED,
                sLocal.span());
        slots.put(stateSlot, stateMeta);
      }
    }

    List<Integer> ownedSlots = new ArrayList<>();
    Set<Integer> loopInnerOuter = new HashSet<>(enclosingOuterSlots);
    loopInnerOuter.add(elemSlot);
    if (stateSlot != -1) {
      loopInnerOuter.add(stateSlot);
    }

    collectLoopBodySlots(loop.body(), loopInnerOuter, slots, ownedSlots, loopLocals);
    loopLocals.put(loop, ownedSlots);

    if (loop.elseBody().isPresent()) {
      collectBlockSlots(
          loop.elseBody().get(), enclosingOuterSlots, slots, seededSlots, loopLocals, false);
    }
  }

  private static void collectLoopBodySlots(
      IrBlock block,
      Set<Integer> outerSlots,
      Map<Integer, SlotMetadata> slots,
      List<Integer> ownedSlots,
      Map<IrLoop, List<Integer>> loopLocals) {
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrStoreLocal store) {
        int slot = store.local().slot();
        if (!outerSlots.contains(slot)) {
          if (!ownedSlots.contains(slot)) {
            ownedSlots.add(slot);
          }
          if (!slots.containsKey(slot)) {
            SlotMetadata meta =
                new SlotMetadata(
                    slot,
                    store.local().name(),
                    BindingKind.FOREACH_LOCAL,
                    InitializationPolicy.FRESH_UNDEFINED,
                    store.local().span());
            slots.put(slot, meta);
          }
        }
      } else if (stmt instanceof IrIf conditional) {
        collectLoopBodySlots(conditional.thenBlock(), outerSlots, slots, ownedSlots, loopLocals);
        if (conditional.elseBlock().isPresent()) {
          collectLoopBodySlots(
              conditional.elseBlock().get(), outerSlots, slots, ownedSlots, loopLocals);
        }
      } else if (stmt instanceof IrLoop innerLoop) {
        collectLoopSlots(innerLoop, outerSlots, slots, new ArrayList<>(), loopLocals);
        int innerElemSlot = innerLoop.elementLocal().slot();
        if (!outerSlots.contains(innerElemSlot) && !ownedSlots.contains(innerElemSlot)) {
          ownedSlots.add(innerElemSlot);
        }
        if (innerLoop.loopStateLocal().isPresent()) {
          int innerStateSlot = innerLoop.loopStateLocal().get().slot();
          if (!outerSlots.contains(innerStateSlot) && !ownedSlots.contains(innerStateSlot)) {
            ownedSlots.add(innerStateSlot);
          }
        }
        List<Integer> innerOwned = loopLocals.getOrDefault(innerLoop, List.of());
        for (int s : innerOwned) {
          if (!outerSlots.contains(s) && !ownedSlots.contains(s)) {
            ownedSlots.add(s);
          }
        }
      }
    }
  }

  private static int maxSlot(Map<Integer, SlotMetadata> slots) {
    int max = -1;
    for (int slot : slots.keySet()) {
      if (slot > max) {
        max = slot;
      }
    }
    return max;
  }

  public static int frameSize(IrTemplate template) {
    int max = maxParameters(template.parameters());
    return Math.max(max, maxBlock(template.root())) + 1;
  }

  public static int frameSize(IrFunction function) {
    int max = Math.max(maxParameters(function.parameters()), maxLocals(function.locals()));
    return Math.max(max, maxBlock(function.body())) + 1;
  }

  /** Returns slot-to-diagnostic-name mappings in deterministic lexical traversal order. */
  public static Map<Integer, String> bindings(IrTemplate template) {
    Map<Integer, String> bindings = new LinkedHashMap<>();
    for (IrParameter parameter : template.parameters()) {
      bindings.putIfAbsent(parameter.slot(), parameter.name());
    }
    collectBindings(template.root(), bindings);
    return Map.copyOf(bindings);
  }

  /** Returns slot-to-diagnostic-name mappings for one independently invoked function frame. */
  public static Map<Integer, String> bindings(IrFunction function) {
    Map<Integer, String> bindings = new LinkedHashMap<>();
    for (IrParameter parameter : function.parameters()) {
      bindings.putIfAbsent(parameter.slot(), parameter.name());
    }
    for (IrLocal local : function.locals()) bindings.putIfAbsent(local.slot(), local.name());
    collectBindings(function.body(), bindings);
    return Map.copyOf(bindings);
  }

  private static void collectBindings(IrBlock block, Map<Integer, String> bindings) {
    for (IrStatement statement : block.statements()) {
      if (statement instanceof IrStoreLocal store) {
        bindings.putIfAbsent(store.local().slot(), store.local().name());
        collectBindings(store.value(), bindings);
      } else if (statement instanceof IrWriteValue write) {
        collectBindings(write.value(), bindings);
      } else if (statement instanceof IrIf conditional) {
        collectBindings(conditional.condition(), bindings);
        collectBindings(conditional.thenBlock(), bindings);
        conditional.elseBlock().ifPresent(value -> collectBindings(value, bindings));
      } else if (statement instanceof IrLoop loop) {
        collectBindings(loop.iterable(), bindings);
        bindings.putIfAbsent(loop.elementLocal().slot(), loop.elementLocal().name());
        loop.loopStateLocal().ifPresent(local -> bindings.putIfAbsent(local.slot(), local.name()));
        collectBindings(loop.body(), bindings);
        loop.elseBody().ifPresent(value -> collectBindings(value, bindings));
      } else if (statement instanceof IrLoopSetup setup) {
        collectBindings(setup.iterable(), bindings);
        bindings.putIfAbsent(setup.iteratorLocal().slot(), setup.iteratorLocal().name());
      } else if (statement instanceof IrLoopNext next) {
        bindings.putIfAbsent(next.iteratorLocal().slot(), next.iteratorLocal().name());
        bindings.putIfAbsent(next.elementLocal().slot(), next.elementLocal().name());
        next.loopStateLocal().ifPresent(local -> bindings.putIfAbsent(local.slot(), local.name()));
      } else if (statement instanceof IrCallMacro call && call.bodyContent().isPresent()) {
        for (IrExpression argument : call.arguments()) collectBindings(argument, bindings);
        collectBindings(call.bodyContent().get(), bindings);
      } else if (statement instanceof IrCallMacro call) {
        for (IrExpression argument : call.arguments()) collectBindings(argument, bindings);
      } else if (statement instanceof IrCallTemplate call) {
        collectBindings(call.templateNameExpr(), bindings);
      } else if (statement instanceof IrEvaluate evaluate) {
        collectBindings(evaluate.expression(), bindings);
      } else if (statement instanceof IrSetProperty set) {
        collectBindings(set.target(), bindings);
        collectBindings(set.value(), bindings);
      } else if (statement instanceof IrSetIndex set) {
        collectBindings(set.target(), bindings);
        collectBindings(set.index(), bindings);
        collectBindings(set.value(), bindings);
      } else if (statement instanceof IrBranchIf branch) {
        collectBindings(branch.condition(), bindings);
      } else if (statement instanceof IrReturn ret && ret.value().isPresent()) {
        collectBindings(ret.value().get(), bindings);
      }
    }
  }

  private static void collectBindings(IrExpression expression, Map<Integer, String> bindings) {
    if (expression instanceof IrLoadLocal load) {
      bindings.putIfAbsent(load.slot(), load.name());
    } else if (expression instanceof IrLoadParam load) {
      bindings.putIfAbsent(load.slot(), load.name());
    } else if (expression instanceof IrGetProperty get) {
      collectBindings(get.receiver(), bindings);
    } else if (expression instanceof IrIndexGet get) {
      collectBindings(get.receiver(), bindings);
      collectBindings(get.index(), bindings);
    } else if (expression instanceof IrInvokeAllowedMethod call) {
      collectBindings(call.receiver(), bindings);
      for (IrExpression argument : call.arguments()) collectBindings(argument, bindings);
    } else if (expression instanceof IrDynamicDispatch call) {
      call.receiver().ifPresent(value -> collectBindings(value, bindings));
      for (IrExpression argument : call.arguments()) collectBindings(argument, bindings);
    } else if (expression instanceof IrBinaryOp binary) {
      collectBindings(binary.left(), bindings);
      collectBindings(binary.right(), bindings);
    } else if (expression instanceof IrUnaryOp unary) {
      collectBindings(unary.operand(), bindings);
    } else if (expression instanceof IrTruthiness truthiness) {
      collectBindings(truthiness.expression(), bindings);
    } else if (expression instanceof IrIsNull isNull) {
      collectBindings(isNull.expression(), bindings);
    } else if (expression instanceof IrConvert convert) {
      collectBindings(convert.expression(), bindings);
    } else if (expression instanceof IrAlternateValue alternate) {
      collectBindings(alternate.primary(), bindings);
      collectBindings(alternate.fallback(), bindings);
    }
  }

  private static int maxParameters(Iterable<IrParameter> parameters) {
    int max = -1;
    for (IrParameter parameter : parameters) {
      max = Math.max(max, parameter.slot());
      if (parameter.defaultValue().isPresent()) {
        max = Math.max(max, maxExpression(parameter.defaultValue().get()));
      }
    }
    return max;
  }

  private static int maxLocals(Iterable<IrLocal> locals) {
    int max = -1;
    for (IrLocal local : locals) {
      max = Math.max(max, local.slot());
    }
    return max;
  }

  private static int maxBlock(IrBlock block) {
    int max = -1;
    for (IrStatement statement : block.statements()) {
      max = Math.max(max, maxStatement(statement));
    }
    return max;
  }

  private static int maxStatement(IrStatement statement) {
    if (statement instanceof IrStoreLocal store) {
      return Math.max(store.local().slot(), maxExpression(store.value()));
    }
    if (statement instanceof IrWriteValue write) return maxExpression(write.value());
    if (statement instanceof IrIf conditional) {
      int max = Math.max(maxExpression(conditional.condition()), maxBlock(conditional.thenBlock()));
      return conditional
          .elseBlock()
          .map(IrSlotLayout::maxBlock)
          .map(v -> Math.max(max, v))
          .orElse(max);
    }
    if (statement instanceof IrLoop loop) {
      int max = Math.max(maxExpression(loop.iterable()), loop.elementLocal().slot());
      max = Math.max(max, loop.loopStateLocal().map(IrLocal::slot).orElse(-1));
      max = Math.max(max, maxBlock(loop.body()));
      if (loop.elseBody().isPresent()) max = Math.max(max, maxBlock(loop.elseBody().get()));
      return max;
    }
    if (statement instanceof IrLoopSetup setup) {
      return Math.max(setup.iteratorLocal().slot(), maxExpression(setup.iterable()));
    }
    if (statement instanceof IrLoopNext next) {
      int max = Math.max(next.iteratorLocal().slot(), next.elementLocal().slot());
      return Math.max(max, next.loopStateLocal().map(IrLocal::slot).orElse(-1));
    }
    if (statement instanceof IrCallMacro call) {
      int max = -1;
      for (IrExpression argument : call.arguments()) max = Math.max(max, maxExpression(argument));
      if (call.bodyContent().isPresent()) max = Math.max(max, maxBlock(call.bodyContent().get()));
      return max;
    }
    if (statement instanceof IrCallTemplate call) return maxExpression(call.templateNameExpr());
    if (statement instanceof IrEvaluate evaluate) return maxExpression(evaluate.expression());
    if (statement instanceof IrSetProperty set) {
      return Math.max(maxExpression(set.target()), maxExpression(set.value()));
    }
    if (statement instanceof IrSetIndex set) {
      return Math.max(
          maxExpression(set.target()),
          Math.max(maxExpression(set.index()), maxExpression(set.value())));
    }
    if (statement instanceof IrBranchIf branch) return maxExpression(branch.condition());
    if (statement instanceof IrReturn ret && ret.value().isPresent())
      return maxExpression(ret.value().get());
    return -1;
  }

  private static int maxExpression(IrExpression expression) {
    if (expression instanceof IrLoadLocal load) return load.slot();
    if (expression instanceof IrLoadParam load) return load.slot();
    if (expression instanceof IrGetProperty get) return maxExpression(get.receiver());
    if (expression instanceof IrIndexGet get)
      return Math.max(maxExpression(get.receiver()), maxExpression(get.index()));
    if (expression instanceof IrInvokeAllowedMethod call) {
      int max = maxExpression(call.receiver());
      for (IrExpression argument : call.arguments()) max = Math.max(max, maxExpression(argument));
      return max;
    }
    if (expression instanceof IrDynamicDispatch call) {
      int max = call.receiver().map(IrSlotLayout::maxExpression).orElse(-1);
      for (IrExpression argument : call.arguments()) max = Math.max(max, maxExpression(argument));
      return max;
    }
    if (expression instanceof IrBinaryOp binary)
      return Math.max(maxExpression(binary.left()), maxExpression(binary.right()));
    if (expression instanceof IrUnaryOp unary) return maxExpression(unary.operand());
    if (expression instanceof IrTruthiness truthiness)
      return maxExpression(truthiness.expression());
    if (expression instanceof IrIsNull isNull) return maxExpression(isNull.expression());
    if (expression instanceof IrConvert convert) return maxExpression(convert.expression());
    if (expression instanceof IrAlternateValue alternate) {
      return Math.max(maxExpression(alternate.primary()), maxExpression(alternate.fallback()));
    }
    return -1;
  }
}
