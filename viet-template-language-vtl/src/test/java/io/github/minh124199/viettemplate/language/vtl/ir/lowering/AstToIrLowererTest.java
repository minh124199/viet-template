package io.github.minh124199.viettemplate.language.vtl.ir.lowering;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrBlock;
import io.github.minh124199.viettemplate.language.vtl.ir.IrFunction;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrBinaryOp;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrConst;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrGetProperty;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.expression.IrLoadParam;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.AccessPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.BinaryOpKind;
import io.github.minh124199.viettemplate.language.vtl.ir.plan.LoopPlan;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrBreak;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallMacro;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrCallTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrIf;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrStoreLocal;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteConst;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrWriteValue;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.Nullability;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VType;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AstToIrLowererTest {

  public record User(String name, int age) {}

  public static class ItemListHolder {
    private List<String> items = List.of("apple", "banana");

    public List<String> getItems() {
      return items;
    }
  }

  private IrTemplate parseAndLower(String sourceStr, ModelSchema schema) {
    SourceText source = SourceText.of("test.vtl", sourceStr);
    VtlParseResult parsed = VtlParser.parse(source);
    VtlSemanticOptions options = VtlSemanticOptions.of(VtlProfile.VTL_CORE, schema);
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    return AstToIrLowerer.lower(parsed.template(), source, analysis, options);
  }

  @Test
  @DisplayName("collapses adjacent static text chunks into a single constant pool entry")
  void collapsesAdjacentText() {
    String source = "Hello, world! How are you?";
    IrTemplate ir = parseAndLower(source, ModelSchema.empty());

    assertThat(ir.constants().size()).isEqualTo(1);
    assertThat(ir.constants().getTextConstant(0)).isPresent();
    assertThat(ir.constants().getTextConstant(0).get().text()).isEqualTo(source);

    assertThat(ir.root().statements()).hasSize(1);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrWriteConst.class);
    IrWriteConst write = (IrWriteConst) ir.root().statements().get(0);
    assertThat(write.constantId()).isEqualTo(0);
  }

  @Test
  @DisplayName("lowers model reference with direct record access plan")
  void lowersDirectRecordProperty() {
    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("user", VTypes.fromJavaType(User.class, Nullability.NON_NULL)))
            .build();

    String source = "$user.name";
    IrTemplate ir = parseAndLower(source, schema);

    assertThat(ir.parameters()).hasSize(1);
    assertThat(ir.parameters().get(0).name()).isEqualTo("user");

    assertThat(ir.root().statements()).hasSize(1);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrWriteValue.class);
    IrWriteValue write = (IrWriteValue) ir.root().statements().get(0);

    assertThat(write.value()).isInstanceOf(IrGetProperty.class);
    IrGetProperty prop = (IrGetProperty) write.value();
    assertThat(prop.propertyName()).isEqualTo("name");
    assertThat(prop.type().typeName()).isEqualTo("java.lang.String");
    assertThat(prop.accessPlan()).isInstanceOf(AccessPlan.DirectRecord.class);

    assertThat(prop.receiver()).isInstanceOf(IrLoadParam.class);
    IrLoadParam loadParam = (IrLoadParam) prop.receiver();
    assertThat(loadParam.name()).isEqualTo("user");
  }

  @Test
  @DisplayName("lowers set directive to store local and subsequent read to load local")
  void lowersSetAndLocalVar() {
    String source = "#set($score = 100)$score";
    IrTemplate ir = parseAndLower(source, ModelSchema.empty());

    assertThat(ir.root().statements()).hasSize(2);

    // 1. Store local
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrStoreLocal.class);
    IrStoreLocal store = (IrStoreLocal) ir.root().statements().get(0);
    assertThat(store.local().name()).isEqualTo("score");
    assertThat(store.value()).isInstanceOf(IrConst.class);
    assertThat(((IrConst) store.value()).value()).isEqualTo(100);

    // 2. Write value loading local
    assertThat(ir.root().statements().get(1)).isInstanceOf(IrWriteValue.class);
    IrWriteValue write = (IrWriteValue) ir.root().statements().get(1);
    assertThat(write.value()).isInstanceOf(IrLoadLocal.class);
    IrLoadLocal load = (IrLoadLocal) write.value();
    assertThat(load.name()).isEqualTo("score");
  }

  @Test
  @DisplayName("lowers if-elseif-else to structured IrIf")
  void lowersIfElseifElse() {
    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("isAdmin", VTypes.BOOLEAN))
            .add(ModelParameter.of("isModerator", VTypes.BOOLEAN))
            .build();

    String source = "#if($isAdmin)Admin#elseif($isModerator)Mod#else User#end";
    IrTemplate ir = parseAndLower(source, schema);

    assertThat(ir.root().statements()).hasSize(1);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrIf.class);
    IrIf topIf = (IrIf) ir.root().statements().get(0);

    assertThat(topIf.condition()).isInstanceOf(IrLoadParam.class);
    assertThat(((IrLoadParam) topIf.condition()).name()).isEqualTo("isAdmin");
    assertThat(topIf.thenBlock().statements()).hasSize(1);

    // Else block contains nested IrIf for elseif
    assertThat(topIf.elseBlock()).isPresent();
    IrBlock elseBlock = topIf.elseBlock().get();
    assertThat(elseBlock.statements()).hasSize(1);
    assertThat(elseBlock.statements().get(0)).isInstanceOf(IrIf.class);

    IrIf elifIf = (IrIf) elseBlock.statements().get(0);
    assertThat(((IrLoadParam) elifIf.condition()).name()).isEqualTo("isModerator");
    assertThat(elifIf.elseBlock()).isPresent();
  }

  @Test
  @DisplayName("lowers foreach loop with determined LoopPlan")
  void lowersForeachLoop() {
    VType listType = VTypes.fromJavaType(ItemListHolder.class, Nullability.NON_NULL);
    ModelSchema schema = ModelSchema.builder().add(ModelParameter.of("holder", listType)).build();

    String source = "#foreach($item in $holder.items)$item#else Empty#end";
    IrTemplate ir = parseAndLower(source, schema);

    assertThat(ir.root().statements()).hasSize(1);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrLoop.class);
    IrLoop loop = (IrLoop) ir.root().statements().get(0);

    assertThat(loop.plan()).isEqualTo(LoopPlan.LIST_INDEXED);
    assertThat(loop.elementLocal().name()).isEqualTo("item");
    assertThat(loop.loopStateLocal()).isPresent();
    assertThat(loop.loopStateLocal().get().name()).isEqualTo("foreach");
    assertThat(loop.elseBody()).isPresent();
  }

  @Test
  @DisplayName("assigns deterministic distinct slots to nested foreach bindings")
  void assignsDeterministicNestedForeachSlots() {
    VType holderType = VTypes.fromJavaType(ItemListHolder.class, Nullability.NON_NULL);
    ModelSchema schema = ModelSchema.builder().add(ModelParameter.of("holder", holderType)).build();
    String source =
        "#foreach($item in $holder.items)#foreach($item in"
            + " $holder.items)$item:$foreach.parent.count#end$item#end";
    IrTemplate first = parseAndLower(source, schema);
    IrTemplate second = parseAndLower(source, schema);

    IrLoop outer = (IrLoop) first.root().statements().get(0);
    IrLoop inner =
        (IrLoop)
            outer.body().statements().stream()
                .filter(IrLoop.class::isInstance)
                .findFirst()
                .orElseThrow();

    assertThat(outer.elementLocal().slot()).isNotEqualTo(inner.elementLocal().slot());
    assertThat(outer.loopStateLocal().orElseThrow().slot())
        .isNotEqualTo(inner.loopStateLocal().orElseThrow().slot());
    assertThat(IrSlotLayout.frameSize(first)).isEqualTo(IrSlotLayout.frameSize(second));

    IrLoop secondOuter = (IrLoop) second.root().statements().get(0);
    assertThat(secondOuter.elementLocal().slot()).isEqualTo(outer.elementLocal().slot());
    assertThat(secondOuter.loopStateLocal().orElseThrow().slot())
        .isEqualTo(outer.loopStateLocal().orElseThrow().slot());
  }

  @Test
  @DisplayName("lowers include and parse directives")
  void lowersIncludeAndParse() {
    String source = "#include('header.txt')#parse('body.vtl')";
    IrTemplate ir = parseAndLower(source, ModelSchema.empty());

    assertThat(ir.root().statements()).hasSize(2);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrCallTemplate.class);
    IrCallTemplate inc = (IrCallTemplate) ir.root().statements().get(0);
    assertThat(inc.isParse()).isFalse();
    assertThat(inc.staticTemplateName()).contains("header.txt");

    assertThat(ir.root().statements().get(1)).isInstanceOf(IrCallTemplate.class);
    IrCallTemplate parse = (IrCallTemplate) ir.root().statements().get(1);
    assertThat(parse.isParse()).isTrue();
    assertThat(parse.staticTemplateName()).contains("body.vtl");
  }

  @Test
  @DisplayName("lowers break and stop control signals")
  void lowersBreakAndStop() {
    String source = "#break#stop";
    IrTemplate ir = parseAndLower(source, ModelSchema.empty());

    assertThat(ir.root().statements()).hasSize(2);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrBreak.class);
    assertThat(ir.root().statements().get(1)).isInstanceOf(IrStop.class);
  }

  @Test
  @DisplayName("lowers macro definition and invocation")
  void lowersMacro() {
    String source = "#macro(greet $name)Hello $name#end#greet('World')";
    IrTemplate ir = parseAndLower(source, ModelSchema.empty());

    assertThat(ir.functions()).hasSize(1);
    IrFunction fn = ir.functions().get(0);
    assertThat(fn.name()).isEqualTo("greet");
    assertThat(fn.parameters()).hasSize(1);
    assertThat(fn.parameters().get(0).name()).isEqualTo("name");

    assertThat(ir.root().statements()).hasSize(1);
    assertThat(ir.root().statements().get(0)).isInstanceOf(IrCallMacro.class);
    IrCallMacro call = (IrCallMacro) ir.root().statements().get(0);
    assertThat(call.macroName()).isEqualTo("greet");
    assertThat(call.arguments()).hasSize(1);
    assertThat(((IrConst) call.arguments().get(0)).value()).isEqualTo("World");
  }

  @Test
  @DisplayName("lowers binary arithmetic and comparisons")
  void lowersBinaryOperations() {
    String source = "#set($x = 1 + 2 * 3)#set($y = $x > 5)";
    IrTemplate ir = parseAndLower(source, ModelSchema.empty());

    assertThat(ir.root().statements()).hasSize(2);

    IrStoreLocal setX = (IrStoreLocal) ir.root().statements().get(0);
    assertThat(setX.value()).isInstanceOf(IrBinaryOp.class);
    IrBinaryOp add = (IrBinaryOp) setX.value();
    assertThat(add.op()).isEqualTo(BinaryOpKind.ADD);
    assertThat(add.right()).isInstanceOf(IrBinaryOp.class);
    assertThat(((IrBinaryOp) add.right()).op()).isEqualTo(BinaryOpKind.MULTIPLY);

    IrStoreLocal setY = (IrStoreLocal) ir.root().statements().get(1);
    assertThat(setY.value()).isInstanceOf(IrBinaryOp.class);
    IrBinaryOp cmp = (IrBinaryOp) setY.value();
    assertThat(cmp.op()).isEqualTo(BinaryOpKind.GREATER_THAN);
  }
}
