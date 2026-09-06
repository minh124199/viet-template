package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering resource inclusion directives (#include and #parse). */
public final class ResourceCorpus {

  private ResourceCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. #include inserting unparsed raw content
    list.add(
        CompatibilityScenario.builder("resource.include.raw-content", ScenarioCategory.RESOURCE)
            .template("Before;#include('raw.txt');After")
            .resources(Map.of("raw.txt", "Plain Text with $unparsed #if(false) directives"))
            .build());

    // 2. #parse evaluating sub-template in caller scope
    list.add(
        CompatibilityScenario.builder("resource.parse.eval-subtemplate", ScenarioCategory.RESOURCE)
            .template("Main-Start;#parse('sub.vm');Main-End")
            .resources(Map.of("sub.vm", "Sub: $user.name"))
            .contextFactory(ContextFactory.of("user", Map.of("name", "Alice")))
            .build());

    // 3. #parse exposing macros defined in parsed template
    list.add(
        CompatibilityScenario.builder("resource.parse.macro-visibility", ScenarioCategory.RESOURCE)
            .template("#parse('macro_lib.vm')#libMacro('test')")
            .resources(Map.of("macro_lib.vm", "#macro(libMacro $arg)from_lib:$arg#end"))
            .build());

    return list;
  }
}
