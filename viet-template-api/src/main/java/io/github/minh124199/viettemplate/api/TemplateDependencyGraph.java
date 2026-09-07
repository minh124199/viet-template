package io.github.minh124199.viettemplate.api;

import java.util.Set;

/**
 * Thread-safe directed graph tracking template dependency relationships.
 *
 * <p>Supports reverse dependency traversal to determine transitive dependents for cache
 * invalidation and hot reload.
 */
public interface TemplateDependencyGraph {

  /** Returns direct dependencies of the specified template. */
  Set<TemplateDependency> dependenciesOf(TemplateId templateId);

  /** Returns direct dependents of the specified template (templates that depend on it). */
  Set<TemplateId> dependentsOf(TemplateId templateId);

  /**
   * Returns all transitive dependents of the specified template in cycle-safe traversal order.
   *
   * <p>If template A depends on B, and B depends on C, then {@code transitiveDependentsOf(C)}
   * includes B and A.
   */
  Set<TemplateId> transitiveDependentsOf(TemplateId templateId);

  /**
   * Replaces the set of outgoing dependencies for the specified template.
   *
   * @param templateId source template identifier
   * @param dependencies complete updated set of dependencies for this template
   */
  void replaceDependencies(TemplateId templateId, Set<TemplateDependency> dependencies);

  /** Removes all dependencies originating from or targeting the specified template. */
  void removeTemplate(TemplateId templateId);

  /** Clears all vertices and edges from the graph. */
  void clear();

  /** Returns the total number of tracked template nodes. */
  int size();
}
