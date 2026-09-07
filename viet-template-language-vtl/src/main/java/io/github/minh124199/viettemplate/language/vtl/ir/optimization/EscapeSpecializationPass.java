package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrExpression;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optimization pass that performs compile-time escaping of static constant writes and hoists
 * escaping checks when content is statically verified to be safe.
 */
public final class EscapeSpecializationPass implements IrOptimizationPass {

  public static final String NAME = "EscapeSpecialization";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public IrTemplate run(IrTemplate template, OptimizationContext context) {
    if (!context.options().escapeSpecialization()) {
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
    if (stmt instanceof IrWriteValue wv) {
      IrExpression val = wv.value();

      if (val instanceof IrConst c && c.value() != null) {
        String strVal = String.valueOf(c.value());

        if (wv.escapeMode() == IrEscapeMode.HTML_TEXT) {
          String escaped = escapeHtml(strVal);
          int constId = context.constantPool().registerText(escaped, wv.span());
          context.statistics().recordEscapeHoisted();
          return new IrWriteConst(constId, wv.span());
        }

        if (wv.escapeMode() == IrEscapeMode.RAW) {
          // If the constant contains no reference or directive escaping chars, write as constant
          if (!strVal.contains("\\") && !strVal.contains("$") && !strVal.contains("#")) {
            int constId = context.constantPool().registerText(strVal, wv.span());
            context.statistics().recordEscapeHoisted();
            return new IrWriteConst(constId, wv.span());
          }
        }
      }

      return wv;
    }

    if (stmt instanceof IrIf ifStmt) {
      IrBlock thenBlock = specializeBlock(ifStmt.thenBlock(), context);
      Optional<IrBlock> elseBlock = ifStmt.elseBlock().map(eb -> specializeBlock(eb, context));
      return new IrIf(ifStmt.condition(), thenBlock, elseBlock, ifStmt.span());
    }

    if (stmt instanceof IrLoop loop) {
      IrBlock body = specializeBlock(loop.body(), context);
      Optional<IrBlock> elseBody = loop.elseBody().map(eb -> specializeBlock(eb, context));
      return new IrLoop(
          loop.plan(),
          loop.iterable(),
          loop.elementLocal(),
          loop.loopStateLocal(),
          body,
          elseBody,
          loop.span());
    }

    if (stmt instanceof IrCallMacro cm && cm.bodyContent().isPresent()) {
      IrBlock body = specializeBlock(cm.bodyContent().get(), context);
      return new IrCallMacro(cm.macroName(), cm.arguments(), Optional.of(body), cm.span());
    }

    return stmt;
  }

  private String escapeHtml(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    StringBuilder sb = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        case '\'' -> sb.append("&#39;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
