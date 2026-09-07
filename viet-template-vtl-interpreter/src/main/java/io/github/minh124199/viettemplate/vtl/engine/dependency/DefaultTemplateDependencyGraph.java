package io.github.minh124199.viettemplate.vtl.engine.dependency;

import io.github.minh124199.viettemplate.api.TemplateDependency;
import io.github.minh124199.viettemplate.api.TemplateDependencyGraph;
import io.github.minh124199.viettemplate.api.TemplateId;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe directed graph implementation tracking template dependencies and supporting
 * cycle-safe reverse dependency traversal for cache invalidation.
 */
public final class DefaultTemplateDependencyGraph implements TemplateDependencyGraph {

  // forward: source (dependent) -> Set<TemplateDependency> (outgoing edges to dependencies)
  private final Map<TemplateId, Set<TemplateDependency>> forward = new ConcurrentHashMap<>();

  // reverse: target (dependency) -> Set<TemplateId> (incoming edges from dependents)
  private final Map<TemplateId, Set<TemplateId>> reverse = new ConcurrentHashMap<>();

  private final Object graphLock = new Object();

  @Override
  public Set<TemplateDependency> dependenciesOf(TemplateId templateId) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    synchronized (graphLock) {
      Set<TemplateDependency> deps = forward.get(templateId);
      if (deps == null || deps.isEmpty()) {
        return Set.of();
      }
      return Set.copyOf(deps);
    }
  }

  @Override
  public Set<TemplateId> dependentsOf(TemplateId templateId) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    synchronized (graphLock) {
      Set<TemplateId> deps = reverse.get(templateId);
      if (deps == null || deps.isEmpty()) {
        return Set.of();
      }
      return Set.copyOf(deps);
    }
  }

  @Override
  public Set<TemplateId> transitiveDependentsOf(TemplateId templateId) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    synchronized (graphLock) {
      Set<TemplateId> direct = reverse.get(templateId);
      if (direct == null || direct.isEmpty()) {
        return Set.of();
      }

      Set<TemplateId> visited = new HashSet<>();
      visited.add(templateId);
      Set<TemplateId> result = new LinkedHashSet<>();
      Queue<TemplateId> queue = new ArrayDeque<>(direct);

      while (!queue.isEmpty()) {
        TemplateId current = queue.poll();
        if (visited.add(current)) {
          result.add(current);
          Set<TemplateId> nextLevel = reverse.get(current);
          if (nextLevel != null) {
            for (TemplateId next : nextLevel) {
              if (!visited.contains(next)) {
                queue.add(next);
              }
            }
          }
        }
      }
      return Collections.unmodifiableSet(result);
    }
  }

  @Override
  public void replaceDependencies(TemplateId templateId, Set<TemplateDependency> dependencies) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    Objects.requireNonNull(dependencies, "dependencies must not be null");

    synchronized (graphLock) {
      // Remove old outgoing edges from reverse index
      Set<TemplateDependency> existing = forward.get(templateId);
      if (existing != null) {
        for (TemplateDependency dep : existing) {
          Set<TemplateId> rev = reverse.get(dep.target());
          if (rev != null) {
            rev.remove(templateId);
            if (rev.isEmpty()) {
              reverse.remove(dep.target());
            }
          }
        }
      }

      if (dependencies.isEmpty()) {
        forward.remove(templateId);
      } else {
        Set<TemplateDependency> copy = new HashSet<>(dependencies);
        forward.put(templateId, copy);
        for (TemplateDependency dep : copy) {
          reverse.computeIfAbsent(dep.target(), k -> new HashSet<>()).add(templateId);
        }
      }
    }
  }

  @Override
  public void removeTemplate(TemplateId templateId) {
    Objects.requireNonNull(templateId, "templateId must not be null");
    synchronized (graphLock) {
      // Remove forward dependencies
      Set<TemplateDependency> existing = forward.remove(templateId);
      if (existing != null) {
        for (TemplateDependency dep : existing) {
          Set<TemplateId> rev = reverse.get(dep.target());
          if (rev != null) {
            rev.remove(templateId);
            if (rev.isEmpty()) {
              reverse.remove(dep.target());
            }
          }
        }
      }

      // Remove reverse dependencies (this template as a dependency of others)
      Set<TemplateId> dependents = reverse.remove(templateId);
      if (dependents != null) {
        for (TemplateId depId : dependents) {
          Set<TemplateDependency> fwd = forward.get(depId);
          if (fwd != null) {
            fwd.removeIf(d -> d.target().equals(templateId));
            if (fwd.isEmpty()) {
              forward.remove(depId);
            }
          }
        }
      }
    }
  }

  @Override
  public void clear() {
    synchronized (graphLock) {
      forward.clear();
      reverse.clear();
    }
  }

  @Override
  public int size() {
    synchronized (graphLock) {
      Set<TemplateId> all = new HashSet<>(forward.keySet());
      all.addAll(reverse.keySet());
      return all.size();
    }
  }
}
