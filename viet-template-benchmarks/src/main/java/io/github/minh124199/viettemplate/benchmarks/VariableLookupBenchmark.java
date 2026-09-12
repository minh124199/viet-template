package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.RenderContext;
import io.github.minh124199.viettemplate.vtl.interpreter.EvaluationValue;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionContext;
import io.github.minh124199.viettemplate.vtl.interpreter.ExecutionFrame;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures {@link ExecutionContext#lookup(String)} across root context, template-local, and nested
 * local scopes under varying scope depths.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class VariableLookupBenchmark {

  @Param({"0", "1", "4", "8"})
  private int scopeDepth;

  private ExecutionContext context;
  private ExecutionFrame frame;
  private String rootVarName;
  private String templateVarName;
  private String outermostVarName;
  private String innermostVarName;
  private String missingVarName;

  @Setup(Level.Trial)
  public void setUp() {
    RenderContext rootContext =
        RenderContext.builder().put("rootVar", "rootValue").put("globalSetting", 42).build();
    context = new ExecutionContext(rootContext);
    frame = new ExecutionFrame(1);
    frame.set(0, EvaluationValue.of("slotValue"));
    context.set("templateVar", EvaluationValue.of("templateValue"));

    rootVarName = "rootVar";
    templateVarName = "templateVar";
    outermostVarName = "outerVar";
    innermostVarName = "innerVar";
    missingVarName = "nonExistentVar";

    for (int i = 1; i <= scopeDepth; i++) {
      Map<String, EvaluationValue> bindings = new HashMap<>();
      if (i == 1) {
        bindings.put("outerVar", EvaluationValue.of("outerValue"));
      }
      bindings.put("level" + i, EvaluationValue.of(i));
      if (i == scopeDepth) {
        bindings.put("innerVar", EvaluationValue.of("innerValue"));
      }
      context.pushScope(bindings, false);
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    while (!context.templateVariables().isEmpty()) {
      context.templateVariables().clear();
    }
    context = null;
  }

  @Benchmark
  public EvaluationValue lookupRootVariable() {
    return context.lookup(rootVarName);
  }

  /** Static semantic binding lookup; scopeDepth is intentionally orthogonal to the slot index. */
  @Benchmark
  public EvaluationValue lookupStaticSlot() {
    return frame.get(0);
  }

  @Benchmark
  public EvaluationValue lookupTemplateLocalVariable() {
    return context.lookup(templateVarName);
  }

  @Benchmark
  public EvaluationValue lookupOutermostScopeVariable() {
    return context.lookup(outermostVarName);
  }

  @Benchmark
  public EvaluationValue lookupInnermostScopeVariable() {
    return context.lookup(innermostVarName);
  }

  @Benchmark
  public EvaluationValue lookupMissingVariable() {
    return context.lookup(missingVarName);
  }
}
