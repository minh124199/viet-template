package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compatibility scenarios covering #foreach loops, $foreach metadata, #break, and #stop. */
public final class ForeachCorpus {

  private ForeachCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Iterate over List
    list.add(
        CompatibilityScenario.of(
            "foreach.collection.list",
            ScenarioCategory.FOREACH,
            "#foreach($item in $items)[$item]#end",
            ContextFactory.of("items", List.of("a", "b", "c"))));

    // 2. Iterate over Set
    list.add(
        CompatibilityScenario.of(
            "foreach.collection.set",
            ScenarioCategory.FOREACH,
            "#foreach($item in $items)[$item]#end",
            ContextFactory.of("items", Set.of("single"))));

    // 3. Iterate over Map (iterates values in Velocity!)
    list.add(
        CompatibilityScenario.of(
            "foreach.collection.map-values",
            ScenarioCategory.FOREACH,
            "#foreach($val in $map)[$val]#end",
            () -> {
              Map<String, String> map = new LinkedHashMap<>();
              map.put("k1", "v1");
              map.put("k2", "v2");
              return Map.of("map", map);
            }));

    // 4. Iterate over Object Array
    list.add(
        CompatibilityScenario.of(
            "foreach.array.object",
            ScenarioCategory.FOREACH,
            "#foreach($item in $arr)[$item]#end",
            ContextFactory.of("arr", new String[] {"x", "y"})));

    // 5. Iterate over Primitive Array
    list.add(
        CompatibilityScenario.of(
            "foreach.array.primitive",
            ScenarioCategory.FOREACH,
            "#foreach($item in $arr)[$item]#end",
            ContextFactory.of("arr", new int[] {1, 2, 3})));

    // 6. Loop metadata ($foreach.index, count, first, last, hasNext)
    list.add(
        CompatibilityScenario.of(
            "foreach.metadata.properties",
            ScenarioCategory.FOREACH,
            "#foreach($x in"
                + " $list)$foreach.index:$foreach.count:$foreach.first:$foreach.last:$foreach.hasNext;#end",
            ContextFactory.of("list", List.of("a", "b", "c"))));

    // 7. Nested loops and $foreach.parent
    list.add(
        CompatibilityScenario.simple(
            "foreach.metadata.parent",
            ScenarioCategory.FOREACH,
            "#foreach($i in [1..2])outer-$i(#foreach($j in"
                + " [1..2])$foreach.parent.index:$foreach.index;#end)#end"));

    // 8. Variable restoration after loop termination
    list.add(
        CompatibilityScenario.builder(
                "foreach.scope.variable-restoration", ScenarioCategory.FOREACH)
            .template("#set($x = 'before')#foreach($x in [1..2])loop-$x;#end after:[$x]")
            .observeContext("x")
            .build());

    // 9. #foreach #else block when collection is empty
    list.add(
        CompatibilityScenario.of(
            "foreach.empty.else-block",
            ScenarioCategory.FOREACH,
            "#foreach($x in $empty)LOOP#{else}EMPTY_BLOCK#end",
            ContextFactory.of("empty", Collections.emptyList())));

    // 10. #break directive inside foreach
    list.add(
        CompatibilityScenario.simple(
            "foreach.control.break-directive",
            ScenarioCategory.FOREACH,
            "#foreach($i in [1..5])#if($i == 3)#break#end$i#end"));

    // 11. $foreach.stop() inside foreach
    list.add(
        CompatibilityScenario.simple(
            "foreach.control.stop-method",
            ScenarioCategory.FOREACH,
            "#foreach($i in [1..5])#if($i == 3)$foreach.stop()#end$i#end"));

    // 12. #stop directive terminating template rendering
    list.add(
        CompatibilityScenario.simple(
            "foreach.control.template-stop", ScenarioCategory.FOREACH, "Start;#stop;End"));

    return list;
  }
}
