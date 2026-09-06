package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.Probe;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios testing alternate value syntax ${val|fallback} and laziness. */
public final class AlternateValueCorpus {

  private AlternateValueCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Missing / undefined triggers fallback
    list.add(
        CompatibilityScenario.simple(
            "alternate-value.undefined",
            ScenarioCategory.REFERENCE,
            "Result: [${missing|'fallback'}]"));

    // 2. Defined null triggers fallback
    list.add(
        CompatibilityScenario.of(
            "alternate-value.defined-null",
            ScenarioCategory.REFERENCE,
            "Result: [${nullVal|'fallback'}]",
            () -> {
              Map<String, Object> ctx = new HashMap<>();
              ctx.put("nullVal", null);
              return ctx;
            }));

    // 3. Zero number triggers fallback (hardcoded empty check)
    list.add(
        CompatibilityScenario.of(
            "alternate-value.number.zero",
            ScenarioCategory.REFERENCE,
            "Result: [${val|'fallback'}]",
            ContextFactory.of("val", 0)));

    // 4. Non-zero number does not trigger fallback
    list.add(
        CompatibilityScenario.of(
            "alternate-value.number.one",
            ScenarioCategory.REFERENCE,
            "Result: [${val|'fallback'}]",
            ContextFactory.of("val", 1)));

    // 5. Empty string triggers fallback
    list.add(
        CompatibilityScenario.of(
            "alternate-value.string.empty",
            ScenarioCategory.REFERENCE,
            "Result: [${val|'fallback'}]",
            ContextFactory.of("val", "")));

    // 6. Non-empty string renders value
    list.add(
        CompatibilityScenario.of(
            "alternate-value.string.nonempty",
            ScenarioCategory.REFERENCE,
            "Result: [${val|'fallback'}]",
            ContextFactory.of("val", "hello")));

    // 7. Empty collection triggers fallback
    list.add(
        CompatibilityScenario.of(
            "alternate-value.list.empty",
            ScenarioCategory.REFERENCE,
            "Result: [${val|'fallback'}]",
            ContextFactory.of("val", Collections.emptyList())));

    // 8. Non-empty collection renders
    list.add(
        CompatibilityScenario.of(
            "alternate-value.list.nonempty",
            ScenarioCategory.REFERENCE,
            "Result: [${val|'fallback'}]",
            ContextFactory.of("val", List.of("item"))));

    // 9. Laziness: fallback expression should NOT be evaluated if target is truthy
    list.add(
        CompatibilityScenario.of(
            "alternate-value.laziness.truthy-no-fallback-eval",
            ScenarioCategory.REFERENCE,
            "Result: [${val|$probe.fail()}]",
            () -> Map.of("val", "present", "probe", new Probe("lazyProbe"))));

    // 10. Laziness: fallback expression is evaluated if target is falsy
    list.add(
        CompatibilityScenario.of(
            "alternate-value.laziness.falsy-triggers-fallback-eval",
            ScenarioCategory.REFERENCE,
            "Result: [${val|$probe.hit()}]",
            () -> Map.of("val", "", "probe", new Probe("evalProbe"))));

    return list;
  }
}
