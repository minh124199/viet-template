package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Authoritative aggregator for all differential compatibility scenarios. Enforces strict scenario
 * ID uniqueness upon initialization.
 */
public final class CompatibilityCorpus {

  private static final List<CompatibilityScenario> ALL_SCENARIOS;

  static {
    List<CompatibilityScenario> list = new ArrayList<>();
    list.addAll(LexicalCorpus.scenarios());
    list.addAll(ReferenceCorpus.scenarios());
    list.addAll(EscapingCorpus.scenarios());
    list.addAll(TruthinessCorpus.scenarios());
    list.addAll(AlternateValueCorpus.scenarios());
    list.addAll(SetCorpus.scenarios());
    list.addAll(ArithmeticCorpus.scenarios());
    list.addAll(ComparisonCorpus.scenarios());
    list.addAll(ShortCircuitCorpus.scenarios());
    list.addAll(LiteralCorpus.scenarios());
    list.addAll(ConditionalCorpus.scenarios());
    list.addAll(ForeachCorpus.scenarios());
    list.addAll(MacroCorpus.scenarios());
    list.addAll(ResourceCorpus.scenarios());
    list.addAll(EvaluateCorpus.scenarios());
    list.addAll(StrictModeCorpus.scenarios());
    list.addAll(SpaceGobblingCorpus.scenarios());
    list.addAll(IntrospectionCorpus.scenarios());
    list.addAll(SecurityCorpus.scenarios());
    list.addAll(ErrorCorpus.scenarios());
    list.addAll(RandomDifferentialCorpus.scenarios());

    // Strict validation: enforce unique scenario IDs
    Set<String> seenIds = new HashSet<>();
    for (CompatibilityScenario s : list) {
      if (!seenIds.add(s.id())) {
        throw new IllegalStateException(
            "Duplicate scenario ID detected in corpus: '" + s.id() + "'");
      }
    }

    ALL_SCENARIOS = Collections.unmodifiableList(list);
  }

  private CompatibilityCorpus() {}

  public static List<CompatibilityScenario> allScenarios() {
    return ALL_SCENARIOS;
  }

  public static List<CompatibilityScenario> filter(
      String idPattern, ScenarioCategory category, String tag) {
    return ALL_SCENARIOS.stream()
        .filter(s -> idPattern == null || s.id().matches(idPattern))
        .filter(s -> category == null || s.category() == category)
        .filter(s -> tag == null || s.tags().contains(tag))
        .toList();
  }
}
