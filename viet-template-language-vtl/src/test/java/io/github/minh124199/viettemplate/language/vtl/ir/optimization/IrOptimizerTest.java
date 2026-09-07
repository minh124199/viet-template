package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.ir.verifier.IrVerifier;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IrOptimizerTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
  }

  private IrTemplate createSampleTemplate() {
    int c1 = pool.registerText("Hello ", span);
    int c2 = pool.registerText("World!", span);

    IrBinaryOp math =
        new IrBinaryOp(
            BinaryOpKind.ADD, IrConst.ofInt(10, span), IrConst.ofInt(20, span), VTypes.INT, span);

    IrBlock block =
        IrBlock.of(
            span,
            new IrWriteConst(c1, span),
            new IrWriteConst(c2, span),
            new IrNoOp(span),
            new IrWriteValue(math, IrEscapeMode.RAW, NullRenderMode.EMPTY_STRING, span));

    return new IrTemplate(
        TemplateId.of("sample.vm"),
        List.of(),
        block,
        pool,
        TemplateCapabilities.empty(),
        List.of(),
        span);
  }

  @Test
  @DisplayName("O0 keeps template unoptimized but passes verifier")
  void o0SkipsOptimizationPasses() {
    IrTemplate template = createSampleTemplate();
    IrTemplate opt = IrOptimizer.optimize(template, IrOptimizationOptions.o0());

    // Should preserve all statements at O0
    assertThat(opt.root().statements()).hasSize(4);
    IrVerifier.verify(opt);
  }

  @Test
  @DisplayName("O1 applies essential cleanup and folding")
  void o1AppliesEssentialCleanup() {
    IrTemplate template = createSampleTemplate();
    IrTemplate opt = IrOptimizer.optimize(template, IrOptimizationOptions.o1());

    // Merges text constants, removes no-op, folds arithmetic
    assertThat(opt.root().statements()).hasSize(2);
    IrVerifier.verify(opt);
  }

  @Test
  @DisplayName("O2 applies full standard optimization pipeline")
  void o2AppliesFullPipeline() {
    IrTemplate template = createSampleTemplate();
    IrTemplate opt = IrOptimizer.optimize(template, IrOptimizationOptions.o2());

    IrVerifier.verify(opt);
    // At O2, escape hoisting + constant merging collapses everything into 1 static write
    assertThat(opt.root().statements()).hasSize(1);
    IrWriteConst wc = (IrWriteConst) opt.root().statements().get(0);
    assertThat(pool.getTextConstant(wc.constantId()).orElseThrow().text())
        .isEqualTo("Hello World!30");
  }

  @Test
  @DisplayName("O3 applies aggressive pipeline with explain plan generation")
  void o3WithExplainPlan() {
    IrTemplate template = createSampleTemplate();
    IrOptimizationOptions options =
        IrOptimizationOptions.builder().level(OptimizationLevel.O3).build();

    IrTemplate opt = IrOptimizer.optimize(template, options);
    IrVerifier.verify(opt);

    String plan = IrExplainPlan.explain(opt, new OptimizationStatistics());
    assertThat(plan).contains("=== IrExplainPlan: sample.vm ===");
    assertThat(plan).contains("Optimization Statistics:");
  }
}
