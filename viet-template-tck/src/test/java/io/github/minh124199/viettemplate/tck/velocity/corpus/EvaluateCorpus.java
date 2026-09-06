package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/** Compatibility scenarios covering #evaluate directive for dynamic code execution. */
public final class EvaluateCorpus {

  private EvaluateCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    list.add(
        CompatibilityScenario.builder("evaluate.dynamic.simple", ScenarioCategory.EVALUATE)
            .template("Static: #evaluate($dynamicSnippet)")
            .contextFactory(ContextFactory.of("dynamicSnippet", "Hello $name", "name", "Alice"))
            .tags("dynamic")
            .build());

    list.add(
        CompatibilityScenario.builder(
                "evaluate.dynamic.macro-defined-inside", ScenarioCategory.EVALUATE)
            .template("#evaluate('#macro(dynGreet $x)dyn:$x#end')#dynGreet('Bob')")
            .tags("dynamic")
            .build());

    return list;
  }
}
