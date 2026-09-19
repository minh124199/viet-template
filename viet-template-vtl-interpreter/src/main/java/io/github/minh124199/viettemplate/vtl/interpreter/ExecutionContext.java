package io.github.minh124199.viettemplate.vtl.interpreter;

import io.github.minh124199.viettemplate.api.MutableRenderContext;
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
    EvaluationValue templateValue = templateVariables.get(name);
    if (templateValue != null) {
      return templateValue;
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
    if (rootContext instanceof MutableRenderContext mrc) {
      mrc.put(name, value.asObjectOrNull());
    }
  }

  public void pushScope(Map<String, EvaluationValue> bindings, boolean isolateSet) {
    scopeStack.push(LocalScope.copyOf(bindings, isolateSet));
  }

  /** Pushes a scope whose binding map was created exclusively for this execution context. */
  void pushOwnedScope(Map<String, EvaluationValue> bindings, boolean isolateSet) {
    scopeStack.push(LocalScope.owned(bindings, isolateSet));
  }

  public void pushForeachScope(String loopVar, EvaluationValue loopVal, ForeachMetadata metadata) {
    scopeStack.push(LocalScope.foreach(loopVar, loopVal, metadata));
  }

  void pushForeachScopeWithoutMetadata(String loopVar, EvaluationValue loopVal) {
    scopeStack.push(LocalScope.foreachWithoutMetadata(loopVar, loopVal));
  }

  public void updateLoopVariable(
      String loopVar, EvaluationValue loopVal, ForeachMetadata metadata) {
    if (!scopeStack.isEmpty()) {
      LocalScope top = scopeStack.peek();
      top.put(loopVar, loopVal);
      top.put("foreach", EvaluationValue.of(metadata));
    }
  }

  public void setLocalScope(String name, EvaluationValue value) {
    if (!scopeStack.isEmpty()) {
      scopeStack.peek().put(name, value);
    }
  }

  public void clearLocalScope(String name) {
    if (!scopeStack.isEmpty()) {
      scopeStack.peek().remove(name);
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
    private final Map<String, EvaluationValue> variables;
    private final boolean isolateSet;

    private LocalScope(Map<String, EvaluationValue> variables, boolean isolateSet) {
      this.variables = variables;
      this.isolateSet = isolateSet;
    }

    static LocalScope copyOf(Map<String, EvaluationValue> initial, boolean isolateSet) {
      Map<String, EvaluationValue> variables =
          initial == null ? new HashMap<>() : HashMap.newHashMap(initial.size());
      if (initial != null) {
        variables.putAll(initial);
      }
      return new LocalScope(variables, isolateSet);
    }

    static LocalScope owned(Map<String, EvaluationValue> variables, boolean isolateSet) {
      return new LocalScope(Objects.requireNonNull(variables), isolateSet);
    }

    static LocalScope foreach(String loopVar, EvaluationValue loopVal, ForeachMetadata metadata) {
      Map<String, EvaluationValue> variables = HashMap.newHashMap(2);
      variables.put(Objects.requireNonNull(loopVar), Objects.requireNonNull(loopVal));
      variables.put("foreach", EvaluationValue.of(metadata));
      return new LocalScope(variables, false);
    }

    static LocalScope foreachWithoutMetadata(String loopVar, EvaluationValue loopVal) {
      Map<String, EvaluationValue> variables = HashMap.newHashMap(1);
      variables.put(Objects.requireNonNull(loopVar), Objects.requireNonNull(loopVal));
      return new LocalScope(variables, false);
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

    void remove(String name) {
      variables.remove(name);
    }
  }
}
