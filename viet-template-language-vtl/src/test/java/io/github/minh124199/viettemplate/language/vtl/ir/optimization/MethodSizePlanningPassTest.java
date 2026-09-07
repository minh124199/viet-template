package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStatement;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MethodSizePlanningPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private MethodSizePlanningPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    // Threshold of 10 statements for test
    IrOptimizationOptions options =
        IrOptimizationOptions.builder().methodSplitThreshold(10).build();
    context = new OptimizationContext(options, pool);
    pass = new MethodSizePlanningPass();
  }

  @Test
  @DisplayName("splits blocks exceeding threshold into segmented chunk functions")
  void splitsLargeBlocks() {
    List<IrStatement> statements = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      int cid = pool.registerText("chunk_" + i, span);
      statements.add(new IrWriteConst(cid, span));
    }

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("large.vm"),
            List.of(),
            new IrBlock(statements, span),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    // 25 statements with threshold 10:
    // First 10 statements kept in root, remaining 15 split into 2 chunk functions
    assertThat(optimized.root().statements()).hasSize(12);
    assertThat(optimized.functions()).hasSize(2);
    assertThat(context.statistics().methodsSplit()).isEqualTo(2);
  }

  @Test
  @DisplayName("leaves blocks under threshold intact")
  void leavesSmallBlocksIntact() {
    List<IrStatement> statements = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      int cid = pool.registerText("small_" + i, span);
      statements.add(new IrWriteConst(cid, span));
    }

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("small.vm"),
            List.of(),
            new IrBlock(statements, span),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    assertThat(optimized.root().statements()).hasSize(5);
    assertThat(optimized.functions()).isEmpty();
  }
}
