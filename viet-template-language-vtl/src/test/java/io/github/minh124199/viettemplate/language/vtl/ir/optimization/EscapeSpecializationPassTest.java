package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EscapeSpecializationPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private EscapeSpecializationPass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new EscapeSpecializationPass();
  }

  @Test
  @DisplayName("hoists constant string write with HTML escaping into pre-escaped IrWriteConst")
  void hoistsAndPreEscapesConstantString() {
    IrConst htmlConst = new IrConst("<b>Hello & Welcome</b>", VTypes.STRING, span);
    IrWriteValue wv =
        new IrWriteValue(htmlConst, IrEscapeMode.HTML_TEXT, NullRenderMode.EMPTY_STRING, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("escape.vm"),
            List.of(),
            IrBlock.of(span, wv),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);

    assertThat(optimized.root().statements()).hasSize(1);
    assertThat(optimized.root().statements().get(0)).isInstanceOf(IrWriteConst.class);
    IrWriteConst wc = (IrWriteConst) optimized.root().statements().get(0);
    assertThat(pool.getTextConstant(wc.constantId()).orElseThrow().text())
        .isEqualTo("&lt;b&gt;Hello &amp; Welcome&lt;/b&gt;");
    assertThat(context.statistics().escapesHoisted()).isEqualTo(1);
  }
}
