package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConvert;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RedundantConversionPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private RedundantConversionPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new RedundantConversionPass();
  }

  @Test
  @DisplayName(
      "eliminates identity type conversion where expression type already equals target type")
  void eliminatesIdentityConversion() {
    IrLoadLocal loadStr = new IrLoadLocal("str", 0, VTypes.STRING, span);
    IrConvert identityConv = new IrConvert(loadStr, VTypes.STRING, span);

    IrBlock block =
        IrBlock.of(
            span,
            new IrWriteValue(identityConv, IrEscapeMode.RAW, NullRenderMode.EMPTY_STRING, span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("conv.vm"),
            List.of(),
            block,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    IrWriteValue wv = (IrWriteValue) optimized.root().statements().get(0);
    assertThat(wv.value()).isEqualTo(loadStr);
    assertThat(context.statistics().conversionsEliminated()).isEqualTo(1);
  }
}
