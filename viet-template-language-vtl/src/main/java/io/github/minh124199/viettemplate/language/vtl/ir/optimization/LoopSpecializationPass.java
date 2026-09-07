package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;

/**
 * Optimization pass specializing {@link IrLoop} iteration plans from generic dynamic traversal to
 * fast specialized paths ({@link LoopPlan#ARRAY}, {@link LoopPlan#LIST_INDEXED}, {@link
 * LoopPlan#ITERABLE}, {@link LoopPlan#ITERATOR}, {@link LoopPlan#RANGE}) when collection types are
 * statically known.
 */
public final class LoopSpecializationPass implements IrOptimizationPass {

  public static final String NAME = "LoopSpecialization";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().loopSpecialization()) {
      return template;
    }

    IrBlock newRoot = specializeBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = specializeBlock(fn.body(), context);
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

  private IrBlock specializeBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> specializedStmts = new ArrayList<>();
    for (IrStatement stmt : block.statements()) {
      specializedStmts.add(specializeStatement(stmt, context));
    }
    return new IrBlock(specializedStmts, block.span());
  }

  private IrStatement specializeStatement(IrStatement stmt, OptimizationContext context) {
    if (stmt instanceof IrLoop loop) {
      IrBlock body = specializeBlock(loop.body(), context);
      Optional<IrBlock> elseBody = loop.elseBody().map(eb -> specializeBlock(eb, context));

      LoopPlan specializedPlan = loop.plan();
      if (specializedPlan == LoopPlan.DYNAMIC) {
        specializedPlan = determineSpecializedPlan(loop.iterable().type());
        if (specializedPlan != LoopPlan.DYNAMIC) {
          context.statistics().recordLoopSpecialized();
        }
      }

      return new IrLoop(
          specializedPlan,
          loop.iterable(),
          loop.elementLocal(),
          loop.loopStateLocal(),
          body,
          elseBody,
          loop.span());
    }

    if (stmt instanceof IrIf ifStmt) {
      IrBlock thenBlock = specializeBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(eb -> specializeBlock(eb, context));
      return new IrIf(ifStmt.condition(), thenBlock, elseBlock, ifStmt.span());
    }

    if (stmt instanceof IrCallMacro cm && cm.bodyContent().isPresent()) {
      IrBlock body = specializeBlock(cm.bodyContent().get(), context);
      return new IrCallMacro(cm.macroName(), cm.arguments(), Optional.of(body), cm.span());
    }

    return stmt;
  }

  private LoopPlan determineSpecializedPlan(VType type) {
    if (type instanceof VType.ArrayType) {
      return LoopPlan.ARRAY;
    }

    if (type instanceof VType.ClassType ct && ct.javaClass().isPresent()) {
      Class<?> clazz = ct.javaClass().get();
      if (clazz.isArray()) {
        return LoopPlan.ARRAY;
      }
      if (RandomAccess.class.isAssignableFrom(clazz) && List.class.isAssignableFrom(clazz)) {
        return LoopPlan.LIST_INDEXED;
      }
      if (Iterator.class.isAssignableFrom(clazz)) {
        return LoopPlan.ITERATOR;
      }
      if (Collection.class.isAssignableFrom(clazz) || Iterable.class.isAssignableFrom(clazz)) {
        return LoopPlan.ITERABLE;
      }
    }

    return LoopPlan.DYNAMIC;
  }
}
