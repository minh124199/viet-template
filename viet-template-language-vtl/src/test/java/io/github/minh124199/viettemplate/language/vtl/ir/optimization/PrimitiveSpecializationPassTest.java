package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrimitiveSpecializationPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private PrimitiveSpecializationPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new PrimitiveSpecializationPass();
  }

  @Test
  @DisplayName("specializes arithmetic operations on primitive types and strips boxing wrapper")
  void specializesPrimitiveArithmetic() {
    IrLoadLocal loadA = new IrLoadLocal("a", 0, VTypes.INT, span);
    IrLoadLocal loadB = new IrLoadLocal("b", 1, VTypes.INT, span);

    // Boxed operand
    IrConvert boxedA = new IrConvert(loadA, VTypes.OBJECT, span);

    IrBinaryOp add = new IrBinaryOp(BinaryOpKind.ADD, boxedA, loadB, VTypes.OBJECT, span);

    IrBlock block =
        IrBlock.of(
            span, new IrWriteValue(add, IrEscapeMode.RAW, NullRenderMode.EMPTY_STRING, span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("prim.vm"),
            List.of(),
            block,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    IrWriteValue wv = (IrWriteValue) optimized.root().statements().get(0);
    IrBinaryOp optAdd = (IrBinaryOp) wv.value();
    assertThat(optAdd.type()).isEqualTo(VTypes.INT);
    assertThat(optAdd.left()).isEqualTo(loadA); // Unboxed
    assertThat(context.statistics().primitivesSpecialized()).isGreaterThanOrEqualTo(1);
  }
}
