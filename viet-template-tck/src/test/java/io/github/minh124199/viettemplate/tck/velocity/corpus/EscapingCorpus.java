package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility scenarios covering backslash escaping across backslash counts 0 to 6 for defined,
 * undefined, defined-null, quiet, formal references, and directives.
 */
public final class EscapingCorpus {

  private EscapingCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // Backslash counts 0 to 6
    for (int slashes = 0; slashes <= 6; slashes++) {
      String prefix = "\\".repeat(slashes);

      // 1. Defined simple reference
      list.add(
          CompatibilityScenario.of(
              String.format("escaping.defined.simple.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s$foo]", prefix),
              ContextFactory.of("foo", "bar")));

      // 2. Undefined simple reference
      list.add(
          CompatibilityScenario.simple(
              String.format("escaping.undefined.simple.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s$missing]", prefix)));

      // 3. Defined-null simple reference
      list.add(
          CompatibilityScenario.of(
              String.format("escaping.defined-null.simple.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s$nullVal]", prefix),
              () -> {
                java.util.Map<String, Object> ctx = new java.util.HashMap<>();
                ctx.put("nullVal", null);
                return ctx;
              }));

      // 4. Defined formal reference
      list.add(
          CompatibilityScenario.of(
              String.format("escaping.defined.formal.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s${foo}]", prefix),
              ContextFactory.of("foo", "bar")));

      // 5. Undefined formal reference
      list.add(
          CompatibilityScenario.simple(
              String.format("escaping.undefined.formal.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s${missing}]", prefix)));

      // 6. Defined quiet reference
      list.add(
          CompatibilityScenario.of(
              String.format("escaping.defined.quiet.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s$!foo]", prefix),
              ContextFactory.of("foo", "bar")));

      // 7. Undefined quiet reference
      list.add(
          CompatibilityScenario.simple(
              String.format("escaping.undefined.quiet.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s$!missing]", prefix)));

      // 8. Directive escaping (#if)
      list.add(
          CompatibilityScenario.simple(
              String.format("escaping.directive.if.slashes-%d", slashes),
              ScenarioCategory.ESCAPING,
              String.format("Output: [%s#if(true)YES#else NO#end]", prefix)));
    }

    return list;
  }
}
