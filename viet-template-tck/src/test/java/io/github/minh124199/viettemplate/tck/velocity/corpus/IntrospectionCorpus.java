package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.IntrospectionTarget;
import io.github.minh124199.viettemplate.tck.velocity.model.OverloadedMethods;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering member resolution precedence and method overloads. */
public final class IntrospectionCorpus {

  private IntrospectionCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Getter takes precedence over public field and generic get
    list.add(
        CompatibilityScenario.of(
            "introspection.property.getter-over-field",
            ScenarioCategory.INTROSPECTION,
            "Resolved: $target.name",
            () -> Map.of("target", new IntrospectionTarget())));

    // 2. Method Overload resolution
    list.add(
        CompatibilityScenario.of(
            "introspection.overload.integer-exact",
            ScenarioCategory.METHOD_OVERLOAD,
            "Result: $probe.choose($val)",
            () -> Map.of("probe", new OverloadedMethods(), "val", Integer.valueOf(42))));

    list.add(
        CompatibilityScenario.of(
            "introspection.overload.number-supertype",
            ScenarioCategory.METHOD_OVERLOAD,
            "Result: $probe.choose($val)",
            () -> Map.of("probe", new OverloadedMethods(), "val", Double.valueOf(3.14))));

    list.add(
        CompatibilityScenario.of(
            "introspection.overload.string-exact",
            ScenarioCategory.METHOD_OVERLOAD,
            "Result: $probe.choose('hello')",
            () -> Map.of("probe", new OverloadedMethods())));

    list.add(
        CompatibilityScenario.of(
            "introspection.overload.primitive-int",
            ScenarioCategory.METHOD_OVERLOAD,
            "Result: $probe.pick(10)",
            () -> Map.of("probe", new OverloadedMethods())));

    return list;
  }
}
