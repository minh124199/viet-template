package io.github.minh124199.viettemplate.language.vtl.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.SourceSpan;
import io.github.minh124199.viettemplate.api.TemplateId;
import io.github.minh124199.viettemplate.language.vtl.VtlProfile;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout.BindingKind;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout.InitializationPolicy;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout.SlotLayout;
import io.github.minh124199.viettemplate.language.vtl.ir.IrSlotLayout.SlotMetadata;
import io.github.minh124199.viettemplate.language.vtl.ir.constant.IrConstantPool;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.AssignVariableSlots;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.OptimizationContext;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrLoop;
import io.github.minh124199.viettemplate.language.vtl.ir.statement.IrNoOp;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.semantics.capability.TemplateCapabilities;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelParameter;
import io.github.minh124199.viettemplate.language.vtl.semantics.model.ModelSchema;
import io.github.minh124199.viettemplate.language.vtl.semantics.type.VTypes;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Section 21: slot layout verifier tests covering slot layout invariants.
 *
 * <ul>
 *   <li>Deterministic slot assignment (identical compilation produces same slot IDs).
 *   <li>No duplicate slot for distinct simultaneously live lexical bindings.
 *   <li>Nested $foreach bindings receive independent slot identities.
 *   <li>Macro parameter and local bindings are independent.
 *   <li>Slot count is stable for identical template compilation.
 *   <li>Repeated optimization does not change slot numbering unexpectedly.
 * </ul>
 */
class IrSlotLayoutTest {

  private final SourceSpan span = new SourceSpan(0, 10, 1, 1, 1, 11);

  private IrTemplate parseAndLower(String sourceStr, ModelSchema schema) {
    SourceText source = SourceText.of("test.vtl", sourceStr);
    VtlParseResult parsed = VtlParser.parse(source);
    if (parsed.hasErrors()) {
      throw new AssertionError("Parse errors: " + parsed.diagnostics());
    }
    VtlSemanticOptions options = VtlSemanticOptions.of(VtlProfile.VTL_DYNAMIC, schema);
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(parsed.template(), options);
    if (analysis.hasErrors()) {
      throw new AssertionError("Semantic errors: " + analysis.diagnostics());
    }
    return AstToIrLowerer.lower(parsed.template(), source, analysis, options);
  }

  @Test
  @DisplayName("deterministic slot assignment across identical compilation runs")
  void deterministicSlotAssignment() {
    String template =
        """
        #macro(helper $param1 $param2)
          #set($macroLocal = $param1)
          [$macroLocal:$param2]
        #end
        #set($templateLocal1 = $rootParam)
        #set($templateLocal2 = 42)
        #foreach($item in $items)
          #set($loopLocal = $item)
          [$loopLocal]
          #helper($loopLocal, 'fixed')
        #end
        """;

    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("rootParam", VTypes.STRING))
            .add(ModelParameter.of("items", VTypes.fromJavaClass(List.class)))
            .build();

    IrTemplate ir1 = parseAndLower(template, schema);
    IrTemplate ir2 = parseAndLower(template, schema);

    SlotLayout layout1 = IrSlotLayout.layout(ir1);
    SlotLayout layout2 = IrSlotLayout.layout(ir2);

    assertThat(layout1.frameSize()).isEqualTo(layout2.frameSize());
    assertThat(layout1.slots().keySet()).isEqualTo(layout2.slots().keySet());

    for (Map.Entry<Integer, SlotMetadata> entry : layout1.slots().entrySet()) {
      int slot = entry.getKey();
      SlotMetadata meta1 = entry.getValue();
      SlotMetadata meta2 = layout2.slots().get(slot);

      assertThat(meta2).isNotNull();
      assertThat(meta2.slot()).isEqualTo(meta1.slot());
      assertThat(meta2.name()).isEqualTo(meta1.name());
      assertThat(meta2.kind()).isEqualTo(meta1.kind());
      assertThat(meta2.policy()).isEqualTo(meta1.policy());
    }

    assertThat(layout1.seededSlots()).hasSize(layout2.seededSlots().size());
    for (int i = 0; i < layout1.seededSlots().size(); i++) {
      SlotMetadata s1 = layout1.seededSlots().get(i);
      SlotMetadata s2 = layout2.seededSlots().get(i);
      assertThat(s1.slot()).isEqualTo(s2.slot());
      assertThat(s1.name()).isEqualTo(s2.name());
    }

    assertThat(IrSlotLayout.bindings(ir1)).isEqualTo(IrSlotLayout.bindings(ir2));

    // Also verify macro functions deterministic slot layout
    assertThat(ir1.functions()).hasSize(1);
    assertThat(ir2.functions()).hasSize(1);
    IrFunction fn1 = ir1.functions().get(0);
    IrFunction fn2 = ir2.functions().get(0);

    SlotLayout fnLayout1 = IrSlotLayout.layout(fn1);
    SlotLayout fnLayout2 = IrSlotLayout.layout(fn2);

    assertThat(fnLayout1.frameSize()).isEqualTo(fnLayout2.frameSize());
    assertThat(fnLayout1.slots().keySet()).isEqualTo(fnLayout2.slots().keySet());
    for (Map.Entry<Integer, SlotMetadata> entry : fnLayout1.slots().entrySet()) {
      SlotMetadata m1 = entry.getValue();
      SlotMetadata m2 = fnLayout2.slots().get(entry.getKey());
      assertThat(m2.name()).isEqualTo(m1.name());
      assertThat(m2.kind()).isEqualTo(m1.kind());
      assertThat(m2.policy()).isEqualTo(m1.policy());
    }
  }

  @Test
  @DisplayName("no duplicate slot for distinct simultaneously live lexical bindings")
  void noDuplicateSlotForDistinctSimultaneouslyLiveLexicalBindings() {
    String template =
        """
        #set($a = 10)
        #set($b = 20)
        #foreach($item in $items)
          #set($inner = $item)
          [$p1:$p2:$a:$b:$item:$inner]
        #end
        """;

    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("p1", VTypes.STRING))
            .add(ModelParameter.of("p2", VTypes.INT))
            .add(ModelParameter.of("items", VTypes.fromJavaClass(List.class)))
            .build();

    IrTemplate ir = parseAndLower(template, schema);
    SlotLayout layout = IrSlotLayout.layout(ir);

    // Assert that every simultaneously live binding has an independent unique slot ID
    Map<Integer, SlotMetadata> slots = layout.slots();
    Set<Integer> uniqueSlots = new HashSet<>(slots.keySet());
    assertThat(uniqueSlots).hasSize(slots.size());

    // Verified via O45 AssignVariableSlots pass
    AssignVariableSlots pass = new AssignVariableSlots();
    OptimizationContext ctx = new OptimizationContext(ir, IrOptimizationOptions.o0());
    IrTemplate validated = pass.run(ir, ctx);
    assertThat(validated).isSameAs(ir);

    // Verify negative validation: duplicate slot for distinct bindings fails fast
    IrParameter paramA = new IrParameter("x", VTypes.STRING, 0, span);
    IrParameter paramB = new IrParameter("y", VTypes.STRING, 0, span); // same slot 0
    IrTemplate malformed =
        new IrTemplate(
            TemplateId.of("malformed.vtl"),
            List.of(paramA, paramB),
            IrBlock.of(span, new IrNoOp(span)),
            new IrConstantPool(),
            TemplateCapabilities.empty(),
            List.of(),
            span);

    assertThatThrownBy(() -> pass.run(malformed, ctx))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("aliases bindings 'x' and 'y'");
  }

  @Test
  @DisplayName("nested foreach bindings receive independent slot identities")
  void nestedForeachBindingsReceiveIndependentSlotIdentities() {
    String template =
        """
        #foreach($outer in $outerItems)
          #set($outerLocal = $outer)
          #foreach($inner in $innerItems)
            #set($innerLocal = $inner)
            [$outer:$outerLocal:$inner:$innerLocal]
          #end
        #end
        """;

    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("outerItems", VTypes.fromJavaClass(List.class)))
            .add(ModelParameter.of("innerItems", VTypes.fromJavaClass(List.class)))
            .build();

    IrTemplate ir = parseAndLower(template, schema);
    SlotLayout layout = IrSlotLayout.layout(ir);

    IrLoop outerLoop =
        (IrLoop)
            ir.root().statements().stream()
                .filter(IrLoop.class::isInstance)
                .findFirst()
                .orElseThrow();
    IrLoop innerLoop =
        (IrLoop)
            outerLoop.body().statements().stream()
                .filter(IrLoop.class::isInstance)
                .findFirst()
                .orElseThrow();

    int outerItemSlot = outerLoop.elementLocal().slot();
    int outerStateSlot = outerLoop.loopStateLocal().orElseThrow().slot();
    int innerItemSlot = innerLoop.elementLocal().slot();
    int innerStateSlot = innerLoop.loopStateLocal().orElseThrow().slot();

    // Assert that nested loop element and state slots are strictly independent
    assertThat(outerItemSlot).isNotEqualTo(innerItemSlot);
    assertThat(outerStateSlot).isNotEqualTo(innerStateSlot);
    assertThat(outerItemSlot).isNotEqualTo(outerStateSlot);
    assertThat(innerItemSlot).isNotEqualTo(innerStateSlot);

    // Check loop-owned locals
    List<Integer> outerOwned = layout.loopLocals().get(outerLoop);
    List<Integer> innerOwned = layout.loopLocals().get(innerLoop);

    assertThat(outerOwned).isNotNull();
    assertThat(innerOwned).isNotNull();

    // All slots must be distinct and appropriately categorized
    Set<Integer> allLoopSlots =
        Set.of(outerItemSlot, outerStateSlot, innerItemSlot, innerStateSlot);
    assertThat(allLoopSlots).hasSize(4);

    SlotMetadata outerItemMeta = layout.slots().get(outerItemSlot);
    assertThat(outerItemMeta.kind()).isEqualTo(BindingKind.FOREACH_ITEM);
    assertThat(outerItemMeta.policy()).isEqualTo(InitializationPolicy.ITERATION_MANAGED);

    SlotMetadata outerStateMeta = layout.slots().get(outerStateSlot);
    assertThat(outerStateMeta.kind()).isEqualTo(BindingKind.FOREACH_METADATA);
    assertThat(outerStateMeta.policy()).isEqualTo(InitializationPolicy.ITERATION_MANAGED);

    SlotMetadata innerItemMeta = layout.slots().get(innerItemSlot);
    assertThat(innerItemMeta.kind()).isEqualTo(BindingKind.FOREACH_ITEM);

    SlotMetadata innerStateMeta = layout.slots().get(innerStateSlot);
    assertThat(innerStateMeta.kind()).isEqualTo(BindingKind.FOREACH_METADATA);
  }

  @Test
  @DisplayName("macro parameter and local bindings are independent")
  void macroParameterAndLocalBindingsAreIndependent() {
    String template =
        """
        #macro(m1 $p1 $p2)
          #set($m1Local = 'local1')
          [$p1:$p2:$m1Local]
        #end
        #macro(m2 $q1)
          #set($m2Local = 'local2')
          [$q1:$m2Local]
        #end
        #set($templateLocal = 'rootVal')
        """;

    IrTemplate ir = parseAndLower(template, ModelSchema.empty());
    SlotLayout templateLayout = IrSlotLayout.layout(ir);

    assertThat(ir.functions()).hasSize(2);
    IrFunction m1 = ir.functions().get(0);
    IrFunction m2 = ir.functions().get(1);

    SlotLayout layout1 = IrSlotLayout.layout(m1);
    SlotLayout layout2 = IrSlotLayout.layout(m2);

    // Macro 1 parameter and local verification
    SlotMetadata p1Meta = layout1.slots().get(m1.parameters().get(0).slot());
    assertThat(p1Meta.name()).isEqualTo("p1");
    assertThat(p1Meta.kind()).isEqualTo(BindingKind.MACRO_PARAMETER);
    assertThat(p1Meta.policy()).isEqualTo(InitializationPolicy.INVOCATION_PARAM);

    SlotMetadata p2Meta = layout1.slots().get(m1.parameters().get(1).slot());
    assertThat(p2Meta.name()).isEqualTo("p2");
    assertThat(p2Meta.kind()).isEqualTo(BindingKind.MACRO_PARAMETER);
    assertThat(p2Meta.policy()).isEqualTo(InitializationPolicy.INVOCATION_PARAM);

    assertThat(p1Meta.slot()).isNotEqualTo(p2Meta.slot());

    // Macro 2 has independent frame layout starting with its own parameters
    SlotMetadata q1Meta = layout2.slots().get(m2.parameters().get(0).slot());
    assertThat(q1Meta.name()).isEqualTo("q1");
    assertThat(q1Meta.kind()).isEqualTo(BindingKind.MACRO_PARAMETER);

    // Template-level locals belong to template layout, not macro layouts
    SlotMetadata tmplLocalMeta =
        templateLayout.slots().values().stream()
            .filter(m -> "templateLocal".equals(m.name()))
            .findFirst()
            .orElseThrow();
    assertThat(tmplLocalMeta.kind()).isEqualTo(BindingKind.TEMPLATE_LOCAL);
    assertThat(tmplLocalMeta.policy()).isEqualTo(InitializationPolicy.SEEDED_FROM_CONTEXT);
  }

  @Test
  @DisplayName("slot count is stable for identical template compilation")
  void slotCountIsStableForIdenticalTemplateCompilation() {
    String template =
        """
        #macro(sampleMacro $x)
          #set($inner = $x)
          $inner
        #end
        #set($val1 = $input)
        #set($val2 = 'constant')
        #foreach($row in $rows)
          #set($cell = $row)
          [$cell]
        #end
        """;

    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("input", VTypes.STRING))
            .add(ModelParameter.of("rows", VTypes.fromJavaClass(List.class)))
            .build();

    IrTemplate baseline = parseAndLower(template, schema);
    SlotLayout baselineLayout = IrSlotLayout.layout(baseline);
    int expectedFrameSize = baselineLayout.frameSize();
    int expectedSlotCount = baselineLayout.slots().size();
    int expectedSeededCount = baselineLayout.seededSlots().size();
    Map<Integer, String> expectedBindings = IrSlotLayout.bindings(baseline);

    for (int i = 0; i < 10; i++) {
      IrTemplate compiled = parseAndLower(template, schema);
      SlotLayout layout = IrSlotLayout.layout(compiled);

      assertThat(layout.frameSize()).isEqualTo(expectedFrameSize);
      assertThat(layout.slots().size()).isEqualTo(expectedSlotCount);
      assertThat(layout.seededSlots().size()).isEqualTo(expectedSeededCount);
      assertThat(IrSlotLayout.bindings(compiled)).isEqualTo(expectedBindings);
    }
  }

  @Test
  @DisplayName("repeated optimization does not change slot numbering unexpectedly")
  void repeatedOptimizationDoesNotChangeSlotNumberingUnexpectedly() {
    String template =
        """
        #macro(renderItem $elem)
          #set($formatted = "Item: $elem")
          [$formatted]
        #end
        #set($heading = 'List')
        #foreach($item in $items)
          #if($item)
            #set($val = $item)
            #renderItem($val)
          #end
        #end
        """;

    ModelSchema schema =
        ModelSchema.builder()
            .add(ModelParameter.of("items", VTypes.fromJavaClass(List.class)))
            .build();

    IrTemplate unoptimized = parseAndLower(template, schema);
    SlotLayout unoptLayout = IrSlotLayout.layout(unoptimized);

    // Optimize across O0, O1, O2, O3
    List<IrOptimizationOptions> levels =
        List.of(
            IrOptimizationOptions.o0(),
            IrOptimizationOptions.o1(),
            IrOptimizationOptions.o2(),
            IrOptimizationOptions.o3());

    AssignVariableSlots assignPass = new AssignVariableSlots();

    for (IrOptimizationOptions optLevel : levels) {
      IrTemplate opt = IrOptimizer.optimize(unoptimized, optLevel);
      OptimizationContext ctx = new OptimizationContext(opt, optLevel);
      assignPass.run(opt, ctx);

      SlotLayout optLayout = IrSlotLayout.layout(opt);
      // For any variable retained in the optimized IR, slot ID must not change
      for (Map.Entry<Integer, SlotMetadata> entry : optLayout.slots().entrySet()) {
        int slot = entry.getKey();
        SlotMetadata meta = entry.getValue();
        if (unoptLayout.slots().containsKey(slot)) {
          SlotMetadata originalMeta = unoptLayout.slots().get(slot);
          assertThat(meta.name()).isEqualTo(originalMeta.name());
          assertThat(meta.kind()).isEqualTo(originalMeta.kind());
        }
      }
    }

    // Repeated multi-pass optimization idempotence
    IrTemplate pass1 = IrOptimizer.optimize(unoptimized, IrOptimizationOptions.o2());
    IrTemplate pass2 = IrOptimizer.optimize(pass1, IrOptimizationOptions.o2());
    IrTemplate pass3 = IrOptimizer.optimize(pass2, IrOptimizationOptions.o3());

    SlotLayout layoutP1 = IrSlotLayout.layout(pass1);
    SlotLayout layoutP2 = IrSlotLayout.layout(pass2);
    SlotLayout layoutP3 = IrSlotLayout.layout(pass3);

    assertThat(layoutP2.slots().keySet()).isEqualTo(layoutP1.slots().keySet());
    assertThat(layoutP3.slots().keySet()).isEqualTo(layoutP2.slots().keySet());
    assertThat(IrSlotLayout.frameSize(pass2)).isEqualTo(IrSlotLayout.frameSize(pass1));
    assertThat(IrSlotLayout.frameSize(pass3)).isEqualTo(IrSlotLayout.frameSize(pass2));
  }

  @Test
  @DisplayName("records representative frame sizes and slot counts for benchmark workloads")
  void representativeWorkloadFrameSizes() {
    // 1. Workload B02 (with 50 declared model parameters)
    ModelSchema.Builder b02SchemaBuilder = ModelSchema.builder();
    StringBuilder b02Source = new StringBuilder();
    for (int i = 0; i < 50; i++) {
      b02SchemaBuilder.add(ModelParameter.of("var_" + i, VTypes.STRING));
      b02Source.append("$var_").append(i).append(" ");
    }
    IrTemplate b02Ir = parseAndLower(b02Source.toString(), b02SchemaBuilder.build());
    SlotLayout b02Layout = IrSlotLayout.layout(b02Ir);
    assertThat(b02Layout.frameSize()).isEqualTo(50);
    assertThat(b02Layout.slots()).hasSize(50);

    // 2. Workload B05 / B06 (Single loop table.vm)
    String tableVm =
        """
        <table>
        #foreach($row in $table)
          <tr><td>$row.col1</td><td>$row.col2</td></tr>
        #end
        </table>
        """;
    IrTemplate tableIr = parseAndLower(tableVm, ModelSchema.empty());
    SlotLayout tableLayout = IrSlotLayout.layout(tableIr);
    assertThat(tableLayout.frameSize()).isEqualTo(2); // $row (slot 0) and $foreach (slot 1)
    assertThat(tableLayout.slots()).hasSize(2);

    // 3. Workload B07 (Nested loop nested.vm with $foreach metadata)
    String nestedVm =
        """
        #foreach($row in $matrix)
          #foreach($item in $row.items)
            [$foreach.count] $item.name (outer: $foreach.parent.count)
          #end
        #end
        """;
    IrTemplate nestedIr = parseAndLower(nestedVm, ModelSchema.empty());
    SlotLayout nestedLayout = IrSlotLayout.layout(nestedIr);
    assertThat(nestedLayout.frameSize())
        .isEqualTo(4); // $row, $foreach (outer), $item, $foreach (inner)
    assertThat(nestedLayout.slots()).hasSize(4);

    // 4. Workload B11 (b11_macros.vm)
    String b11Vm =
        """
        #macro(renderBadge $label $type)
          <span>$label:$type</span>
        #end
        #macro(renderCard $title $desc $tag)
          <div>$title - $desc - #renderBadge($tag, 'primary')</div>
        #end
        #foreach($card in $cards)
          #renderCard($card.title, $card.desc, $card.tag)
        #end
        """;
    IrTemplate b11Ir = parseAndLower(b11Vm, ModelSchema.empty());
    SlotLayout b11TemplateLayout = IrSlotLayout.layout(b11Ir);
    IrFunction renderBadgeFn =
        b11Ir.functions().stream()
            .filter(f -> "renderbadge".equals(f.name()))
            .findFirst()
            .orElseThrow();
    SlotLayout badgeLayout = IrSlotLayout.layout(renderBadgeFn);
    assertThat(badgeLayout.frameSize()).isEqualTo(3); // $label, $type, $bodyContent

    IrFunction renderCardFn =
        b11Ir.functions().stream()
            .filter(f -> "rendercard".equals(f.name()))
            .findFirst()
            .orElseThrow();
    SlotLayout cardLayout = IrSlotLayout.layout(renderCardFn);
    assertThat(cardLayout.frameSize()).isEqualTo(4); // $title, $desc, $tag, $bodyContent

    // 5. Large synthetic template: 200 distinct local variables
    StringBuilder syntheticSource = new StringBuilder();
    for (int i = 0; i < 200; i++) {
      syntheticSource.append("#set($var_").append(i).append(" = ").append(i).append(")\n");
    }
    for (int i = 0; i < 200; i++) {
      syntheticSource.append("$var_").append(i).append(" ");
    }
    IrTemplate syntheticIr = parseAndLower(syntheticSource.toString(), ModelSchema.empty());
    SlotLayout syntheticLayout = IrSlotLayout.layout(syntheticIr);
    assertThat(syntheticLayout.frameSize()).isEqualTo(200);
    assertThat(syntheticLayout.slots()).hasSize(200);
  }
}
