package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.vtl.interpreter.EvaluationValue;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionContext;
import io.github.minh124199.viettemplate.vtl.interpreter.ForeachMetadata;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures runtime scope stack management and traversal for {@code #foreach} loops, macro
 * invocations, and deeply nested composite scopes.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class NestedScopeBenchmark {

  private ExecutionContext context;
  private EvaluationValue loopValue;
  private EvaluationValue macroArgValue;
  private ForeachMetadata rootForeachMetadata;

  @Setup(Level.Trial)
  public void setUp() {
    RenderContext rootContext =
        RenderContext.builder()
            .put("rootVar", "rootValue")
            .put("globalConfig", "configValue")
            .build();
    context = new ExecutionContext(rootContext);
    context.set("templateVar", EvaluationValue.of("templateValue"));

    loopValue = EvaluationValue.of("loopItem");
    macroArgValue = EvaluationValue.of("macroArg");
    rootForeachMetadata = new ForeachMetadata(0, 1, true, false, true, null);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    context = null;
  }

  @Benchmark
  public void pushPopForeachScope(Blackhole bh) {
    context.pushForeachScope("item", loopValue, rootForeachMetadata);
    EvaluationValue item = context.lookup("item");
    EvaluationValue foreach = context.lookup("foreach");
    context.popScope();

    bh.consume(item);
    bh.consume(foreach);
  }

  @Benchmark
  public void pushPopMacroScope(Blackhole bh) {
    Map<String, EvaluationValue> bindings = new HashMap<>();
    bindings.put("arg1", macroArgValue);
    bindings.put("arg2", loopValue);

    context.pushScope(bindings, false);
    EvaluationValue arg1 = context.lookup("arg1");
    EvaluationValue root = context.lookup("rootVar");
    context.popScope();

    bh.consume(arg1);
    bh.consume(root);
  }

  @Benchmark
  public void nestedForeachAndMacroScopes(Blackhole bh) {
    // 1. Outer Foreach Scope
    context.pushForeachScope("outerItem", loopValue, rootForeachMetadata);

    // 2. Inner Foreach Scope
    ForeachMetadata innerMetadata =
        new ForeachMetadata(0, 1, true, false, true, rootForeachMetadata);
    context.pushForeachScope("innerItem", loopValue, innerMetadata);

    // 3. Macro Scope inside nested loop
    Map<String, EvaluationValue> macroBindings = new HashMap<>();
    macroBindings.put("macroParam", macroArgValue);
    context.pushScope(macroBindings, false);

    // Lookup across all stack tiers
    EvaluationValue param = context.lookup("macroParam");
    EvaluationValue inner = context.lookup("innerItem");
    EvaluationValue outer = context.lookup("outerItem");
    EvaluationValue template = context.lookup("templateVar");
    EvaluationValue root = context.lookup("rootVar");

    context.popScope();
    context.popScope();
    context.popScope();

    bh.consume(param);
    bh.consume(inner);
    bh.consume(outer);
    bh.consume(template);
    bh.consume(root);
  }
}
