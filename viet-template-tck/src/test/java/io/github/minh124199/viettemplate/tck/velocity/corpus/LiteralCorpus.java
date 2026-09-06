package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/** Compatibility scenarios covering collection literals (lists, maps, ranges). */
public final class LiteralCorpus {

  private LiteralCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. List literals
    list.add(
        CompatibilityScenario.simple(
            "literal.list.empty", ScenarioCategory.LITERAL, "#set($list = [])Size: $list.size()"));

    list.add(
        CompatibilityScenario.simple(
            "literal.list.numbers",
            ScenarioCategory.LITERAL,
            "#set($list = [10, 20, 30])[$list[0], $list[1], $list[2]]"));

    list.add(
        CompatibilityScenario.of(
            "literal.list.variables",
            ScenarioCategory.LITERAL,
            "#set($list = [$a, $b])[$list[0]-$list[1]]",
            ContextFactory.of("a", "alpha", "b", "beta")));

    // 2. Map literals
    list.add(
        CompatibilityScenario.simple(
            "literal.map.empty", ScenarioCategory.LITERAL, "#set($map = {})Size: $map.size()"));

    list.add(
        CompatibilityScenario.simple(
            "literal.map.entries",
            ScenarioCategory.LITERAL,
            "#set($map = {'x': 1, 'y': 2})[$map.x, $map['y']]"));

    // 3. Range literals
    list.add(
        CompatibilityScenario.simple(
            "literal.range.ascending", ScenarioCategory.LITERAL, "#foreach($i in [1..5])$i#end"));

    list.add(
        CompatibilityScenario.simple(
            "literal.range.descending", ScenarioCategory.LITERAL, "#foreach($i in [5..1])$i#end"));

    list.add(
        CompatibilityScenario.simple(
            "literal.range.single", ScenarioCategory.LITERAL, "#foreach($i in [0..0])$i#end"));

    list.add(
        CompatibilityScenario.simple(
            "literal.range.negative", ScenarioCategory.LITERAL, "#foreach($i in [-2..2])$i,#end"));

    return list;
  }
}
