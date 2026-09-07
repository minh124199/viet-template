package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optimization pass detecting and consolidating sequential {@link IrWriteConst} statements within
 * blocks into single, combined text constants in {@link IrConstantPool}.
 */
public final class MergeTextConstantsPass implements IrOptimizationPass {

  public static final String NAME = "MergeTextConstants";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().mergeTextConstants()) {
      return template;
    }

    IrBlock newRoot = mergeBlock(template.root(), context);

    List<IrFunction> newFunctions = new ArrayList<>();
    for (IrFunction fn : template.functions()) {
      IrBlock newBody = mergeBlock(fn.body(), context);
      newFunctions.add(new IrFunction(fn.name(), fn.parameters(), fn.locals(), newBody, fn.span()));
    }

    return new IrTemplate(
        template.id(),
        template.parameters(),
        newRoot,
        context.constantPool(),
        template.capabilities(),
        newFunctions,
        template.span());
  }

  private IrBlock mergeBlock(IrBlock block, OptimizationContext context) {
    List<IrStatement> originalStmts = block.statements();
    List<IrStatement> mergedStmts = new ArrayList<>();
    int mergedCount = 0;

    int i = 0;
    while (i < originalStmts.size()) {
      IrStatement current = originalStmts.get(i);

      if (current instanceof IrWriteConst firstWc) {
        // Collect consecutive IrWriteConst statements
        List<IrWriteConst> chunk = new ArrayList<>();
        chunk.add(firstWc);

        int j = i + 1;
        while (j < originalStmts.size() && originalStmts.get(j) instanceof IrWriteConst nextWc) {
          chunk.add(nextWc);
          j++;
        }

        if (chunk.size() > 1) {
          // Merge text constants
          StringBuilder sb = new StringBuilder();
          SourceSpan firstSpan = chunk.get(0).span();
          SourceSpan lastSpan = chunk.get(chunk.size() - 1).span();

          for (IrWriteConst wc : chunk) {
            Optional<IrTextConstant> tc = context.constantPool().getTextConstant(wc.constantId());
            tc.ifPresent(c -> sb.append(c.text()));
          }

          SourceSpan mergedSpan = mergeSpans(firstSpan, lastSpan);
          int newConstId = context.constantPool().registerText(sb.toString(), mergedSpan);
          mergedStmts.add(new IrWriteConst(newConstId, mergedSpan));

          mergedCount += (chunk.size() - 1);
          i = j;
          continue;
        } else {
          mergedStmts.add(firstWc);
          i++;
          continue;
        }
      }

      if (current instanceof IrIf ifStmt) {
        IrBlock thenBlock = mergeBlock(ifStmt.thenBlock(), context);
        Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(eb -> mergeBlock(eb, context));
        mergedStmts.add(new IrIf(ifStmt.condition(), thenBlock, elseBlock, ifStmt.span()));
        i++;
        continue;
      }

      if (current instanceof IrLoop loop) {
        IrBlock body = mergeBlock(loop.body(), context);
        Optional<IrBlock> elseBody = loop.elseBody().map(eb -> mergeBlock(eb, context));
        mergedStmts.add(
            new IrLoop(
                loop.plan(),
                loop.iterable(),
                loop.elementLocal(),
                loop.loopStateLocal(),
                body,
                elseBody,
                loop.span()));
        i++;
        continue;
      }

      if (current instanceof IrCallMacro cm && cm.bodyContent().isPresent()) {
        IrBlock body = mergeBlock(cm.bodyContent().get(), context);
        mergedStmts.add(
            new IrCallMacro(cm.macroName(), cm.arguments(), Optional.of(body), cm.span()));
        i++;
        continue;
      }

      mergedStmts.add(current);
      i++;
    }

    if (mergedCount > 0) {
      context.statistics().recordTextConstantsMerged(mergedCount);
    }

    return new IrBlock(mergedStmts, block.span());
  }

  private SourceSpan mergeSpans(SourceSpan s1, SourceSpan s2) {
    if (s1.isKnown() && s2.isKnown()) {
      return SourceSpan.of(
          s1.startOffset(),
          s2.endOffset(),
          s1.startLine(),
          s1.startColumn(),
          s2.endLine(),
          s2.endColumn());
    }
    return s1.isKnown() ? s1 : s2;
  }
}
