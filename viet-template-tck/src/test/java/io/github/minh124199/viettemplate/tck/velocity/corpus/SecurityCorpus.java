package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.model.PersonBean;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Compatibility scenarios covering security boundaries and forbidden introspection targets. */
public final class SecurityCorpus {

  private SecurityCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    list.add(
        CompatibilityScenario.of(
            "security.denial.get-class-method",
            ScenarioCategory.SECURITY,
            "Class: $obj.getClass()",
            () -> Map.of("obj", new PersonBean("Bob", 20, true, null))));

    list.add(
        CompatibilityScenario.of(
            "security.denial.class-property",
            ScenarioCategory.SECURITY,
            "Class: $obj.class",
            () -> Map.of("obj", new PersonBean("Bob", 20, true, null))));

    return list;
  }
}
