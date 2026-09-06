package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.NullReturningBean;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering runtime.references.strict=true mode. */
public final class StrictModeCorpus {

  private StrictModeCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    CompatibilityConfiguration strictConfig =
        CompatibilityConfiguration.defaultConfiguration().withStrictReferences(true);

    // 1. Undefined root variable throws upon rendering
    list.add(
        CompatibilityScenario.builder("strict.undefined.root.throws", ScenarioCategory.STRICT_MODE)
            .template("Value: $missing")
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 2. Quiet undefined root variable throws upon rendering
    list.add(
        CompatibilityScenario.builder("strict.undefined.quiet.throws", ScenarioCategory.STRICT_MODE)
            .template("Value: $!missing")
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 3. Defined null throws in normal mode
    list.add(
        CompatibilityScenario.builder(
                "strict.defined-null.normal.throws", ScenarioCategory.STRICT_MODE)
            .template("Value: $nullVal")
            .contextFactory(
                () -> {
                  Map<String, Object> ctx = new HashMap<>();
                  ctx.put("nullVal", null);
                  return ctx;
                })
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 4. Quiet defined null suppresses exception and produces empty string
    list.add(
        CompatibilityScenario.builder(
                "strict.defined-null.quiet.suppressed", ScenarioCategory.STRICT_MODE)
            .template("Value: [$!nullVal]")
            .contextFactory(
                () -> {
                  Map<String, Object> ctx = new HashMap<>();
                  ctx.put("nullVal", null);
                  return ctx;
                })
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 5. Defined null property throws in normal mode
    list.add(
        CompatibilityScenario.builder(
                "strict.defined-null.property.normal.throws", ScenarioCategory.STRICT_MODE)
            .template("Value: $probe.nullProperty")
            .contextFactory(() -> Map.of("probe", new NullReturningBean()))
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 6. Defined null property suppressed in quiet mode
    list.add(
        CompatibilityScenario.builder(
                "strict.defined-null.property.quiet.suppressed", ScenarioCategory.STRICT_MODE)
            .template("Value: [$!probe.nullProperty]")
            .contextFactory(() -> Map.of("probe", new NullReturningBean()))
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 7. #if conditional does NOT throw on undefined variable
    list.add(
        CompatibilityScenario.builder(
                "strict.conditional.undefined-safe", ScenarioCategory.STRICT_MODE)
            .template("#if($missing)YES#{else}NO#end")
            .configuration(strictConfig)
            .tags("strict")
            .build());

    // 8. Alternate value does NOT throw on undefined variable
    list.add(
        CompatibilityScenario.builder(
                "strict.alternate-value.undefined-safe", ScenarioCategory.STRICT_MODE)
            .template("Result: [${missing|'fallback'}]")
            .configuration(strictConfig)
            .tags("strict")
            .build());

    return list;
  }
}
