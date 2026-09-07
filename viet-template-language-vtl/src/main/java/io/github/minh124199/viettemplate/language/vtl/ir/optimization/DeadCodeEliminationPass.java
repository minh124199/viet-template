package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optimization pass eliminating unreachable IR code, dead branches after constant evaluation,
 * trailing statements after terminal statements (return, stop, break), and redundant no-op
 * statements.
 */
public final class DeadCodeEliminationPass implements IrOptimizationPass {

  public static final String NAME = "DeadCodeElimination";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().deadCodeElimination()) {
      return template;
    }

    IrBlock newRoot = optimizeBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = optimizeBlock(fn.body(), context);
      newFunctions.add(new IrFunction(fn.name(), fn.parameters(), fn.locals(), newBody, fn.span()));
    }

    return new IrTemplate(
        template.id(),
        template.parameters(),
        newRoot,
        template.constants(),
        template.capabilities(),
        newFunctions,
        template.span());
  }

  private IrBlock optimizeBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> originalStmts = block.statements();
    List<IrStatement> optimizedStmts = new ArrayList<>();
    int removedCount = 0;

    for (int i = 0; i < originalStmts.size(); i++) {
      IrStatement stmt = originalStmts.get(i);

      if (stmt instanceof IrNoOp) {
        removedCount++;
        continue;
      }

      if (stmt instanceof IrIf ifStmt) {
        List<IrStatement> branchResult = optimizeIf(ifStmt, context);
        if (branchResult.isEmpty()) {
          removedCount++;
        } else {
          optimizedStmts.addAll(branchResult);
        }
        continue;
      }

      if (stmt instanceof IrLoop loop) {
        IrBlock newBody = optimizeBlock(loop.body(), context);
        Optional<IrBlock> newElse =
            loop.elseBody().map(elseBlock -> optimizeBlock(elseBlock, context));
        optimizedStmts.add(
            new IrLoop(
                loop.plan(),
                loop.iterable(),
                loop.elementLocal(),
                loop.loopStateLocal(),
                newBody,
                newElse,
                loop.span()));
        continue;
      }

      if (stmt instanceof IrCallMacro callM && callM.bodyContent().isPresent()) {
        IrBlock newBody = optimizeBlock(callM.bodyContent().get(), context);
        optimizedStmts.add(
            new IrCallMacro(
                callM.macroName(), callM.arguments(), Optional.of(newBody), callM.span()));
        continue;
      }

      optimizedStmts.add(stmt);

      // Terminal statements: IrReturn, IrStop, IrBreak prune all subsequent statements in this
      // block
      if (stmt instanceof IrReturn || stmt instanceof IrStop || stmt instanceof IrBreak) {
        int remaining = originalStmts.size() - (i + 1);
        if (remaining > 0) {
          removedCount += remaining;
        }
        break;
      }
    }

    if (removedCount > 0) {
      context.statistics().recordDeadCodeRemoved(removedCount);
    }

    return new IrBlock(optimizedStmts, block.span());
  }

  private List<IrStatement> optimizeIf(IrIf ifStmt, OptimizationContext context) {
    IrBlock thenBlock = optimizeBlock(ifStmt.thenBlock(), context);
    Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(eb -> optimizeBlock(eb, context));

    IrExpression cond = ifStmt.condition();

    // Constant condition folding
    if (cond instanceof IrConst c && c.value() instanceof Boolean b) {
      if (b) {
        // Condition is statically true: inline thenBlock, discard elseBlock
        int pruned = elseBlock.map(IrBlock::size).orElse(0);
        if (pruned > 0) {
          context.statistics().recordDeadCodeRemoved(pruned);
        }
        return thenBlock.statements();
      } else {
        // Condition is statically false: discard thenBlock, inline elseBlock (if present)
        int pruned = thenBlock.size();
        if (pruned > 0) {
          context.statistics().recordDeadCodeRemoved(pruned);
        }
        return elseBlock.map(IrBlock::statements).orElse(List.of());
      }
    }

    // Both branches empty and condition is pure: discard if statement
    if (thenBlock.isEmpty() && (!elseBlock.isPresent() || elseBlock.get().isEmpty())) {
      if (isPure(cond)) {
        context.statistics().recordDeadCodeRemoved(1);
        return List.of();
      }
    }

    return List.of(new IrIf(cond, thenBlock, elseBlock, ifStmt.span()));
  }

  private boolean isPure(IrExpression expr) {
    return expr instanceof IrConst;
  }
}
