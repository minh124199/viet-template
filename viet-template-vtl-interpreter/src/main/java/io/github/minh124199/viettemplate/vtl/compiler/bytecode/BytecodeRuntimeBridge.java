package io.github.minh124199.viettemplate.vtl.compiler.bytecode;

import io.github.minh124199.viettemplate.api.MutableRenderContext;
import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.api.TemplateLimitException;
import io.github.minh124199.viettemplate.api.TemplateOutput;
import io.github.minh124199.viettemplate.api.TemplateRenderException;
import io.github.minh124199.viettemplate.api.TemplateSecurityException;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.runtime.EscapeMode;
import io.github.minh124199.viettemplate.runtime.SafeHtml;
import io.github.minh124199.viettemplate.runtime.SafeUrl;
import io.github.minh124199.viettemplate.runtime.StandardEscapers;
import io.github.minh124199.viettemplate.runtime.Utf8OutputStreamTemplateOutput;
import io.github.minh124199.viettemplate.runtime.linker.DynamicCallSite;
import io.github.minh124199.viettemplate.runtime.linker.DynamicLinker;
import io.github.minh124199.viettemplate.runtime.linker.LinkerAccessPolicy;
import io.github.minh124199.viettemplate.runtime.linker.LinkerStatistics;
import io.github.minh124199.viettemplate.runtime.linker.MemberKey;
import io.github.minh124199.viettemplate.runtime.linker.MemberOperation;
import io.github.minh124199.viettemplate.vtl.interpreter.EvaluationValue;
import io.github.minh124199.viettemplate.vtl.interpreter.ForeachMetadata;
import io.github.minh124199.viettemplate.vtl.interpreter.InterpreterDiagnosticCodes;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlComparisonOperations;
import io.github.minh124199.viettemplate.vtl.interpreter.VtlNumericOperations;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Shared runtime bridge supporting generated template bytecode execution.
 *
 * <p>Provides standardized, low-overhead implementations of VTL semantic operations, contextual
 * escaping, dynamic PIC dispatch, truthiness checks, and execution limits.
 */
public final class BytecodeRuntimeBridge {

  private BytecodeRuntimeBridge() {}

  /** Writes a value expression to output, applying escaping and null rendering semantics. */
  public static void writeValue(
      Object val,
      TemplateOutput output,
      int escapeModeOrdinal,
      int nullModeOrdinal,
      String literal,
      boolean strict,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol)
      throws IOException {
    writeValue(
        val,
        output,
        escapeModeOrdinal,
        nullModeOrdinal,
        literal,
        strict,
        templateIdStr,
        startLine,
        startCol,
        endLine,
        endCol,
        null);
  }

  public static void writeValue(
      Object val,
      TemplateOutput output,
      int escapeModeOrdinal,
      int nullModeOrdinal,
      String literal,
      boolean strict,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol,
      LinkerAccessPolicy securityPolicy)
      throws IOException {
    TemplateId templateId = TemplateId.of(templateIdStr);
    SourceSpan span = makeSpan(startLine, startCol, endLine, endCol);
    NullRenderMode nullMode = NullRenderMode.values()[nullModeOrdinal];
    io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode irMode =
        io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode.values()[
            escapeModeOrdinal];
    EscapeMode escapeMode =
        switch (irMode) {
          case RAW -> EscapeMode.RAW;
          case HTML_TEXT -> EscapeMode.HTML_TEXT;
          case HTML_ATTRIBUTE_QUOTED -> EscapeMode.HTML_ATTRIBUTE_QUOTED;
          case URL_COMPONENT -> EscapeMode.URL_COMPONENT;
        };

    Object unwrapped = (val instanceof EvaluationValue ev) ? ev.value() : val;
    boolean isNullOrUndef =
        (val == null) || (val instanceof EvaluationValue ev && (ev.isNull() || ev.isUndefined()));

    if (strict) {
      if (val instanceof EvaluationValue ev && ev.isUndefined()) {
        throw new TemplateRenderException(
            "Variable '" + (literal != null ? literal : "$ref") + "' has not been set",
            templateId,
            span,
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
      if (isNullOrUndef) {
        if (nullMode == NullRenderMode.EMPTY_STRING) {
          return;
        }
        throw new TemplateRenderException(
            "Reference '"
                + (literal != null ? literal : "$ref")
                + "' evaluated to null when attempting to render",
            templateId,
            span,
            InterpreterDiagnosticCodes.VARIABLE_UNDEFINED);
      }
    }

    if (nullMode == NullRenderMode.EMPTY_STRING) {
      if (!isNullOrUndef) {
        renderEscaped(unwrapped, output, escapeMode, securityPolicy, templateId, span);
      }
      return;
    }

    if (isNullOrUndef) {
      if (literal != null && !literal.isEmpty()) {
        output.write(literal);
      }
    } else {
      renderEscaped(unwrapped, output, escapeMode, securityPolicy, templateId, span);
    }
  }

  /** Writes a text constant to the output, using UTF-8 fast-path where supported. */
  public static void writeConst(TemplateOutput output, String text, byte[] utf8Bytes)
      throws IOException {
    if (utf8Bytes != null && output instanceof Utf8OutputStreamTemplateOutput) {
      output.writeUtf8(utf8Bytes);
    } else {
      output.write(text);
    }
  }

  /**
   * Streams an object value to {@link TemplateOutput} respecting contextual escaping and safe
   * types.
   */
  public static void renderEscaped(Object value, TemplateOutput output, EscapeMode escapeMode)
      throws IOException {
    renderEscaped(
        value, output, escapeMode, null, TemplateId.of("<generated>"), SourceSpan.UNKNOWN);
  }

  public static void renderEscaped(
      Object value,
      TemplateOutput output,
      EscapeMode escapeMode,
      LinkerAccessPolicy securityPolicy,
      TemplateId templateId,
      SourceSpan span)
      throws IOException {
    if (value == null) {
      return;
    }
    if (value instanceof Integer i) {
      output.writeInt(i);
      return;
    } else if (value instanceof Long l) {
      output.writeLong(l);
      return;
    } else if (value instanceof Double d) {
      output.writeDouble(d);
      return;
    } else if (value instanceof Float f) {
      output.writeFloat(f);
      return;
    } else if (value instanceof Short s) {
      output.writeShort(s);
      return;
    } else if (value instanceof Byte b) {
      output.writeByte(b);
      return;
    } else if (value instanceof Boolean b) {
      output.writeBoolean(b);
      return;
    }

    // Safe content handling (context-specific: SafeHtml only in HTML_TEXT, SafeUrl only in
    // URL_COMPONENT)
    if (escapeMode == EscapeMode.HTML_TEXT) {
      if (value instanceof SafeHtml safe) {
        output.write(safe.content());
        return;
      }
    } else if (escapeMode == EscapeMode.URL_COMPONENT) {
      if (value instanceof SafeUrl safe) {
        output.write(safe.content());
        return;
      }
    }

    if (escapeMode != EscapeMode.RAW
        || (securityPolicy != null && securityPolicy.isSafeProfile())) {
      if (securityPolicy != null && !securityPolicy.isClassPermitted(value.getClass())) {
        throw new TemplateSecurityException(
            "Rendering class " + value.getClass().getName() + " is denied by security policy",
            templateId != null ? templateId : TemplateId.of("<generated>"),
            span != null ? span : SourceSpan.UNKNOWN,
            InterpreterDiagnosticCodes.SECURITY_VIOLATION);
      }
    }

    CharSequence cs = (value instanceof CharSequence seq) ? seq : String.valueOf(value);
    StandardEscapers.get(escapeMode).escape(cs, output);
  }

  /** Evaluates VTL truthiness of an expression. */
  public static boolean isTruthy(Object val, boolean emptyCheck) {
    if (val == null) {
      return false;
    }
    if (val instanceof EvaluationValue ev) {
      if (ev.isNull() || ev.isUndefined()) {
        return false;
      }
      val = ev.value();
      if (val == null) {
        return false;
      }
    }
    if (val instanceof Boolean b) {
      return b;
    }
    if (emptyCheck) {
      if (val instanceof CharSequence cs) {
        return cs.length() > 0;
      }
      if (val instanceof java.util.Collection<?> col) {
        return !col.isEmpty();
      }
      if (val instanceof java.util.Map<?, ?> map) {
        return !map.isEmpty();
      }
      if (val.getClass().isArray()) {
        return Array.getLength(val) > 0;
      }
    }
    return true;
  }

  /** Evaluates VTL alternate value: {@code $primary | 'fallback'}. */
  public static Object alternateValue(Object primary, Object fallback) {
    return isTruthy(primary, true) ? primary : fallback;
  }

  /** Evaluates a binary operation. */
  public static Object binaryOp(
      Object left,
      Object right,
      int opOrdinal,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol) {
    return binaryOp(
        left, right, opOrdinal, templateIdStr, startLine, startCol, endLine, endCol, null);
  }

  public static Object binaryOp(
      Object left,
      Object right,
      int opOrdinal,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol,
      LinkerAccessPolicy securityPolicy) {
    BinaryOpKind op = BinaryOpKind.values()[opOrdinal];
    TemplateId templateId = TemplateId.of(templateIdStr);
    SourceSpan span = makeSpan(startLine, startCol, endLine, endCol);

    Object unwrappedLeft = (left instanceof EvaluationValue ev) ? ev.value() : left;
    Object unwrappedRight = (right instanceof EvaluationValue ev) ? ev.value() : right;

    if (op == BinaryOpKind.ADD
        && (unwrappedLeft instanceof String || unwrappedRight instanceof String)) {
      return String.valueOf(unwrappedLeft != null ? unwrappedLeft : "")
          + String.valueOf(unwrappedRight != null ? unwrappedRight : "");
    }

    return switch (op) {
      case ADD -> VtlNumericOperations.add(unwrappedLeft, unwrappedRight, span, templateId);
      case SUBTRACT ->
          VtlNumericOperations.subtract(unwrappedLeft, unwrappedRight, span, templateId);
      case MULTIPLY ->
          VtlNumericOperations.multiply(unwrappedLeft, unwrappedRight, span, templateId);
      case DIVIDE -> VtlNumericOperations.divide(unwrappedLeft, unwrappedRight, span, templateId);
      case REMAINDER ->
          VtlNumericOperations.remainder(unwrappedLeft, unwrappedRight, span, templateId);
      case EQUALS -> VtlComparisonOperations.equals(unwrappedLeft, unwrappedRight, securityPolicy);
      case NOT_EQUALS ->
          !VtlComparisonOperations.equals(unwrappedLeft, unwrappedRight, securityPolicy);
      case LESS_THAN ->
          VtlComparisonOperations.compare(
                  unwrappedLeft, unwrappedRight, span, templateId, securityPolicy)
              < 0;
      case LESS_THAN_OR_EQUAL ->
          VtlComparisonOperations.compare(
                  unwrappedLeft, unwrappedRight, span, templateId, securityPolicy)
              <= 0;
      case GREATER_THAN ->
          VtlComparisonOperations.compare(
                  unwrappedLeft, unwrappedRight, span, templateId, securityPolicy)
              > 0;
      case GREATER_THAN_OR_EQUAL ->
          VtlComparisonOperations.compare(
                  unwrappedLeft, unwrappedRight, span, templateId, securityPolicy)
              >= 0;
      case AND -> isTruthy(unwrappedLeft, true) && isTruthy(unwrappedRight, true);
      case OR -> isTruthy(unwrappedLeft, true) || isTruthy(unwrappedRight, true);
    };
  }

  /** Evaluates a unary operation. */
  public static Object unaryOp(
      Object operand,
      int opOrdinal,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol) {
    UnaryOpKind op = UnaryOpKind.values()[opOrdinal];
    TemplateId templateId = TemplateId.of(templateIdStr);
    SourceSpan span = makeSpan(startLine, startCol, endLine, endCol);
    Object unwrapped = (operand instanceof EvaluationValue ev) ? ev.value() : operand;

    return switch (op) {
      case NOT -> !isTruthy(unwrapped, true);
      case NEGATE -> VtlNumericOperations.negate(unwrapped, span, templateId);
    };
  }

  /** Evaluates an integer range list: {@code [start..end]}. */
  public static List<Integer> range(
      Object left,
      Object right,
      int maxRangeSize,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol) {
    TemplateId templateId = TemplateId.of(templateIdStr);
    SourceSpan span = makeSpan(startLine, startCol, endLine, endCol);

    Object unwrappedLeft = (left instanceof EvaluationValue ev) ? ev.value() : left;
    Object unwrappedRight = (right instanceof EvaluationValue ev) ? ev.value() : right;

    if (!(unwrappedLeft instanceof Number startNum) || !(unwrappedRight instanceof Number endNum)) {
      throw new TemplateRenderException(
          "Expected integer in range endpoint, but was: [" + left + ".." + right + "]",
          templateId,
          span,
          InterpreterDiagnosticCodes.SYNTAX_ERROR);
    }
    int start = startNum.intValue();
    int end = endNum.intValue();
    int size = Math.abs(end - start) + 1;
    if (size > maxRangeSize) {
      throw new TemplateLimitException(
          "Range size exceeds maximum limit (" + size + " > " + maxRangeSize + ")",
          templateId,
          span,
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }
    List<Integer> list = new ArrayList<>(size);
    if (start <= end) {
      for (int i = start; i <= end; i++) {
        list.add(i);
      }
    } else {
      for (int i = start; i >= end; i--) {
        list.add(i);
      }
    }
    return list;
  }

  /** Converts an arbitrary iterable, collection, map, or array to a uniform {@link Iterator}. */
  public static Iterator<?> toIterator(Object collection) {
    return toIterator(collection, null, null, 1, 1, 1, 1);
  }

  public static Iterator<?> toIterator(
      Object collection,
      LinkerAccessPolicy securityPolicy,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol) {
    if (collection == null) {
      return Collections.emptyIterator();
    }
    if (collection instanceof EvaluationValue ev) {
      if (ev.isNull() || ev.isUndefined()) {
        return Collections.emptyIterator();
      }
      collection = ev.value();
      if (collection == null) {
        return Collections.emptyIterator();
      }
    }
    if (securityPolicy != null
        && securityPolicy.isSafeProfile()
        && !securityPolicy.isClassPermitted(collection.getClass())) {
      TemplateId templateId =
          templateIdStr != null ? TemplateId.of(templateIdStr) : TemplateId.of("<generated>");
      SourceSpan span = makeSpan(startLine, startCol, endLine, endCol);
      throw new TemplateSecurityException(
          "Access to class "
              + collection.getClass().getName()
              + " in loop is denied by security policy",
          templateId,
          span,
          InterpreterDiagnosticCodes.SECURITY_VIOLATION);
    }
    if (collection instanceof Iterable<?> iterable) {
      return iterable.iterator();
    }
    if (collection instanceof Iterator<?> iterator) {
      return iterator;
    }
    if (collection instanceof Map<?, ?> map) {
      return map.values().iterator();
    }
    if (collection.getClass().isArray()) {
      int len = Array.getLength(collection);
      List<Object> list = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        list.add(Array.get(collection, i));
      }
      return list.iterator();
    }
    return Collections.singleton(collection).iterator();
  }

  /** Creates initial loop state counter. */
  public static Object createLoopState() {
    return new int[1];
  }

  /** Creates ForeachMetadata for loop state tracking from mutable counter array. */
  public static io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata
      createForeachMetadata(Object counter, boolean hasNext, Object parent) {
    int index = 0;
    if (counter instanceof int[] arr && arr.length > 0) {
      index = arr[0]++;
    }
    int count = index + 1;
    boolean first = (index == 0);
    boolean last = !hasNext;
    ForeachMetadata parentMeta = (parent instanceof ForeachMetadata fm) ? fm : null;
    return new ForeachMetadata(index, count, first, last, hasNext, parentMeta);
  }

  /** Creates ForeachMetadata for loop state tracking. */
  public static io.github.minh124199.viettemplate.language.vtl.semantics.scope.ForeachMetadata
      createForeachMetadata(int index, boolean hasNext, Object parent) {
    int count = index + 1;
    boolean first = (index == 0);
    boolean last = !hasNext;
    ForeachMetadata parentMeta = (parent instanceof ForeachMetadata fm) ? fm : null;
    return new ForeachMetadata(index, count, first, last, hasNext, parentMeta);
  }

  /** Checks loop iteration budget limit. */
  public static void checkLoopIterations(
      int currentIteration,
      int maxIterations,
      String templateIdStr,
      int startLine,
      int startCol,
      int endLine,
      int endCol) {
    if (currentIteration > maxIterations) {
      throw new TemplateLimitException(
          "Exceeded maximum foreach iterations: " + maxIterations,
          TemplateId.of(templateIdStr),
          makeSpan(startLine, startCol, endLine, endCol),
          InterpreterDiagnosticCodes.LIMIT_EXCEEDED);
    }
  }

  /** Dispatches dynamic property read through M9 {@link DynamicCallSite}. */
  public static Object dynamicGetProperty(DynamicCallSite site, Object target) {
    if (target == null) {
      return null;
    }
    Object unwrapped = (target instanceof EvaluationValue ev) ? ev.value() : target;
    if (unwrapped == null) {
      return null;
    }
    try {
      return site.invoke(unwrapped);
    } catch (Throwable t) {
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(t);
    }
  }

  /** Dispatches dynamic property write through M9 {@link DynamicCallSite}. */
  public static void dynamicSetProperty(DynamicCallSite site, Object target, Object value) {
    if (target == null) {
      return;
    }
    Object unwrapped = (target instanceof EvaluationValue ev) ? ev.value() : target;
    if (unwrapped == null) {
      return;
    }
    try {
      site.invoke(unwrapped, value);
    } catch (Throwable t) {
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(t);
    }
  }

  /** Dispatches dynamic indexed read through M9 {@link DynamicCallSite}. */
  public static Object dynamicGetIndex(DynamicCallSite site, Object target, Object index) {
    if (target == null) {
      return null;
    }
    Object unwrapped = (target instanceof EvaluationValue ev) ? ev.value() : target;
    if (unwrapped == null) {
      return null;
    }
    try {
      return site.invoke(unwrapped, index);
    } catch (Throwable t) {
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(t);
    }
  }

  /** Dispatches dynamic indexed write through M9 {@link DynamicCallSite}. */
  public static void dynamicSetIndex(
      DynamicCallSite site, Object target, Object index, Object value) {
    if (target == null) {
      return;
    }
    Object unwrapped = (target instanceof EvaluationValue ev) ? ev.value() : target;
    if (unwrapped == null) {
      return;
    }
    try {
      site.invoke(unwrapped, index, value);
    } catch (Throwable t) {
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(t);
    }
  }

  /** Dispatches dynamic method invocation through M9 {@link DynamicCallSite}. */
  public static Object dynamicInvokeMethod(DynamicCallSite site, Object target, Object[] args) {
    if (target == null) {
      return null;
    }
    Object unwrapped = (target instanceof EvaluationValue ev) ? ev.value() : target;
    if (unwrapped == null) {
      return null;
    }
    try {
      return site.invokeWithArgs(unwrapped, args);
    } catch (ClassCastException | java.lang.invoke.WrongMethodTypeException cce) {
      Class<?> clazz = unwrapped.getClass();
      String methodName = site.memberKey().name();
      int arity = args != null ? args.length : 0;
      for (java.lang.reflect.Method m : clazz.getMethods()) {
        if (m.getName().equals(methodName) && m.getParameterCount() == arity) {
          boolean matches = true;
          Class<?>[] ptypes = m.getParameterTypes();
          for (int i = 0; i < arity; i++) {
            Object arg = args[i];
            if (arg != null && !isAssignable(ptypes[i], arg.getClass())) {
              matches = false;
              break;
            }
          }
          if (matches) {
            if (!site.policy().isMethodPermitted(clazz, m)) {
              throw new TemplateSecurityException(
                  "Access to "
                      + methodName
                      + " on "
                      + clazz.getName()
                      + " is denied by security policy: method "
                      + methodName
                      + " is denied by policy",
                  TemplateId.of("<generated>"),
                  SourceSpan.UNKNOWN,
                  InterpreterDiagnosticCodes.SECURITY_VIOLATION);
            }
            try {
              m.setAccessible(true);
              return m.invoke(unwrapped, args);
            } catch (Exception ex) {
              throw new RuntimeException(ex);
            }
          }
        }
      }
      throw cce;
    } catch (Throwable t) {
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(t);
    }
  }

  private static boolean isAssignable(Class<?> targetType, Class<?> argType) {
    if (targetType.isAssignableFrom(argType)) {
      return true;
    }
    if (targetType.isPrimitive()) {
      if (targetType == boolean.class && argType == Boolean.class) return true;
      if (targetType == byte.class && argType == Byte.class) return true;
      if (targetType == short.class && argType == Short.class) return true;
      if (targetType == char.class && argType == Character.class) return true;
      if (targetType == int.class && argType == Integer.class) return true;
      if (targetType == long.class && argType == Long.class) return true;
      if (targetType == float.class && argType == Float.class) return true;
      if (targetType == double.class && argType == Double.class) return true;
    }
    return false;
  }

  /** Factory method to create a dynamic call site linked to policy. */
  public static DynamicCallSite createCallSite(
      int id, String name, int operationOrdinal, int arity, LinkerAccessPolicy policy) {
    MemberOperation op = MemberOperation.values()[operationOrdinal];
    MemberKey key = new MemberKey(op, name, arity);
    DynamicLinker linker = new DynamicLinker(policy);
    return new DynamicCallSite(id, key, policy, linker, new LinkerStatistics());
  }

  private static SourceSpan makeSpan(int startLine, int startCol, int endLine, int endCol) {
    if (startLine < 1 || startCol < 1 || endLine < 1 || endCol < 1) {
      return SourceSpan.UNKNOWN;
    }
    if (startLine > endLine || (startLine == endLine && startCol > endCol)) {
      return SourceSpan.UNKNOWN;
    }
    return SourceSpan.of(0, 0, startLine, startCol, endLine, endCol);
  }

  /**
   * Records a template-level variable assignment back into the execution context if the supplied
   * context is mutable (e.g. during shared layout rendering).
   */
  public static void recordContextVariable(RenderContext context, String name, Object value) {
    if (context instanceof MutableRenderContext mrc && name != null) {
      Object unwrapped = (value instanceof EvaluationValue ev) ? ev.value() : value;
      mrc.put(name, unwrapped);
    }
  }

  /** Increments loop iteration count on the output's render budget if present. */
  public static void countLoopIteration(TemplateOutput output) {
    if (output
        instanceof io.github.minh124199.viettemplate.vtl.interpreter.CountingTemplateOutput cto) {
      cto.budget().countLoopIteration();
    }
  }
}
