package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BooleanSimplificationPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private BooleanSimplificationPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new BooleanSimplificationPass();
  }

  @Test
  @DisplayName("simplifies true && x to x and false || x to x")
  void simplifiesIdentityLogicals() {
    IrLoadLocal loadX = new IrLoadLocal("x", 0, VTypes.BOOLEAN, span);
    IrBinaryOp andExpr =
        new IrBinaryOp(
            BinaryOpKind.AND, new IrConst(true, VTypes.BOOLEAN, span), loadX, VTypes.BOOLEAN, span);

    IrBinaryOp orExpr =
        new IrBinaryOp(
            BinaryOpKind.OR, new IrConst(false, VTypes.BOOLEAN, span), loadX, VTypes.BOOLEAN, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("bool.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(andExpr, span), new IrEvaluate(orExpr, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval1 = (IrEvaluate) optimized.root().statements().get(0);
    IrEvaluate eval2 = (IrEvaluate) optimized.root().statements().get(1);
    assertThat(eval1.expression()).isEqualTo(loadX);
    assertThat(eval2.expression()).isEqualTo(loadX);
    assertThat(context.statistics().booleansSimplified()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("simplifies double negation !(!x) to x")
  void simplifiesDoubleNegation() {
    IrLoadLocal loadX = new IrLoadLocal("x", 0, VTypes.BOOLEAN, span);
    IrUnaryOp innerNot = new IrUnaryOp(UnaryOpKind.NOT, loadX, VTypes.BOOLEAN, span);
    IrUnaryOp outerNot = new IrUnaryOp(UnaryOpKind.NOT, innerNot, VTypes.BOOLEAN, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("not.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(outerNot, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isEqualTo(loadX);
  }

  @Test
  @DisplayName("inverts negated condition with then and else branches")
  void invertsNegatedConditionInIf() {
    IrLoadLocal loadCond = new IrLoadLocal("flag", 0, VTypes.BOOLEAN, span);
    IrUnaryOp notCond = new IrUnaryOp(UnaryOpKind.NOT, loadCond, VTypes.BOOLEAN, span);

    int c1 = pool.registerText("then_branch", span);
    int c2 = pool.registerText("else_branch", span);

    IrIf ifStmt =
        new IrIf(
            notCond,
            IrBlock.of(span, new IrWriteConst(c1, span)),
            Optional.of(IrBlock.of(span, new IrWriteConst(c2, span))),
            span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("if_invert.vm"),
            List.of(),
            IrBlock.of(span, ifStmt),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrIf optIf = (IrIf) optimized.root().statements().get(0);
    assertThat(optIf.condition()).isEqualTo(loadCond);
    IrWriteConst optThen = (IrWriteConst) optIf.thenBlock().statements().get(0);
    IrWriteConst optElse = (IrWriteConst) optIf.elseBlock().get().statements().get(0);
    assertThat(pool.getTextConstant(optThen.constantId()).orElseThrow().text())
        .isEqualTo("else_branch");
    assertThat(pool.getTextConstant(optElse.constantId()).orElseThrow().text())
        .isEqualTo("then_branch");
  }
}
