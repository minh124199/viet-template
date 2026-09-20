package io.github.minh124199.viettemplate.benchmarks.comparative;

/**
 * Common adapter interface for benchmarking template engines under identical evaluation semantics
 * and fixture models.
 */
public interface BenchmarkEngineAdapter extends AutoCloseable {

  /** Human-readable engine identifier. */
  String name();

  /** Precompiles/parses and warms all comparative benchmark templates. */
  void setup();

  /**
   * Renders the specified comparative workload using the provided data model.
   *
   * @param workload workload identifier (C01 to C08)
   * @param model data model for the workload
   * @return rendered output string
   */
  String render(String workload, Object model);

  @Override
  default void close() {}
}
