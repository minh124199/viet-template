package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.PersonBean;
import io.github.minh124199.viettemplate.tck.velocity.model.Probe;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering error handling, diagnostics, and exceptions. */
public final class ErrorCorpus {

  private ErrorCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Syntax error: bare null literal rejected by default
    list.add(
        CompatibilityScenario.simple(
            "error.syntax.bare-null-disallowed", ScenarioCategory.ERROR, "#set($x = null)"));

    // 2. Method-thrown runtime exception
    list.add(
        CompatibilityScenario.of(
            "error.runtime.method-thrown-exception",
            ScenarioCategory.ERROR,
            "Start;#set($dummy = $probe.fail());End",
            () -> Map.of("probe", new Probe("failProbe"))));

    // 3. Strict mode invalid property access
    CompatibilityConfiguration strictConfig =
        CompatibilityConfiguration.defaultConfiguration().withStrictReferences(true);

    list.add(
        CompatibilityScenario.builder("error.strict.invalid-property", ScenarioCategory.ERROR)
            .template("Value: $person.nonExistentProperty")
            .contextFactory(() -> Map.of("person", new PersonBean("Alice", 25, true, null)))
            .configuration(strictConfig)
            .tags("strict")
            .build());

    return list;
  }
}
