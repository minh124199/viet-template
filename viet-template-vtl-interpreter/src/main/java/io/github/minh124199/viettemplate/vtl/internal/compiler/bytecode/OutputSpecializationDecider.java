package io.github.minh124199.viettemplate.vtl.internal.compiler.bytecode;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.PrimitiveKind;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Authoritative decider for write output specialization (String/Integer fast paths vs generic write
 * fallback).
 *
 * <p>Shared between {@link BytecodeTemplateCompiler} and explanation/inspection engines to
 * guarantee zero divergence.
 */
public final class OutputSpecializationDecider {

  public static final String REJECTION_STRICT_REFERENCES_OR_ERROR_NULL_HANDLING =
      "STRICT_REFERENCES_OR_ERROR_NULL_HANDLING";
  public static final String REJECTION_VTL_SAFE_PROFILE = "VTL_SAFE_PROFILE";
  public static final String REJECTION_NULL_MODE_REQUIRES_GENERIC_PATH =
      "NULL_MODE_REQUIRES_GENERIC_PATH";
  public static final String REJECTION_UNTYPED_TEMPLATE = "UNTYPED_TEMPLATE";
  public static final String REJECTION_LOCAL_VARIABLE_NOT_PROVEN_INTEGER =
      "LOCAL_VARIABLE_NOT_PROVEN_INTEGER";
  public static final String REJECTION_STATIC_TYPE_NOT_STRING = "STATIC_TYPE_NOT_STRING";
  public static final String REJECTION_STATIC_TYPE_NOT_INTEGER = "STATIC_TYPE_NOT_INTEGER";
  public static final String REJECTION_STATIC_TYPE_UNKNOWN = "STATIC_TYPE_UNKNOWN";

  public static final String PATH_WRITE_STRING = "BytecodeRuntimeBridge.writeString";
  public static final String PATH_WRITE_INTEGER = "BytecodeRuntimeBridge.writeInteger";
  public static final String PATH_WRITE_VALUE = "BytecodeRuntimeBridge.writeValue";

  private OutputSpecializationDecider() {}

  /**
   * Decides the output write specialization dispatch path and collects rejection reasons if
   * fallback is chosen.
   *
   * @param expr the expression being rendered to output
   * @param nullMode null rendering policy
   * @param context compiler/specialization context
   * @return immutable {@link WriteDispatchDecision}
   */
  public static WriteDispatchDecision decide(
      IrExpression expr, NullRenderMode nullMode, OutputSpecializationContext context) {
    boolean isStrict =
        (context != null && context.isStrict()) || (nullMode == NullRenderMode.THROW_ERROR);
    boolean isSafeProfile = context != null && context.isSafeProfile();
    boolean isNullModeThrow = nullMode == NullRenderMode.THROW_ERROR;

    if (!isStrict && !isSafeProfile && !isNullModeThrow && isStaticString(expr)) {
      return new WriteDispatchDecision(
          WriteDispatchKind.WRITE_STRING_SPECIALIZED, PATH_WRITE_STRING, List.of());
    }

    if (!isStrict && !isSafeProfile && !isNullModeThrow && isStaticInt(expr, context)) {
      return new WriteDispatchDecision(
          WriteDispatchKind.WRITE_INTEGER_SPECIALIZED, PATH_WRITE_INTEGER, List.of());
    }

    List<String> rejections = new ArrayList<>();

    if (context != null && context.isStrict()) {
      rejections.add(REJECTION_STRICT_REFERENCES_OR_ERROR_NULL_HANDLING);
    }
    if (isNullModeThrow) {
      if (context == null || !context.isStrict()) {
        rejections.add(REJECTION_STRICT_REFERENCES_OR_ERROR_NULL_HANDLING);
      }
      rejections.add(REJECTION_NULL_MODE_REQUIRES_GENERIC_PATH);
    }
    if (isSafeProfile) {
      rejections.add(REJECTION_VTL_SAFE_PROFILE);
    }

    boolean isStaticStr = isStaticString(expr);
    boolean isStaticInteger = isStaticInt(expr, context);

    if (!isStaticStr && !isStaticInteger) {
      if (context != null && !context.isTyped()) {
        rejections.add(REJECTION_UNTYPED_TEMPLATE);
      }

      if (expr instanceof IrLoadLocal local
          && context != null
          && context.isNonIntLocal(local.slot())) {
        rejections.add(REJECTION_LOCAL_VARIABLE_NOT_PROVEN_INTEGER);
      }

      if (isSpecializationCandidate(expr)) {
        if (!isStaticStr) {
          rejections.add(REJECTION_STATIC_TYPE_NOT_STRING);
        }
        if (context == null || context.isTyped()) {
          if (expr instanceof IrLoadLocal local
              && context != null
              && context.isNonIntLocal(local.slot())) {
            // Already marked by LOCAL_VARIABLE_NOT_PROVEN_INTEGER
          } else if (!isStaticInteger) {
            rejections.add(REJECTION_STATIC_TYPE_NOT_INTEGER);
          }
        }
      } else {
        rejections.add(REJECTION_STATIC_TYPE_UNKNOWN);
      }
    }

    return new WriteDispatchDecision(
        WriteDispatchKind.GENERIC_WRITE_VALUE,
        PATH_WRITE_VALUE,
        Collections.unmodifiableList(rejections));
  }

  /** Checks if the given expression statically evaluates to a {@link String}. */
  public static boolean isStaticString(IrExpression expr) {
    if (expr == null) {
      return false;
    }
    if (expr instanceof IrConst c) {
      return c.value() instanceof String;
    }
    if (expr instanceof IrLoadParam param) {
      return isStringType(param.type());
    }
    if (expr instanceof IrLoadLocal local) {
      return isStringType(local.type());
    }
    if (expr instanceof IrGetProperty prop) {
      AccessPlan plan = prop.accessPlan();
      if (plan instanceof AccessPlan.DirectRecord rec) {
        return rec.returnType() == String.class;
      }
      if (plan instanceof AccessPlan.DirectGetter getter) {
        return getter.returnType() == String.class;
      }
      if (plan instanceof AccessPlan.DirectField field) {
        return field.fieldType() == String.class;
      }
      return false;
    }
    if (expr instanceof IrConvert conv) {
      return isStringType(conv.type());
    }
    return false;
  }

  /** Checks if the given expression statically evaluates to an {@code int} or {@link Integer}. */
  public static boolean isStaticInt(IrExpression expr) {
    return isStaticInt(expr, null);
  }

  /**
   * Checks if the given expression statically evaluates to an {@code int} or {@link Integer} under
   * the provided specialization context.
   */
  public static boolean isStaticInt(IrExpression expr, OutputSpecializationContext context) {
    if (expr == null) {
      return false;
    }
    if (context != null && !context.isTyped()) {
      return false;
    }
    if (expr instanceof IrConst c) {
      return c.value() instanceof Integer;
    }
    if (expr instanceof IrLoadParam param) {
      return isIntegerType(param.type());
    }
    if (expr instanceof IrLoadLocal local) {
      if (context != null && context.isNonIntLocal(local.slot())) {
        return false;
      }
      return isIntegerType(local.type());
    }
    if (expr instanceof IrGetProperty prop) {
      AccessPlan plan = prop.accessPlan();
      if (plan instanceof AccessPlan.DirectRecord rec) {
        return rec.returnType() == int.class || rec.returnType() == Integer.class;
      }
      if (plan instanceof AccessPlan.DirectGetter getter) {
        return getter.returnType() == int.class || getter.returnType() == Integer.class;
      }
      if (plan instanceof AccessPlan.DirectField field) {
        return field.fieldType() == int.class || field.fieldType() == Integer.class;
      }
      return false;
    }
    if (expr instanceof IrConvert conv) {
      return isIntegerType(conv.type());
    }
    return false;
  }

  /** Checks if the given semantic type represents {@link String}. */
  public static boolean isStringType(VType type) {
    if (type instanceof VType.ClassType ct) {
      if (ct.javaClass().isPresent()) {
        return ct.javaClass().get() == String.class;
      }
      return "java.lang.String".equals(ct.className());
    }
    return false;
  }

  /**
   * Checks if the given semantic type represents primitive {@code int} or boxed {@link Integer}.
   */
  public static boolean isIntegerType(VType type) {
    if (type == null) {
      return false;
    }
    if (type instanceof VType.PrimitiveType pt) {
      return pt.kind() == PrimitiveKind.INT;
    }
    if (type instanceof VType.ClassType ct) {
      if (ct.javaClass().isPresent()) {
        return ct.javaClass().get() == Integer.class || ct.javaClass().get() == int.class;
      }
      return "java.lang.Integer".equals(ct.className()) || "int".equals(ct.className());
    }
    return false;
  }

  /**
   * Scans IR statements for local variable assignments that are not proven ints and computes the
   * set of non-int local variable slots.
   *
   * @param template template IR
   * @param isTyped whether the template has known type contract / schema
   * @return set of local variable slots that cannot be proven integer
   */
  public static Set<Integer> computeNonIntLocalSlots(IrTemplate template, boolean isTyped) {
    if (!isTyped || template == null) {
      return Set.of();
    }
    Set<Integer> nonIntLocalSlots = new HashSet<>();
    OutputSpecializationContext scanningContext =
        new OutputSpecializationContext() {
          @Override
          public boolean isStrict() {
            return false;
          }

          @Override
          public boolean isSafeProfile() {
            return false;
          }

          @Override
          public boolean isTyped() {
            return true;
          }

          @Override
          public boolean isNonIntLocal(int slot) {
            return nonIntLocalSlots.contains(slot);
          }
        };

    int prevSize;
    do {
      prevSize = nonIntLocalSlots.size();
      scanNonIntLocals(template.root(), nonIntLocalSlots, scanningContext);
      for (IrFunction fn : template.functions()) {
        scanNonIntLocals(fn.body(), nonIntLocalSlots, scanningContext);
      }
    } while (nonIntLocalSlots.size() > prevSize);

    return Set.copyOf(nonIntLocalSlots);
  }

  private static void scanNonIntLocals(
      IrBlock block, Set<Integer> nonIntLocalSlots, OutputSpecializationContext context) {
    if (block == null) {
      return;
    }
    for (IrStatement stmt : block.statements()) {
      if (stmt instanceof IrStoreLocal sl) {
        if (!isStaticInt(sl.value(), context)) {
          nonIntLocalSlots.add(sl.local().slot());
        }
      } else if (stmt instanceof IrIf ifStmt) {
        scanNonIntLocals(ifStmt.thenBlock(), nonIntLocalSlots, context);
        ifStmt.elseBlock().ifPresent(b -> scanNonIntLocals(b, nonIntLocalSlots, context));
      } else if (stmt instanceof IrLoop loop) {
        scanNonIntLocals(loop.body(), nonIntLocalSlots, context);
        loop.elseBody().ifPresent(b -> scanNonIntLocals(b, nonIntLocalSlots, context));
      }
    }
  }

  private static boolean isSpecializationCandidate(IrExpression expr) {
    if (expr == null) {
      return false;
    }
    if (expr instanceof IrConst c) {
      return c.value() != null;
    }
    if (expr instanceof IrLoadParam param) {
      return isKnownConcreteType(param.type());
    }
    if (expr instanceof IrLoadLocal local) {
      return isKnownConcreteType(local.type());
    }
    if (expr instanceof IrGetProperty prop) {
      AccessPlan plan = prop.accessPlan();
      if (plan instanceof AccessPlan.DirectRecord rec) {
        return isKnownConcreteClass(rec.returnType());
      }
      if (plan instanceof AccessPlan.DirectGetter getter) {
        return isKnownConcreteClass(getter.returnType());
      }
      if (plan instanceof AccessPlan.DirectField field) {
        return isKnownConcreteClass(field.fieldType());
      }
      return false;
    }
    if (expr instanceof IrConvert conv) {
      return isKnownConcreteType(conv.type());
    }
    return false;
  }

  private static boolean isKnownConcreteType(VType type) {
    if (type == null || type.isDynamic() || type.isError() || type.isNull()) {
      return false;
    }
    if (type instanceof VType.ClassType ct) {
      if (ct.javaClass().isPresent() && ct.javaClass().get() == Object.class) {
        return false;
      }
      return !"java.lang.Object".equals(ct.className());
    }
    return true;
  }

  private static boolean isKnownConcreteClass(Class<?> clazz) {
    return clazz != null && clazz != Object.class;
  }
}
