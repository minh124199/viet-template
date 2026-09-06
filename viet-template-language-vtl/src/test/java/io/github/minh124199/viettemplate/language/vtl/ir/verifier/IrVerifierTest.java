package io.github.minh124199.viettemplate.language.vtl.ir.verifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.IrParameter;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicAccessSite;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.IrEscapeMode;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.NullRenderMode;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IrVerifierTest {

  private final SourceSpan testSpan = new SourceSpan(0, 10, 1, 1, 1, 11);

  @Test
  @DisplayName("well formed template passes verification")
  void validTemplate() {
    IrConstantPool pool = new IrConstantPool();
    int constId = pool.registerText("hello", testSpan);

    IrParameter param = new IrParameter("user", VTypes.STRING, 0, testSpan);
    IrLocal local = new IrLocal("x", VTypes.INT, 0, testSpan);

    IrBlock root =
        IrBlock.of(
            testSpan,
            new IrWriteConst(constId, testSpan),
            new IrStoreLocal(local, IrConst.ofInt(123, testSpan), testSpan),
            new IrWriteValue(
                new IrLoadLocal("x", 0, VTypes.INT, testSpan),
                IrEscapeMode.RAW,
                NullRenderMode.EMPTY_STRING,
                testSpan),
            new IrWriteValue(
                new IrLoadParam("user", 0, VTypes.STRING, testSpan),
                IrEscapeMode.RAW,
                NullRenderMode.LITERAL_EXPRESSION,
                testSpan));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vtl"),
            List.of(param),
            root,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            testSpan);

    assertThat(IrVerifier.check(template)).isEmpty();
    IrVerifier.verify(template);
  }

  @Test
  @DisplayName("fails when IrWriteConst references unknown constant ID")
  void unknownConstantIdFails() {
    IrConstantPool pool = new IrConstantPool();
    IrBlock root = IrBlock.of(testSpan, new IrWriteConst(99, testSpan));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vtl"),
            List.of(),
            root,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            testSpan);

    assertThatThrownBy(() -> IrVerifier.verify(template))
        .isInstanceOf(IrVerificationException.class)
        .hasMessageContaining("references nonexistent constant ID 99");
  }

  @Test
  @DisplayName("fails when local variable is read before write")
  void localReadBeforeWriteFails() {
    IrConstantPool pool = new IrConstantPool();
    IrBlock root =
        IrBlock.of(
            testSpan,
            new IrWriteValue(
                new IrLoadLocal("unwritten", 0, VTypes.STRING, testSpan),
                IrEscapeMode.RAW,
                NullRenderMode.LITERAL_EXPRESSION,
                testSpan));

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vtl"),
            List.of(),
            root,
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            testSpan);

    assertThatThrownBy(() -> IrVerifier.verify(template))
        .isInstanceOf(IrVerificationException.class)
        .hasMessageContaining("read before write");
  }

  @Test
  @DisplayName("fails when dynamic dispatch site present in AOT-eligible template")
  void dynamicDispatchInAotFails() {
    IrConstantPool pool = new IrConstantPool();
    DynamicAccessSite site = new DynamicAccessSite(1, DynamicKind.PROPERTY_GET, "dyn", testSpan);
    IrBlock root =
        IrBlock.of(
            testSpan,
            new IrWriteValue(
                IrDynamicDispatch.root(site, "dyn", VTypes.DYNAMIC, testSpan),
                IrEscapeMode.RAW,
                NullRenderMode.LITERAL_EXPRESSION,
                testSpan));

    // Template claims AOT eligibility
    TemplateCapabilities aotCapabilities =
        TemplateCapabilities.builder().setHasErrors(false).build();
    assertThat(aotCapabilities.eligibleForStaticAot()).isTrue();

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vtl"), List.of(), root, pool, aotCapabilities, List.of(), testSpan);

    assertThatThrownBy(() -> IrVerifier.verify(template))
        .isInstanceOf(IrVerificationException.class)
        .hasMessageContaining("Dynamic dispatch site 'dyn' forbidden in static AOT");
  }

  @Test
  @DisplayName("fails when dynamic loop plan is present in AOT-eligible template")
  void dynamicLoopInAotFails() {
    IrConstantPool pool = new IrConstantPool();
    IrLocal elem = new IrLocal("item", VTypes.DYNAMIC, 0, testSpan);
    IrLocal state = new IrLocal("foreach", VTypes.DYNAMIC, 1, testSpan);

    IrLoop loop =
        IrLoop.of(
            LoopPlan.DYNAMIC,
            IrConst.ofString("dynamicItems", testSpan),
            elem,
            state,
            IrBlock.empty(testSpan),
            testSpan);

    IrBlock root = IrBlock.of(testSpan, loop);

    TemplateCapabilities aotCapabilities =
        TemplateCapabilities.builder().setHasErrors(false).build();

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("test.vtl"), List.of(), root, pool, aotCapabilities, List.of(), testSpan);

    assertThatThrownBy(() -> IrVerifier.verify(template))
        .isInstanceOf(IrVerificationException.class)
        .hasMessageContaining("Dynamic loop plan forbidden in static AOT");
  }
}
