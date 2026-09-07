package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MergeTextConstantsPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private MergeTextConstantsPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new MergeTextConstantsPass();
  }

  @Test
  @DisplayName("merges consecutive IrWriteConst statements into a single constant")
  void mergesConsecutiveTextConstants() {
    int c1 = pool.registerText("Hello ", span);
    int c2 = pool.registerText("World, ", span);
    int c3 = pool.registerText("welcome!", span);

    IrBlock block =
        IrBlock.of(
            span,
            new IrWriteConst(c1, span),
            new IrWriteConst(c2, span),
            new IrWriteConst(c3, span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("merge.vm"),
            List.of(),
            block,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    assertThat(optimized.root().statements()).hasSize(1);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrWriteConst.class);
    IrWriteConst merged = (IrWriteConst) optimized.root().statements().get(0);
    assertThat(pool.getTextConstant(merged.constantId()).orElseThrow().text())
        .isEqualTo("Hello World, welcome!");
    assertThat(context.statistics().textConstantsMerged()).isEqualTo(2);
  }

  @Test
  @DisplayName("does not merge across non-text statements")
  void doesNotMergeAcrossInterrupters() {
    int c1 = pool.registerText("Part 1: ", span);
    int c2 = pool.registerText("Part 2", span);

    IrBlock block =
        IrBlock.of(span, new IrWriteConst(c1, span), new IrNoOp(span), new IrWriteConst(c2, span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("split.vm"),
            List.of(),
            block,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    assertThat(optimized.root().statements()).hasSize(3);
    assertThat(context.statistics().textConstantsMerged()).isEqualTo(0);
  }
}
