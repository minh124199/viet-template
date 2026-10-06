package io.github.minh124199.viettemplate.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationEvidenceCoverageTest {

  @Test
  void testEveryRuleReferencesAuthoritativeEvidence() {
    List<MigrationRule> rules = MigrationRuleRegistry.allRules();
    assertFalse(rules.isEmpty());

    for (MigrationRule rule : rules) {
      assertFalse(
          rule.evidenceScenarioIds().isEmpty(),
          "Rule " + rule.id() + " must reference at least one evidence scenario");
      for (String scenarioId : rule.evidenceScenarioIds()) {
        assertNotNull(scenarioId);
        assertFalse(scenarioId.isBlank());
      }
    }
  }

  @Test
  void testCanonicalDifferentialEvidenceScenarios() {
    MigrationRule arith = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_ARITH_DIV_ZERO);
    assertTrue(arith.evidenceScenarioIds().contains("arithmetic.divide-by-zero"));

    MigrationRule sec = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_SEC_CLASS_ACCESS);
    assertTrue(sec.evidenceScenarioIds().contains("security.denial.class-property"));
    assertTrue(sec.evidenceScenarioIds().contains("security.denial.get-class-method"));

    MigrationRule setNull = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_SET_NULL_RHS);
    assertTrue(setNull.evidenceScenarioIds().contains("set.null-rhs.legacy-preserved.method-null"));
    assertTrue(setNull.evidenceScenarioIds().contains("set.null-rhs.legacy-preserved.undefined"));

    MigrationRule foreachStop =
        MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_EXT_FOREACH_STOP);
    assertTrue(foreachStop.evidenceScenarioIds().contains("foreach.control.stop-method"));

    MigrationRule altVal = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_EXT_ALT_VALUE);
    assertTrue(altVal.evidenceScenarioIds().contains("alternate.basic.literal"));

    MigrationRule parseDyn = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_PARSE_DYNAMIC);
    assertTrue(parseDyn.evidenceScenarioIds().contains("resource.parse.dynamic"));

    MigrationRule incDyn = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_INCLUDE_DYNAMIC);
    assertTrue(incDyn.evidenceScenarioIds().contains("resource.include.dynamic"));

    MigrationRule evalDyn =
        MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_EVALUATE_DYNAMIC);
    assertTrue(evalDyn.evidenceScenarioIds().contains("evaluate.basic.expression"));

    MigrationRule strictRef = MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_STRICT_REF);
    assertTrue(strictRef.evidenceScenarioIds().contains("strict.mode.undefined.variable"));
    assertTrue(strictRef.evidenceScenarioIds().contains("strict.mode.invalid.property"));

    MigrationRule safeProfile =
        MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_SEC_SAFE_PROFILE);
    assertTrue(safeProfile.evidenceScenarioIds().contains("security.denial.get-class-method"));

    MigrationRule refDyn =
        MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_REF_DYNAMIC_RESOLVE);
    assertTrue(refDyn.evidenceScenarioIds().contains("reference.property.javabean"));
  }
}
