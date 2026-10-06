package io.github.minh124199.viettemplate.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical registry defining differential migration rules relative to Apache Velocity 2.4.1. */
public final class MigrationRuleRegistry {

  public static final String ID_ARITH_DIV_ZERO = "MIG-ARITH-DIV-ZERO";
  public static final String ID_SEC_CLASS_ACCESS = "MIG-SEC-CLASS-ACCESS";
  public static final String ID_SET_NULL_RHS = "MIG-SET-NULL-RHS";
  public static final String ID_EXT_FOREACH_STOP = "MIG-EXT-FOREACH-STOP";
  public static final String ID_EXT_ALT_VALUE = "MIG-EXT-ALT-VALUE";
  public static final String ID_PARSE_DYNAMIC = "MIG-PARSE-DYNAMIC";
  public static final String ID_INCLUDE_DYNAMIC = "MIG-INCLUDE-DYNAMIC";
  public static final String ID_EVALUATE_DYNAMIC = "MIG-EVALUATE-DYNAMIC";
  public static final String ID_STRICT_REF = "MIG-STRICT-REF";
  public static final String ID_SEC_SAFE_PROFILE = "MIG-SEC-SAFE-PROFILE";
  public static final String ID_REF_DYNAMIC_RESOLVE = "MIG-REF-DYNAMIC-RESOLVE";

  public static final MigrationRule RULE_ARITH_DIV_ZERO =
      new MigrationRule(
          ID_ARITH_DIV_ZERO,
          MigrationCategory.ARITHMETIC,
          MigrationSeverity.BLOCKER,
          MigrationClassification.KNOWN_BEHAVIOR_DIFFERENCE,
          "Fail-fast division or modulo by zero",
          "Logs a warning and returns null on division or modulo by zero, producing literal or"
              + " empty output.",
          "Throws TemplateRenderException with DIVISION_BY_ZERO diagnostic code on division or"
              + " modulo by zero.",
          "Guard divisions and modulo operations with non-zero checks or replace divisor"
              + " expressions.",
          MigrationConfidence.DOCUMENTED_DIFFERENCE,
          List.of("arithmetic.divide-by-zero"),
          "docs/06-velocity-2.4.1-compatibility.md#4.1",
          "ADR-0005");

  public static final MigrationRule RULE_SEC_CLASS_ACCESS =
      new MigrationRule(
          ID_SEC_CLASS_ACCESS,
          MigrationCategory.SECURITY,
          MigrationSeverity.BLOCKER,
          MigrationClassification.SECURITY_RESTRICTED,
          "Class, getClass(), or ClassLoader reflection access",
          "Permitted by default in standard Velocity Engine unless custom SecureUberspector is"
              + " configured.",
          "Denies access to .class, getClass(), and ClassLoader targets by default to prevent"
              + " sandbox escapes.",
          "Remove reflection calls from template; expose required properties directly on the"
              + " model.",
          MigrationConfidence.STATICALLY_VERIFIED,
          List.of("security.denial.class-property", "security.denial.get-class-method"),
          "docs/06-velocity-2.4.1-compatibility.md#4.2",
          "ADR-0004");

  public static final MigrationRule RULE_SET_NULL_RHS =
      new MigrationRule(
          ID_SET_NULL_RHS,
          MigrationCategory.NULL_UNDEFINED,
          MigrationSeverity.INFO,
          MigrationClassification.KNOWN_BEHAVIOR_DIFFERENCE,
          "Legacy #set null right-hand side preservation",
          "Velocity 2.x unconditionally assigns null to target variable, removing Velocity 1.x"
              + " preservation option.",
          "Assigns null in 2.x mode, but supports ignoreSetNullRhs compatibility mode preserving"
              + " existing variable value.",
          "Verify #set semantics when RHS is null or undefined; enable ignoreSetNullRhs if"
              + " cascading defaults are needed.",
          MigrationConfidence.DOCUMENTED_DIFFERENCE,
          List.of(
              "set.null-rhs.legacy-preserved.method-null",
              "set.null-rhs.legacy-preserved.undefined"),
          "docs/06-velocity-2.4.1-compatibility.md#4.3",
          "ADR-0007");

  public static final MigrationRule RULE_EXT_FOREACH_STOP =
      new MigrationRule(
          ID_EXT_FOREACH_STOP,
          MigrationCategory.FOREACH,
          MigrationSeverity.INFO,
          MigrationClassification.VIET_TEMPLATE_EXTENSION,
          "Programmatic loop termination via $foreach.stop()",
          "Velocity 2.4.1 removed $foreach.stop(), requiring #break directive.",
          "Supports both #break and $foreach.stop() for backwards compatibility with legacy"
              + " templates.",
          "Optionally migrate $foreach.stop() calls to #break directive for standard VTL"
              + " compliance.",
          MigrationConfidence.STATICALLY_VERIFIED,
          List.of("foreach.control.stop-method"),
          "docs/06-velocity-2.4.1-compatibility.md#5.1",
          "ADR-0006");

  public static final MigrationRule RULE_EXT_ALT_VALUE =
      new MigrationRule(
          ID_EXT_ALT_VALUE,
          MigrationCategory.EXTENSION,
          MigrationSeverity.INFO,
          MigrationClassification.VIET_TEMPLATE_EXTENSION,
          "Alternate default value expression ${var|'default'}",
          "Causes a syntax parse error in Apache Velocity 2.4.1.",
          "Natively evaluates alternate fallback expression when reference evaluates to null or"
              + " empty.",
          "No action required in Viet Template; replace with #if or ternary if Velocity"
              + " cross-compatibility is required.",
          MigrationConfidence.STATICALLY_VERIFIED,
          List.of("alternate.basic.literal"),
          "docs/migration/compatibility-matrix.md#STATE3-003",
          "N/A");

  public static final MigrationRule RULE_PARSE_DYNAMIC =
      new MigrationRule(
          ID_PARSE_DYNAMIC,
          MigrationCategory.PARSE,
          MigrationSeverity.WARNING,
          MigrationClassification.DYNAMICALLY_UNVERIFIABLE,
          "Dynamic template path in #parse",
          "Dynamically resolves and evaluates template name at runtime from context.",
          "Allowed in dynamic profile but prevents static dependency tracking, fingerprinting, and"
              + " AOT compilation.",
          "Use static string literal template paths where possible to enable dependency graph"
              + " analysis and AOT compilation.",
          MigrationConfidence.DYNAMICALLY_UNVERIFIABLE,
          List.of("resource.parse.dynamic"),
          "docs/02-vtl-compatibility-spec.md#14",
          "N/A");

  public static final MigrationRule RULE_INCLUDE_DYNAMIC =
      new MigrationRule(
          ID_INCLUDE_DYNAMIC,
          MigrationCategory.INCLUDE,
          MigrationSeverity.WARNING,
          MigrationClassification.DYNAMICALLY_UNVERIFIABLE,
          "Dynamic resource path in #include",
          "Dynamically inserts unparsed resource file at runtime.",
          "Allowed in dynamic profile but prevents static build-time resource verification and"
              + " embedding.",
          "Use static string literal resource paths where possible to allow build-time validation.",
          MigrationConfidence.DYNAMICALLY_UNVERIFIABLE,
          List.of("resource.include.dynamic"),
          "docs/02-vtl-compatibility-spec.md#13",
          "N/A");

  public static final MigrationRule RULE_EVALUATE_DYNAMIC =
      new MigrationRule(
          ID_EVALUATE_DYNAMIC,
          MigrationCategory.EVALUATE,
          MigrationSeverity.WARNING,
          MigrationClassification.DYNAMICALLY_UNVERIFIABLE,
          "Dynamic code evaluation via #evaluate",
          "Evaluates dynamic VTL snippet string at runtime.",
          "Blocked in VTL_SAFE profile; in other profiles, cannot be verified statically and"
              + " disables AOT optimizations.",
          "Avoid #evaluate in secure environments or pre-render dynamic snippets into separate"
              + " static templates.",
          MigrationConfidence.DYNAMICALLY_UNVERIFIABLE,
          List.of("evaluate.basic.expression"),
          "docs/02-vtl-compatibility-spec.md#16",
          "N/A");

  public static final MigrationRule RULE_STRICT_REF =
      new MigrationRule(
          ID_STRICT_REF,
          MigrationCategory.STRICT_REFERENCES,
          MigrationSeverity.WARNING,
          MigrationClassification.COMPATIBLE_WITH_CONFIGURATION,
          "Strict reference evaluation difference",
          "In standard mode, undefined references render as their literal notation ($foo), whereas"
              + " strict mode throws.",
          "Throws TemplateRenderException when undefined reference is accessed under"
              + " strictReferences mode.",
          "Declare variables in template contract or use quiet reference notation $!var to safely"
              + " handle missing values.",
          MigrationConfidence.DOCUMENTED_DIFFERENCE,
          List.of("strict.mode.undefined.variable", "strict.mode.invalid.property"),
          "docs/02-vtl-compatibility-spec.md#4",
          "N/A");

  public static final MigrationRule RULE_SEC_SAFE_PROFILE =
      new MigrationRule(
          ID_SEC_SAFE_PROFILE,
          MigrationCategory.SECURITY,
          MigrationSeverity.BLOCKER,
          MigrationClassification.SECURITY_RESTRICTED,
          "Safe security profile execution restriction",
          "Permits arbitrary method invocations on context objects by default.",
          "VTL_SAFE blocks arbitrary method execution, reflection, classloading, and system calls.",
          "Allowlist required methods or select VTL_MIGRATION profile for legacy compatibility.",
          MigrationConfidence.STATICALLY_VERIFIED,
          List.of("security.denial.get-class-method"),
          "docs/02-vtl-compatibility-spec.md#21",
          "ADR-0004");

  public static final MigrationRule RULE_REF_DYNAMIC_RESOLVE =
      new MigrationRule(
          ID_REF_DYNAMIC_RESOLVE,
          MigrationCategory.REFERENCE,
          MigrationSeverity.INFO,
          MigrationClassification.COMPATIBLE_WITH_CONFIGURATION,
          "Dynamic property resolution fallback",
          "Searches multiple getter variations (getProp, GetProp, isProp, prop) dynamically at"
              + " runtime.",
          "Emulates Velocity getter candidate search in VTL_MIGRATION profile; typed AOT binds"
              + " directly.",
          "Standardize JavaBean getter naming or provide explicit TemplateContract for optimal AOT"
              + " performance.",
          MigrationConfidence.DOCUMENTED_DIFFERENCE,
          List.of("reference.property.javabean"),
          "docs/02-vtl-compatibility-spec.md#5",
          "N/A");

  private static final List<MigrationRule> ALL_RULES =
      List.of(
          RULE_ARITH_DIV_ZERO,
          RULE_SEC_CLASS_ACCESS,
          RULE_SET_NULL_RHS,
          RULE_EXT_FOREACH_STOP,
          RULE_EXT_ALT_VALUE,
          RULE_PARSE_DYNAMIC,
          RULE_INCLUDE_DYNAMIC,
          RULE_EVALUATE_DYNAMIC,
          RULE_STRICT_REF,
          RULE_SEC_SAFE_PROFILE,
          RULE_REF_DYNAMIC_RESOLVE);

  private static final Map<String, MigrationRule> RULES_BY_ID;

  static {
    Map<String, MigrationRule> map = new LinkedHashMap<>();
    for (MigrationRule rule : ALL_RULES) {
      map.put(rule.id(), rule);
    }
    RULES_BY_ID = Collections.unmodifiableMap(map);
  }

  private MigrationRuleRegistry() {}

  public static List<MigrationRule> allRules() {
    return ALL_RULES;
  }

  public static Map<String, MigrationRule> rulesById() {
    return RULES_BY_ID;
  }

  public static Optional<MigrationRule> findById(String id) {
    if (id == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(RULES_BY_ID.get(id));
  }

  public static MigrationRule getRule(String id) {
    return findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Unknown migration rule ID: " + id));
  }
}
