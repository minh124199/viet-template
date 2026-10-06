package io.github.minh124199.viettemplate.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MigrationRuleRegistryTest {

  @Test
  void testRuleUniqueness() {
    List<MigrationRule> rules = MigrationRuleRegistry.allRules();
    assertFalse(rules.isEmpty(), "Registry must not be empty");

    Set<String> ids = new HashSet<>();
    for (MigrationRule rule : rules) {
      assertTrue(ids.add(rule.id()), "Duplicate rule ID found in registry: " + rule.id());
    }
    assertEquals(ids.size(), rules.size());
  }

  @Test
  void testNonEmptyFields() {
    for (MigrationRule rule : MigrationRuleRegistry.allRules()) {
      assertNotNull(rule.id(), "id must not be null");
      assertFalse(rule.id().isBlank(), "id must not be blank");
      assertNotNull(rule.category(), "category must not be null: " + rule.id());
      assertNotNull(rule.defaultSeverity(), "defaultSeverity must not be null: " + rule.id());
      assertNotNull(rule.classification(), "classification must not be null: " + rule.id());
      assertNotNull(rule.title(), "title must not be null: " + rule.id());
      assertFalse(rule.title().isBlank(), "title must not be blank: " + rule.id());
      assertNotNull(rule.velocityBehavior(), "velocityBehavior must not be null: " + rule.id());
      assertFalse(
          rule.velocityBehavior().isBlank(), "velocityBehavior must not be blank: " + rule.id());
      assertNotNull(
          rule.vietTemplateBehavior(), "vietTemplateBehavior must not be null: " + rule.id());
      assertFalse(
          rule.vietTemplateBehavior().isBlank(),
          "vietTemplateBehavior must not be blank: " + rule.id());
      assertNotNull(rule.migrationAction(), "migrationAction must not be null: " + rule.id());
      assertFalse(
          rule.migrationAction().isBlank(), "migrationAction must not be blank: " + rule.id());
      assertNotNull(rule.confidence(), "confidence must not be null: " + rule.id());
      assertNotNull(
          rule.evidenceScenarioIds(), "evidenceScenarioIds must not be null: " + rule.id());
      assertFalse(
          rule.evidenceScenarioIds().isEmpty(),
          "evidenceScenarioIds must not be empty: " + rule.id());
      assertNotNull(rule.specSection(), "specSection must not be null: " + rule.id());
      assertFalse(rule.specSection().isBlank(), "specSection must not be blank: " + rule.id());
      assertNotNull(rule.adr(), "adr must not be null: " + rule.id());
    }
  }

  @Test
  void testEvidenceReferencesPresent() {
    for (MigrationRule rule : MigrationRuleRegistry.allRules()) {
      for (String scenarioId : rule.evidenceScenarioIds()) {
        assertNotNull(scenarioId, "scenarioId must not be null in rule " + rule.id());
        assertFalse(scenarioId.isBlank(), "scenarioId must not be blank in rule " + rule.id());
      }
    }
  }

  @Test
  void testDeterministicRegistryOrder() {
    List<MigrationRule> rules = MigrationRuleRegistry.allRules();
    Map<String, MigrationRule> map = MigrationRuleRegistry.rulesById();

    assertEquals(rules.size(), map.size());
    int idx = 0;
    for (Map.Entry<String, MigrationRule> entry : map.entrySet()) {
      assertEquals(rules.get(idx).id(), entry.getKey());
      assertSame(rules.get(idx), entry.getValue());
      idx++;
    }
  }

  @Test
  void testLookupById() {
    Optional<MigrationRule> found =
        MigrationRuleRegistry.findById(MigrationRuleRegistry.ID_ARITH_DIV_ZERO);
    assertTrue(found.isPresent());
    assertEquals(MigrationRuleRegistry.ID_ARITH_DIV_ZERO, found.get().id());
    assertSame(
        MigrationRuleRegistry.RULE_ARITH_DIV_ZERO,
        MigrationRuleRegistry.getRule(MigrationRuleRegistry.ID_ARITH_DIV_ZERO));

    assertTrue(MigrationRuleRegistry.findById("NON_EXISTENT_RULE").isEmpty());
    assertThrows(
        IllegalArgumentException.class, () -> MigrationRuleRegistry.getRule("NON_EXISTENT_RULE"));
  }
}
