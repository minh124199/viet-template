package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/** Compatibility scenarios covering #if, #elseif, #else directives. */
public final class ConditionalCorpus {

  private ConditionalCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    list.add(
        CompatibilityScenario.simple(
            "conditional.if.true", ScenarioCategory.CONDITIONAL, "#if(true)MATCH#end"));

    list.add(
        CompatibilityScenario.simple(
            "conditional.if.false", ScenarioCategory.CONDITIONAL, "#if(false)NO#end"));

    list.add(
        CompatibilityScenario.simple(
            "conditional.if-else.true",
            ScenarioCategory.CONDITIONAL,
            "#if(true)BRANCH1#{else}BRANCH2#end"));

    list.add(
        CompatibilityScenario.simple(
            "conditional.if-else.false",
            ScenarioCategory.CONDITIONAL,
            "#if(false)BRANCH1#{else}BRANCH2#end"));

    list.add(
        CompatibilityScenario.simple(
            "conditional.if-elseif-else.elseif",
            ScenarioCategory.CONDITIONAL,
            "#if(false)B1#{elseif}(true)B2#{else}B3#end"));

    list.add(
        CompatibilityScenario.simple(
            "conditional.if-elseif-else.else",
            ScenarioCategory.CONDITIONAL,
            "#if(false)B1#{elseif}(false)B2#{else}B3#end"));

    list.add(
        CompatibilityScenario.of(
            "conditional.nested",
            ScenarioCategory.CONDITIONAL,
            "#if($outer)#if($inner)INNER#{else}OUTER_ELSE#end#{else}NO#end",
            ContextFactory.of("outer", true, "inner", true)));

    return list;
  }
}
