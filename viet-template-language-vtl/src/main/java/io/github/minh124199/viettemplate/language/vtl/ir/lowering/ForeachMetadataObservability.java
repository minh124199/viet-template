package io.github.minh124199.viettemplate.language.vtl.ir.lowering;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrAlternateValue;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
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
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetIndex;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrSetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;

/** Conservative compile-time proof for whether one loop's immutable metadata can be observed. */
final class ForeachMetadataObservability {

  private ForeachMetadataObservability() {}

  static boolean isRequired(IrBlock body, int metadataSlot) {
    for (IrStatement statement : body.statements()) {
      if (isRequired(statement, metadataSlot)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRequired(IrStatement statement, int metadataSlot) {
    return switch (statement) {
      case IrStoreLocal store -> references(store.value(), metadataSlot);
      case IrWriteValue write -> references(write.value(), metadataSlot);
      case IrIf conditional ->
          references(conditional.condition(), metadataSlot)
              || isRequired(conditional.thenBlock(), metadataSlot)
              || conditional
                  .elseBlock()
                  .map(block -> isRequired(block, metadataSlot))
                  .orElse(false);
      case IrLoop nested ->
          references(nested.iterable(), metadataSlot)
              || nested.loopStateLocal().isPresent()
              || isRequired(nested.body(), metadataSlot)
              || nested.elseBody().map(block -> isRequired(block, metadataSlot)).orElse(false);
      // These operations can observe the caller's lexical bindings by name.
      case IrEvaluate ignored -> true;
      case IrCallMacro ignored -> true;
      case IrCallTemplate call ->
          call.isParse() || references(call.templateNameExpr(), metadataSlot);
      case IrSetProperty set ->
          references(set.target(), metadataSlot) || references(set.value(), metadataSlot);
      case IrSetIndex set ->
          references(set.target(), metadataSlot)
              || references(set.index(), metadataSlot)
              || references(set.value(), metadataSlot);
      case IrBranchIf branch -> references(branch.condition(), metadataSlot);
      case IrReturn ret -> ret.value().map(value -> references(value, metadataSlot)).orElse(false);
      default -> false;
    };
  }

  private static boolean references(IrExpression expression, int metadataSlot) {
    return switch (expression) {
      case IrLoadLocal load -> load.slot() == metadataSlot;
      case IrGetProperty get -> references(get.receiver(), metadataSlot);
      case IrIndexGet get ->
          references(get.receiver(), metadataSlot) || references(get.index(), metadataSlot);
      case IrInvokeAllowedMethod call ->
          references(call.receiver(), metadataSlot)
              || call.arguments().stream().anyMatch(argument -> references(argument, metadataSlot));
      case IrDynamicDispatch call ->
          call.receiver().map(receiver -> references(receiver, metadataSlot)).orElse(false)
              || call.arguments().stream().anyMatch(argument -> references(argument, metadataSlot));
      case IrBinaryOp binary ->
          references(binary.left(), metadataSlot) || references(binary.right(), metadataSlot);
      case IrUnaryOp unary -> references(unary.operand(), metadataSlot);
      case IrTruthiness truthiness -> references(truthiness.expression(), metadataSlot);
      case IrIsNull isNull -> references(isNull.expression(), metadataSlot);
      case IrConvert convert -> references(convert.expression(), metadataSlot);
      case IrAlternateValue alternate ->
          references(alternate.primary(), metadataSlot)
              || references(alternate.fallback(), metadataSlot);
      case IrConst ignored -> false;
      case IrLoadParam ignored -> false;
    };
  }
}
