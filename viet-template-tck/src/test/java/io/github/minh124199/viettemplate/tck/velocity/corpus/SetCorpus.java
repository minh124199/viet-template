package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.MutableBean;
import io.github.minh124199.viettemplate.tck.velocity.model.NullReturningBean;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering #set directive across assignments and null handling. */
public final class SetCorpus {

  private SetCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Root variable assignment
    list.add(
        CompatibilityScenario.builder("set.root.string", ScenarioCategory.ASSIGNMENT)
            .template("#set($x = 'hello')[$x]")
            .observeContext("x")
            .build());

    list.add(
        CompatibilityScenario.builder("set.root.number", ScenarioCategory.ASSIGNMENT)
            .template("#set($num = 42)[$num]")
            .observeContext("num")
            .build());

    // 2. Null RHS with setNullAllowed = true (Velocity 2.x default)
    list.add(
        CompatibilityScenario.builder(
                "set.null-rhs.overwrite.undefined", ScenarioCategory.ASSIGNMENT)
            .template("#set($x = 'initial')#set($x = $missing)[$x]")
            .observeContext("x")
            .build());

    list.add(
        CompatibilityScenario.builder(
                "set.null-rhs.overwrite.method-null", ScenarioCategory.ASSIGNMENT)
            .template("#set($x = 'initial')#set($x = $probe.returnNull())[$x]")
            .contextFactory(() -> Map.of("probe", new NullReturningBean()))
            .observeContext("x")
            .build());

    list.add(
        CompatibilityScenario.builder(
                "set.null-rhs.overwrite.property-null", ScenarioCategory.ASSIGNMENT)
            .template("#set($x = 'initial')#set($x = $probe.nullProperty)[$x]")
            .contextFactory(() -> Map.of("probe", new NullReturningBean()))
            .observeContext("x")
            .build());

    // 3. Null RHS with setNullAllowed = false (Legacy 1.x mode)
    CompatibilityConfiguration legacySetNull =
        CompatibilityConfiguration.defaultConfiguration().withSetNullAllowed(false);

    list.add(
        CompatibilityScenario.builder(
                "set.null-rhs.legacy-preserved.undefined", ScenarioCategory.ASSIGNMENT)
            .template("#set($x = 'initial')#set($x = $missing)[$x]")
            .configuration(legacySetNull)
            .observeContext("x")
            .build());

    list.add(
        CompatibilityScenario.builder(
                "set.null-rhs.legacy-preserved.method-null", ScenarioCategory.ASSIGNMENT)
            .template("#set($x = 'initial')#set($x = $probe.returnNull())[$x]")
            .contextFactory(() -> Map.of("probe", new NullReturningBean()))
            .configuration(legacySetNull)
            .observeContext("x")
            .build());

    // 4. Bean property mutation
    list.add(
        CompatibilityScenario.builder("set.property.bean", ScenarioCategory.ASSIGNMENT)
            .template("#set($bean.text = 'updated')[$bean.text]")
            .contextFactory(() -> Map.of("bean", new MutableBean()))
            .build());

    // 5. Map property and index mutation
    list.add(
        CompatibilityScenario.builder("set.map.property", ScenarioCategory.ASSIGNMENT)
            .template("#set($map.key = 'val')[$map.key]")
            .contextFactory(
                () -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("key", "old");
                  return Map.of("map", map);
                })
            .build());

    list.add(
        CompatibilityScenario.builder("set.map.index", ScenarioCategory.ASSIGNMENT)
            .template("#set($map['key'] = 'val2')[$map['key']]")
            .contextFactory(
                () -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("key", "old");
                  return Map.of("map", map);
                })
            .build());

    // 6. List and Array index mutation
    list.add(
        CompatibilityScenario.builder("set.list.index", ScenarioCategory.ASSIGNMENT)
            .template("#set($list[0] = 'new_zero')[$list[0]]")
            .contextFactory(
                () -> {
                  List<String> listObj = new ArrayList<>();
                  listObj.add("old");
                  return Map.of("list", listObj);
                })
            .build());

    list.add(
        CompatibilityScenario.builder("set.array.index", ScenarioCategory.ASSIGNMENT)
            .template("#set($arr[0] = 'new_arr')[$arr[0]]")
            .contextFactory(() -> Map.of("arr", new String[] {"old"}))
            .build());

    return list;
  }
}
