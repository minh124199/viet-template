package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrTextConstant;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreEncodeUtf8PassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private PreEncodeUtf8Pass pass;

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new PreEncodeUtf8Pass();
  }

  @Test
  @DisplayName("pre-encodes text constants into UTF-8 byte arrays")
  void preEncodesUtf8Bytes() {
    String text = "Xin chào thế giới! 123";
    int cid = 0;
    pool.add(IrTextConstant.withoutUtf8(cid, text, span));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("utf8.vm"),
            List.of(),
            IrBlock.of(span, new IrWriteConst(cid, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    // Before pass: no utf-8 bytes
    assertThat(pool.getTextConstant(cid).orElseThrow().utf8Bytes()).isEmpty();

    IrTemplate optimized = pass.run(template, context);

    // After pass: utf-8 bytes pre-encoded
    IrTextConstant tc = optimized.constants().getTextConstant(cid).orElseThrow();
    assertThat(tc.utf8Bytes()).isPresent();
    assertThat(tc.utf8Bytes().get()).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    assertThat(context.statistics().utf8ConstantsEncoded()).isGreaterThanOrEqualTo(1);
  }
}
