package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.Probe;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compatibility scenarios verifying short-circuit evaluation of boolean operators (&&, ||, and,
 * or).
 */
public final class ShortCircuitCorpus {

  private ShortCircuitCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Logical AND short-circuit on false
    list.add(
        CompatibilityScenario.of(
            "short-circuit.and.false-skips-probe-fail",
            ScenarioCategory.EXPRESSION,
            "#if(false && $probe.fail())YES#{else}NO#end",
            () -> Map.of("probe", new Probe("andProbe"))));

    list.add(
        CompatibilityScenario.of(
            "short-circuit.and.alias-and-skips-probe-fail",
            ScenarioCategory.EXPRESSION,
            "#if(false and $probe.fail())YES#{else}NO#end",
            () -> Map.of("probe", new Probe("andProbe"))));

    list.add(
        CompatibilityScenario.of(
            "short-circuit.and.true-evaluates-rhs",
            ScenarioCategory.EXPRESSION,
            "#if(true && $probe.hit() > 0)YES#{else}NO#end",
            () -> Map.of("probe", new Probe("andProbe"))));

    // 2. Logical OR short-circuit on true
    list.add(
        CompatibilityScenario.of(
            "short-circuit.or.true-skips-probe-fail",
            ScenarioCategory.EXPRESSION,
            "#if(true || $probe.fail())YES#{else}NO#end",
            () -> Map.of("probe", new Probe("orProbe"))));

    list.add(
        CompatibilityScenario.of(
            "short-circuit.or.alias-or-skips-probe-fail",
            ScenarioCategory.EXPRESSION,
            "#if(true or $probe.fail())YES#{else}NO#end",
            () -> Map.of("probe", new Probe("orProbe"))));

    list.add(
        CompatibilityScenario.of(
            "short-circuit.or.false-evaluates-rhs",
            ScenarioCategory.EXPRESSION,
            "#if(false || $probe.hit() > 0)YES#{else}NO#end",
            () -> Map.of("probe", new Probe("orProbe"))));

    return list;
  }
}
