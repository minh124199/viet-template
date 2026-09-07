package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Execution frame for interpreting intermediate representation (IR) templates.
 *
 * <p>Holds allocated local variable slots, runtime execution context, streaming output destination,
 * constant pool, function definitions, and recursion limit counters.
 */
final class InterpretedFrame {

  Object[] locals;
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
      int evaluateDepth) {
    this(
        new Object[64],
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
        evaluateDepth);
  }

  InterpretedFrame(
      Object[] locals,
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
    this.locals = Objects.requireNonNull(locals, "locals must not be null");
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
  }

  void ensureCapacity(int slot) {
    if (slot >= locals.length) {
      int newSize = Math.max(slot + 16, locals.length * 2);
      locals = Arrays.copyOf(locals, newSize);
    }
  }

  Object getLocal(int slot) {
    if (slot >= 0 && slot < locals.length) {
      return locals[slot];
    }
    return null;
  }

  void setLocal(int slot, String name, Object value) {
    ensureCapacity(slot);
    locals[slot] = value;
    if (name != null) {
      context.set(name, EvaluationValue.of(value));
    }
  }

  InterpretedFrame withContext(ExecutionContext newContext) {
    return new InterpretedFrame(
        locals,
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
        evaluateDepth);
  }

  InterpretedFrame withMacroDepth(int newMacroDepth, Object[] newLocals) {
    return new InterpretedFrame(
        newLocals,
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
        evaluateDepth);
  }

  InterpretedFrame withParseDepth(
      int newParseDepth, TemplateId newId, SourceText newSource, IrConstantPool newPool) {
    return new InterpretedFrame(
        new Object[64],
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
        evaluateDepth);
  }

  InterpretedFrame withEvaluateDepth(
      int newEvaluateDepth, TemplateId newId, SourceText newSource, IrConstantPool newPool) {
    return new InterpretedFrame(
        new Object[64],
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
        newEvaluateDepth);
  }
}
