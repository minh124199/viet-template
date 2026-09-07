package io.github.minh124199.viettemplate.language.vtl.ir.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrDynamicDispatch;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicAccessSite;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.DynamicKind;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrEvaluate;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectAccessorBindingPassTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);
  private IrConstantPool pool;
  private OptimizationContext context;
  private DirectAccessorBindingPass pass;

  public record SampleUser(String name, int age) {}

  public static class SampleBean {
    private String title = "test";

    public String getTitle() {
      return title;
    }
  }

  public static class SampleFieldHolder {
    public int counter = 42;
  }

  @BeforeEach
  void setUp() {
    pool = new IrConstantPool();
    context = new OptimizationContext(IrOptimizationOptions.defaultOptions(), pool);
    pass = new DirectAccessorBindingPass();
  }

  @Test
  @DisplayName("binds record component access directly to DirectRecord")
  void bindsRecordAccessor() {
    VType recordType = VTypes.fromJavaClass(SampleUser.class);
    IrLoadLocal loadUser = new IrLoadLocal("user", 0, recordType, span);
    DynamicAccessSite site = new DynamicAccessSite(1, DynamicKind.PROPERTY_GET, "name", span);

    IrDynamicDispatch dispatch =
        new IrDynamicDispatch(site, Optional.of(loadUser), "name", List.of(), VTypes.OBJECT, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("record.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(dispatch, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrGetProperty.class);
    IrGetProperty gp = (IrGetProperty) eval.expression();
    assertThat(gp.accessPlan()).isInstanceOf(AccessPlan.DirectRecord.class);
    assertThat(context.statistics().accessorsBound()).isEqualTo(1);
  }

  @Test
  @DisplayName("binds getter method access to DirectGetter")
  void bindsGetterAccessor() {
    VType beanType = VTypes.fromJavaClass(SampleBean.class);
    IrLoadLocal loadBean = new IrLoadLocal("bean", 0, beanType, span);
    DynamicAccessSite site = new DynamicAccessSite(2, DynamicKind.PROPERTY_GET, "title", span);

    IrDynamicDispatch dispatch =
        new IrDynamicDispatch(site, Optional.of(loadBean), "title", List.of(), VTypes.OBJECT, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("getter.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(dispatch, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrGetProperty.class);
    IrGetProperty gp = (IrGetProperty) eval.expression();
    assertThat(gp.accessPlan()).isInstanceOf(AccessPlan.DirectGetter.class);
  }

  @Test
  @DisplayName("binds map lookup to MapLookup")
  void bindsMapLookupAccessor() {
    VType mapType = VTypes.fromJavaClass(Map.class);
    IrLoadLocal loadMap = new IrLoadLocal("map", 0, mapType, span);
    DynamicAccessSite site = new DynamicAccessSite(3, DynamicKind.PROPERTY_GET, "key", span);

    IrDynamicDispatch dispatch =
        new IrDynamicDispatch(site, Optional.of(loadMap), "key", List.of(), VTypes.OBJECT, span);

    IrTemplate template =
        new IrTemplate(
            TemplateId.of("map.vm"),
            List.of(),
            IrBlock.of(span, new IrEvaluate(dispatch, span)),
            pool,
            TemplateCapabilities.empty(),
            List.of(),
            span);

    IrTemplate optimized = pass.run(template, context);
    IrEvaluate eval = (IrEvaluate) optimized.root().statements().get(0);
    assertThat(eval.expression()).isInstanceOf(IrGetProperty.class);
    IrGetProperty gp = (IrGetProperty) eval.expression();
    assertThat(gp.accessPlan()).isInstanceOf(AccessPlan.MapLookup.class);
  }
}
