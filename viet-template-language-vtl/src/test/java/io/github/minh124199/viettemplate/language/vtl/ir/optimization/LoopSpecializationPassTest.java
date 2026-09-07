package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoopSpecializationPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private LoopSpecializationPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new LoopSpecializationPass();
  }

  @Test
  @DisplayName("specializes loop on array type to LoopPlan.ARRAY")
  void specializesArrayLoop() {
    VType.ArrayType arrayType = new VType.ArrayType(VTypes.STRING, Nullability.NON_NULL);
    IrLoadLocal loadArr = new IrLoadLocal("items", 0, arrayType, span);
    IrLocal elemLocal = new IrLocal("item", VTypes.STRING, 1, span);

    IrLoop loop =
        new IrLoop(
            LoopPlan.DYNAMIC,
            loadArr,
            elemLocal,
            Optional.empty(),
            IrBlock.empty(span),
            Optional.empty(),
            span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("loop.vm"),
            List.of(),
            IrBlock.of(span, loop),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    IrLoop optLoop = (IrLoop) optimized.root().statements().get(0);
    assertThat(optLoop.plan()).isEqualTo(LoopPlan.ARRAY);
    assertThat(context.statistics().loopsSpecialized()).isEqualTo(1);
  }

  @Test
  @DisplayName("specializes loop on ArrayList to LoopPlan.LIST_INDEXED")
  void specializesListLoop() {
    VType listType = VTypes.fromJavaClass(ArrayList.class);
    IrLoadLocal loadList = new IrLoadLocal("list", 0, listType, span);
    IrLocal elemLocal = new IrLocal("item", VTypes.OBJECT, 1, span);

    IrLoop loop =
        new IrLoop(
            LoopPlan.DYNAMIC,
            loadList,
            elemLocal,
            Optional.empty(),
            IrBlock.empty(span),
            Optional.empty(),
            span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("list_loop.vm"),
            List.of(),
            IrBlock.of(span, loop),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    IrLoop optLoop = (IrLoop) optimized.root().statements().get(0);
    assertThat(optLoop.plan()).isEqualTo(LoopPlan.LIST_INDEXED);
    assertThat(context.statistics().loopsSpecialized()).isEqualTo(1);
  }
}
