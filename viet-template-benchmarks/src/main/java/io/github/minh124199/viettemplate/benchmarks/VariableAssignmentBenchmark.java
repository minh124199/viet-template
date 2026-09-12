package io.github.minh124199.viettemplate.benchmarks;

import io.github.minh124199.viettemplate.api.MutableRenderContext;
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

/**
 * Measures {@link ExecutionContext#set(String, EvaluationValue)} for template-local assignments,
 * local scope updates, foreach scope updates, and mutable root context write-through.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {"-server", "-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC"})
public class VariableAssignmentBenchmark {

  private ExecutionContext templateContext;
  private ExecutionContext localScopeContext;
  private ExecutionContext foreachScopeContext;
  private ExecutionContext writeThroughContext;

  private EvaluationValue testValue;
  private EvaluationValue updateValue;

  @Setup(Level.Trial)
  public void setUp() {
    testValue = EvaluationValue.of("assignedValue");
    updateValue = EvaluationValue.of("updatedValue");

    // 1. Template Local Context
    RenderContext immutableRoot =
        RenderContext.builder().put("initRoot", "val").build();
    templateContext = new ExecutionContext(immutableRoot);

    // 2. Local Scope Context
    localScopeContext = new ExecutionContext(immutableRoot);
    Map<String, EvaluationValue> localBindings = new HashMap<>();
    localBindings.put("localVar", testValue);
    localScopeContext.pushScope(localBindings, false);

    // 3. Foreach Scope Context
    foreachScopeContext = new ExecutionContext(immutableRoot);
    ForeachMetadata metadata = new ForeachMetadata(0, 1, true, false, true, null);
    foreachScopeContext.pushForeachScope("loopVar", testValue, metadata);

    // 4. Mutable Root Context (Write-Through)
    MutableRenderContext mutableRoot = MutableRenderContext.of();
    mutableRoot.put("rootVar", "initial");
    writeThroughContext = new ExecutionContext(mutableRoot);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    templateContext = null;
    localScopeContext = null;
    foreachScopeContext = null;
    writeThroughContext = null;
  }

  @Benchmark
  public void assignTemplateLocal() {
    templateContext.set("tempKey", testValue);
  }

  @Benchmark
  public void updateLocalScopeVariable() {
    localScopeContext.set("localVar", updateValue);
  }

  @Benchmark
  public void updateForeachScopeVariable() {
    foreachScopeContext.set("loopVar", updateValue);
  }

  @Benchmark
  public void writeThroughMutableRootContext() {
    writeThroughContext.set("rootVar", updateValue);
  }
}
