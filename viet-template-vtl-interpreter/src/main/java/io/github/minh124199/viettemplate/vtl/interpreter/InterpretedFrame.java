package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Execution frame for interpreting intermediate representation (IR) templates.
 *
 * <p>Holds allocated local variable slots, runtime execution context, streaming output destination,
 * constant pool, function definitions, and recursion limit counters.
 */
final class InterpretedFrame {

  final ExecutionFrame variables;
  final ExecutionContext context;
  final TemplateOutput output;
  final IrConstantPool constantPool;
  final Map<String, IrFunction> functions;
  final TemplateId templateId;
  final SourceText source;
  final VtlInterpreterOptions options;
  final ReferenceAccess referenceAccess;
  final int macroDepth;
  final int parseDepth;
  final int evaluateDepth;
  final IrSlotLayout.SlotLayout layout;

  InterpretedFrame(
      TemplateId templateId,
      SourceText source,
      ExecutionContext context,
      TemplateOutput output,
      IrConstantPool constantPool,
      Map<String, IrFunction> functions,
      VtlInterpreterOptions options,
      ReferenceAccess referenceAccess,
      int macroDepth,
      int parseDepth,
      int evaluateDepth,
      int slotCount) {
    this(
        new ExecutionFrame(slotCount),
        context,
        output,
        constantPool,
        functions,
        templateId,
        source,
        options,
        referenceAccess,
        macroDepth,
        parseDepth,
        evaluateDepth,
        null);
  }

  InterpretedFrame(
      TemplateId templateId,
      SourceText source,
      ExecutionContext context,
      TemplateOutput output,
      IrConstantPool constantPool,
      Map<String, IrFunction> functions,
      VtlInterpreterOptions options,
      ReferenceAccess referenceAccess,
      int macroDepth,
      int parseDepth,
      int evaluateDepth,
      IrSlotLayout.SlotLayout layout) {
    this(
        new ExecutionFrame(layout != null ? layout.frameSize() : 0),
        context,
        output,
        constantPool,
        functions,
        templateId,
        source,
        options,
        referenceAccess,
        macroDepth,
        parseDepth,
        evaluateDepth,
        layout);
  }

  InterpretedFrame(
      ExecutionFrame variables,
      ExecutionContext context,
      TemplateOutput output,
      IrConstantPool constantPool,
      Map<String, IrFunction> functions,
      TemplateId templateId,
      SourceText source,
      VtlInterpreterOptions options,
      ReferenceAccess referenceAccess,
      int macroDepth,
      int parseDepth,
      int evaluateDepth) {
    this(
        variables,
        context,
        output,
        constantPool,
        functions,
        templateId,
        source,
        options,
        referenceAccess,
        macroDepth,
        parseDepth,
        evaluateDepth,
        null);
  }

  InterpretedFrame(
      ExecutionFrame variables,
      ExecutionContext context,
      TemplateOutput output,
      IrConstantPool constantPool,
      Map<String, IrFunction> functions,
      TemplateId templateId,
      SourceText source,
      VtlInterpreterOptions options,
      ReferenceAccess referenceAccess,
      int macroDepth,
      int parseDepth,
      int evaluateDepth,
      IrSlotLayout.SlotLayout layout) {
    this.variables = Objects.requireNonNull(variables, "variables must not be null");
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.output = Objects.requireNonNull(output, "output must not be null");
    this.constantPool = Objects.requireNonNull(constantPool, "constantPool must not be null");
    this.functions = Objects.requireNonNull(functions, "functions must not be null");
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.options = Objects.requireNonNull(options, "options must not be null");
    this.referenceAccess =
        Objects.requireNonNull(referenceAccess, "referenceAccess must not be null");
    this.macroDepth = macroDepth;
    this.parseDepth = parseDepth;
    this.evaluateDepth = evaluateDepth;
    this.layout = layout;
  }

  EvaluationValue getLocal(int slot) {
    return variables.get(slot);
  }

  void seedLocal(int slot, EvaluationValue value) {
    variables.seed(slot, value);
  }

  void setLocal(int slot, String name, Object value) {
    EvaluationValue evaluationValue = EvaluationValue.of(value);
    variables.set(slot, evaluationValue);
    if (name != null) {
      if (layout != null) {
        IrSlotLayout.SlotMetadata meta = layout.slots().get(slot);
        if (meta != null
            && (meta.kind() == IrSlotLayout.BindingKind.MACRO_LOCAL
                || meta.kind() == IrSlotLayout.BindingKind.MACRO_PARAMETER
                || meta.kind() == IrSlotLayout.BindingKind.FOREACH_LOCAL
                || meta.kind() == IrSlotLayout.BindingKind.FOREACH_ITEM
                || meta.kind() == IrSlotLayout.BindingKind.FOREACH_METADATA)) {
          context.setLocalScope(name, evaluationValue);
          return;
        }
      }
      context.set(name, evaluationValue);
    }
  }

  void syncFromContext(Collection<IrSlotLayout.SlotMetadata> slots) {
    if (slots == null) {
      return;
    }
    for (IrSlotLayout.SlotMetadata meta : slots) {
      EvaluationValue val = context.lookup(meta.name());
      if (val.isDefined()) {
        variables.set(meta.slot(), val);
      }
    }
  }

  InterpretedFrame withContext(ExecutionContext newContext) {
    return new InterpretedFrame(
        variables,
        newContext,
        output,
        constantPool,
        functions,
        templateId,
        source,
        options,
        referenceAccess,
        macroDepth,
        parseDepth,
        evaluateDepth,
        layout);
  }

  InterpretedFrame withMacroDepth(
      int newMacroDepth, ExecutionFrame newVariables, IrSlotLayout.SlotLayout newLayout) {
    return new InterpretedFrame(
        newVariables,
        context,
        output,
        constantPool,
        functions,
        templateId,
        source,
        options,
        referenceAccess,
        newMacroDepth,
        parseDepth,
        evaluateDepth,
        newLayout);
  }

  InterpretedFrame withMacroDepth(int newMacroDepth, ExecutionFrame newVariables) {
    return withMacroDepth(newMacroDepth, newVariables, layout);
  }

  InterpretedFrame withParseDepth(
      int newParseDepth,
      TemplateId newId,
      SourceText newSource,
      IrConstantPool newPool,
      IrSlotLayout.SlotLayout newLayout) {
    return new InterpretedFrame(
        newId,
        newSource,
        context,
        output,
        newPool,
        functions,
        options,
        referenceAccess,
        macroDepth,
        newParseDepth,
        evaluateDepth,
        newLayout);
  }

  InterpretedFrame withParseDepth(
      int newParseDepth,
      TemplateId newId,
      SourceText newSource,
      IrConstantPool newPool,
      int slotCount) {
    return new InterpretedFrame(
        new ExecutionFrame(slotCount),
        context,
        output,
        newPool,
        functions,
        newId,
        newSource,
        options,
        referenceAccess,
        macroDepth,
        newParseDepth,
        evaluateDepth,
        null);
  }

  InterpretedFrame withEvaluateDepth(
      int newEvaluateDepth,
      TemplateId newId,
      SourceText newSource,
      IrConstantPool newPool,
      IrSlotLayout.SlotLayout newLayout) {
    return new InterpretedFrame(
        newId,
        newSource,
        context,
        output,
        newPool,
        functions,
        options,
        referenceAccess,
        macroDepth,
        parseDepth,
        newEvaluateDepth,
        newLayout);
  }

  InterpretedFrame withEvaluateDepth(
      int newEvaluateDepth,
      TemplateId newId,
      SourceText newSource,
      IrConstantPool newPool,
      int slotCount) {
    return new InterpretedFrame(
        new ExecutionFrame(slotCount),
        context,
        output,
        newPool,
        functions,
        newId,
        newSource,
        options,
        referenceAccess,
        macroDepth,
        parseDepth,
        newEvaluateDepth,
        null);
  }
}
