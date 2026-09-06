package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.RenderContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Render-local execution context wrapping the caller's immutable {@link RenderContext}. Manages
 * template-level variables and local scopes (macros, foreach loops).
 */
public final class ExecutionContext {

  private final RenderContext rootContext;
  private final Map<String, EvaluationValue> templateVariables = new HashMap<>();
  private final Deque<LocalScope> scopeStack = new ArrayDeque<>();

  public ExecutionContext(RenderContext rootContext) {
    this.rootContext = Objects.requireNonNull(rootContext, "rootContext must not be null");
  }

  public EvaluationValue lookup(String name) {
    // 1. Search local scopes (from innermost out)
    for (LocalScope scope : scopeStack) {
      if (scope.contains(name)) {
        return scope.get(name);
      }
    }

    // 2. Search template-local variables (set during render)
    if (templateVariables.containsKey(name)) {
      return templateVariables.get(name);
    }

    // 3. Search caller root RenderContext
    if (rootContext.contains(name)) {
      return EvaluationValue.of(rootContext.get(name));
    }

    // 4. Undefined
    return EvaluationValue.undefined();
  }

  public void set(String name, EvaluationValue value) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(value, "value must not be null");

    // If a local scope explicitly owns this variable (e.g., macro parameter or loop variable),
    // update it.
    for (LocalScope scope : scopeStack) {
      if (scope.contains(name)) {
        scope.put(name, value);
        return;
      }
    }

    // Otherwise, assign at template context level (Velocity-compatible global/template scope)
    templateVariables.put(name, value);
  }

  public void pushScope(Map<String, EvaluationValue> bindings, boolean isolateSet) {
    scopeStack.push(new LocalScope(bindings, isolateSet));
  }

  public void pushForeachScope(String loopVar, EvaluationValue loopVal, ForeachMetadata metadata) {
    Map<String, EvaluationValue> bindings = new HashMap<>();
    bindings.put(loopVar, loopVal);
    bindings.put("foreach", EvaluationValue.of(metadata));
    scopeStack.push(new LocalScope(bindings, false));
  }

  public void updateLoopVariable(
      String loopVar, EvaluationValue loopVal, ForeachMetadata metadata) {
    if (!scopeStack.isEmpty()) {
      LocalScope top = scopeStack.peek();
      top.put(loopVar, loopVal);
      top.put("foreach", EvaluationValue.of(metadata));
    }
  }

  public void popScope() {
    if (!scopeStack.isEmpty()) {
      scopeStack.pop();
    }
  }

  public RenderContext rootContext() {
    return rootContext;
  }

  public Map<String, EvaluationValue> templateVariables() {
    return templateVariables;
  }

  private static final class LocalScope {
    private final Map<String, EvaluationValue> variables = new HashMap<>();
    private final boolean isolateSet;

    LocalScope(Map<String, EvaluationValue> initial, boolean isolateSet) {
      if (initial != null) {
        variables.putAll(initial);
      }
      this.isolateSet = isolateSet;
    }

    boolean contains(String name) {
      return variables.containsKey(name);
    }

    EvaluationValue get(String name) {
      return variables.get(name);
    }

    void put(String name, EvaluationValue value) {
      variables.put(name, value);
    }
  }
}
