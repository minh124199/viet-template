package io.github.minh124199.viettemplate.vtl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.minh124199.viettemplate.language.vtl.ast.VtlTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.IrTemplate;
import io.github.minh124199.viettemplate.language.vtl.ir.lowering.AstToIrLowerer;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizationOptions;
import io.github.minh124199.viettemplate.language.vtl.ir.optimization.IrOptimizer;
import io.github.minh124199.viettemplate.language.vtl.parser.VtlParser;
import io.github.minh124199.viettemplate.language.vtl.semantics.SemanticAnalysisResult;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticAnalyzer;
import io.github.minh124199.viettemplate.language.vtl.semantics.VtlSemanticOptions;
import io.github.minh124199.viettemplate.language.vtl.source.SourceText;
import io.github.minh124199.viettemplate.runtime.MapRenderContext;
import io.github.minh124199.viettemplate.runtime.StringTemplateOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OptimizerInterpreterEquivalenceTest {

  record EquivalenceCase(String name, String template, Map<String, Object> context) {
    @Override
    public String toString() {
      return name;
    }
  }

  public record User(String name, int age) {}

  static List<EquivalenceCase> cases() {
    List<EquivalenceCase> list = new ArrayList<>();

    list.add(
        new EquivalenceCase(
            "constant folding and dead branches",
            "#if(10 > 5 && 2 < 3)Branch A#{else}Branch B#end\n"
                + "#if(5 == 6)Dead Then#{else}Live Else#end\n"
                + "Math: #(10 + 5 * 2) String: #('Hello ' + 'World')",
            Map.of()));

    list.add(
        new EquivalenceCase(
            "adjacent text constants", "Part 1 " + "Part 2 " + "Part 3\n" + "Line 2", Map.of()));

    list.add(
        new EquivalenceCase(
            "foreach loop with range and break",
            "#foreach($i in [1..10])#if($i == 5)#break#end$i #end",
            Map.of()));

    Map<String, Object> recordCtx = new LinkedHashMap<>();
    recordCtx.put("user", new User("Alice", 30));
    list.add(
        new EquivalenceCase(
            "record property access", "Name: $user.name, Age: $user.age", recordCtx));

    Map<String, Object> mapCtx = new LinkedHashMap<>();
    mapCtx.put("meta", Map.of("key1", "val1", "key2", "val2"));
    list.add(new EquivalenceCase("map property access", "K1: $meta.key1, K2: $meta.key2", mapCtx));

    list.add(
        new EquivalenceCase(
            "macro definition and call",
            "#macro(item $name $val)- $name: $val\n#end"
                + "#item('itemA', 100)"
                + "#item('itemB', 200)",
            Map.of()));

    list.add(
        new EquivalenceCase(
            "nested conditional logic with variables",
            "#set($x = 42)\n" + "#if($x > 50)Large#{elseif}($x > 40)Medium#{else}Small#end",
            Map.of()));

    return list;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("unoptimized IR and optimized IR (O2, O3) render identically")
  void verifyOptimizedRenderEquivalence(EquivalenceCase scenario) throws IOException {
    SourceText source = SourceText.of(scenario.name() + ".vm", scenario.template());
    VtlTemplate ast = VtlParser.parse(source).template();

    VtlInterpreterOptions baseOptions =
        VtlInterpreterOptions.builder().executionTier(ExecutionTier.IR).build();
    BitSet gobbled = SpaceGobbler.computeGobbledIndices(source, baseOptions.spaceGobbling());
    VtlSemanticOptions semanticOptions =
        VtlSemanticOptions.builder()
            .profile(baseOptions.profile())
            .strictMode(baseOptions.strictReferences())
            .allowArbitraryMethods(baseOptions.profile().isArbitraryMethodsAllowed())
            .build();
    SemanticAnalysisResult analysis = VtlSemanticAnalyzer.analyze(ast, semanticOptions);
    IrTemplate rawIr = AstToIrLowerer.lower(ast, source, analysis, semanticOptions, gobbled);

    // 1. Unoptimized (O0)
    IrTemplate unoptimized = IrOptimizer.optimize(rawIr, IrOptimizationOptions.o0());
    StringTemplateOutput outUnoptimized = new StringTemplateOutput();
    IrInterpreter.render(
        unoptimized,
        source,
        new ExecutionContext(MapRenderContext.of(scenario.context())),
        outUnoptimized,
        baseOptions);

    // 2. Standard Optimization (O2)
    IrTemplate optO2 = IrOptimizer.optimize(rawIr, IrOptimizationOptions.o2());
    StringTemplateOutput outO2 = new StringTemplateOutput();
    IrInterpreter.render(
        optO2,
        source,
        new ExecutionContext(MapRenderContext.of(scenario.context())),
        outO2,
        baseOptions);

    // 3. Aggressive Optimization (O3)
    IrTemplate optO3 = IrOptimizer.optimize(rawIr, IrOptimizationOptions.o3());
    StringTemplateOutput outO3 = new StringTemplateOutput();
    IrInterpreter.render(
        optO3,
        source,
        new ExecutionContext(MapRenderContext.of(scenario.context())),
        outO3,
        baseOptions);

    assertThat(outO2.toString())
        .as("O2 optimized IR must match unoptimized IR for %s", scenario.name())
        .isEqualTo(outUnoptimized.toString());

    assertThat(outO3.toString())
        .as("O3 optimized IR must match unoptimized IR for %s", scenario.name())
        .isEqualTo(outUnoptimized.toString());
  }
}
