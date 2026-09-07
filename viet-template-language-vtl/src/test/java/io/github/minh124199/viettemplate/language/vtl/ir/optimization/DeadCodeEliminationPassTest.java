package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrReturn;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadCodeEliminationPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private DeadCodeEliminationPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new DeadCodeEliminationPass();
  }

  @Test
  @DisplayName("eliminates unreachable statements after terminal IrReturn/IrStop/IrBreak")
  void eliminatesAfterTerminalStatements() {
    int c1 = pool.registerText("live1", span);
    int c2 = pool.registerText("dead", span);

    IrBlock block =
        IrBlock.of(
            span,
            new IrWriteConst(c1, span),
            IrReturn.voidReturn(span),
            new IrWriteConst(c2, span),
            new IrNoOp(span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vm"),
            List.of(),
            block,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    assertThat(optimized.root().statements()).hasSize(2);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrWriteConst.class);
    assertThat(optimized.root().statements().get(1)).isInstanceOf(IrReturn.class);
    assertThat(context.statistics().deadCodeRemoved()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("eliminates if branch when condition is statically true")
  void simplifiesConstantTrueCondition() {
    int liveConst = pool.registerText("always_true", span);
    int deadConst = pool.registerText("never_reached", span);

    IrBlock thenBlock = IrBlock.of(span, new IrWriteConst(liveConst, span));
    IrBlock elseBlock = IrBlock.of(span, new IrWriteConst(deadConst, span));

    IrIf ifStmt =
        new IrIf(new IrConst(true, VTypes.BOOLEAN, span), thenBlock, Optional.of(elseBlock), span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("true_test.vm"),
            List.of(),
            IrBlock.of(span, ifStmt),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    assertThat(optimized.root().statements()).hasSize(1);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrWriteConst.class);
    IrWriteConst wc = (IrWriteConst) optimized.root().statements().get(0);
    assertThat(pool.getTextConstant(wc.constantId()).orElseThrow().text()).isEqualTo("always_true");
  }

  @Test
  @DisplayName("eliminates if branch when condition is statically false")
  void simplifiesConstantFalseCondition() {
    int deadConst = pool.registerText("never_reached", span);
    int liveConst = pool.registerText("always_false_else", span);

    IrBlock thenBlock = IrBlock.of(span, new IrWriteConst(deadConst, span));
    IrBlock elseBlock = IrBlock.of(span, new IrWriteConst(liveConst, span));

    IrIf ifStmt =
        new IrIf(new IrConst(false, VTypes.BOOLEAN, span), thenBlock, Optional.of(elseBlock), span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("false_test.vm"),
            List.of(),
            IrBlock.of(span, ifStmt),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    assertThat(optimized.root().statements()).hasSize(1);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrWriteConst.class);
    IrWriteConst wc = (IrWriteConst) optimized.root().statements().get(0);
    assertThat(pool.getTextConstant(wc.constantId()).orElseThrow().text())
        .isEqualTo("always_false_else");
  }

  @Test
  @DisplayName("eliminates NoOp statements completely")
  void eliminatesNoOps() {
    IrBlock block = IrBlock.of(span, new IrNoOp(span), new IrNoOp(span));
    IrTemplate template =
        new IrTemplate(
            TemplateId.of("noop_test.vm"),
            List.of(),
            block,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    assertThat(optimized.root().statements()).isEmpty();
  }
}
