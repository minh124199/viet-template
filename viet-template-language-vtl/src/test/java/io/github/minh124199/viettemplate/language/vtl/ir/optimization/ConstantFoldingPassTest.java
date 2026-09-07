package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrTruthiness;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrUnaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.UnaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConstantFoldingPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private ConstantFoldingPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new ConstantFoldingPass();
  }

  @Test
  @DisplayName("folds arithmetic binary operations on constant integers")
  void foldsArithmeticOperations() {
    IrBinaryOp add =
        new IrBinaryOp(
            BinaryOpKind.ADD, IrConst.ofInt(10, span), IrConst.ofInt(5, span), VTypes.INT, span);
    IrTemplate template =
        new IrTemplate(
            TemplateId.of("arith.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(add, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrConst.class);
    assertThat(((IrConst) eval.expression()).value()).isEqualTo(15);
    assertThat(context.statistics().constantsFolded()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("folds string concatenation on constant strings")
  void foldsStringConcatenation() {
    IrBinaryOp concat =
        new IrBinaryOp(
            BinaryOpKind.ADD,
            new IrConst("hello ", VTypes.STRING, span),
            new IrConst("world", VTypes.STRING, span),
            VTypes.STRING,
            span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("concat.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(concat, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrConst.class);
    assertThat(((IrConst) eval.expression()).value()).isEqualTo("hello world");
  }

  @Test
  @DisplayName("folds comparison and equality operations")
  void foldsComparisons() {
    IrBinaryOp comp =
        new IrBinaryOp(
            BinaryOpKind.GREATER_THAN,
            IrConst.ofInt(10, span),
            IrConst.ofInt(5, span),
            VTypes.BOOLEAN,
            span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("comp.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(comp, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrConst.class);
    assertThat(((IrConst) eval.expression()).value()).isEqualTo(true);
  }

  @Test
  @DisplayName("folds unary operations and truthiness")
  void foldsUnaryAndTruthiness() {
    IrUnaryOp not =
        new IrUnaryOp(
            UnaryOpKind.NOT, new IrConst(false, VTypes.BOOLEAN, span), VTypes.BOOLEAN, span);
    IrTruthiness truth =
        new IrTruthiness(new IrConst("non-empty", VTypes.STRING, span), true, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("unary.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(not, span), new IrEvaluate(truth, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval1 = (IrEvaluate) optimized.root().statements().get(0);
    IrEvaluate eval2 = (IrEvaluate) optimized.root().statements().get(1);
    assertThat(((IrConst) eval1.expression()).value()).isEqualTo(true);
    assertThat(((IrConst) eval2.expression()).value()).isEqualTo(true);
  }

  @Test
  @DisplayName("does not fold ArrayType range expressions into integer addition")
  void preservesRangeExpressions() {
    VType.ArrayType arrayType = new VType.ArrayType(VTypes.INT, Nullability.NON_NULL);
    IrBinaryOp range =
        new IrBinaryOp(
            BinaryOpKind.ADD, IrConst.ofInt(1, span), IrConst.ofInt(10, span), arrayType, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("range.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(range, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrBinaryOp.class);
    assertThat(((IrBinaryOp) eval.expression()).type()).isEqualTo(arrayType);
  }
}
