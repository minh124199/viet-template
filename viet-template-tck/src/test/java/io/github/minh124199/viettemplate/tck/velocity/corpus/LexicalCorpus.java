package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/** Compatibility scenarios covering comments, verbatim blocks, and lexical boundaries. */
public final class LexicalCorpus {

  private LexicalCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Line comments
    list.add(
        CompatibilityScenario.simple(
            "lexical.comment.single-line",
            ScenarioCategory.LEXICAL,
            "Prefix## this is a comment\nSuffix"));

    list.add(
        CompatibilityScenario.simple(
            "lexical.comment.single-line-standalone",
            ScenarioCategory.LEXICAL,
            "Line 1\n## Standalone comment\nLine 2"));

    list.add(
        CompatibilityScenario.simple(
            "lexical.comment.single-line-eof",
            ScenarioCategory.LEXICAL,
            "Prefix## comment at EOF"));

    // 2. Block comments
    list.add(
        CompatibilityScenario.simple(
            "lexical.comment.block.inline",
            ScenarioCategory.LEXICAL,
            "Before#* block comment *#After"));

    list.add(
        CompatibilityScenario.simple(
            "lexical.comment.block.multiline",
            ScenarioCategory.LEXICAL,
            "Start\n#* line 1\nline 2 *#\nEnd"));

    // 3. Doc comments
    list.add(
        CompatibilityScenario.simple(
            "lexical.comment.doc",
            ScenarioCategory.LEXICAL,
            "Start\n#** doc comment\n@param test *#\nEnd"));

    // 4. Verbatim #[[ ... ]]# blocks
    list.add(
        CompatibilityScenario.simple(
            "lexical.verbatim.basic",
            ScenarioCategory.LEXICAL,
            "Plain #[[ $foo #if(true) bar #end ]]# Tail"));

    list.add(
        CompatibilityScenario.simple(
            "lexical.verbatim.multiline",
            ScenarioCategory.LEXICAL,
            "#[[Line 1\n$var\n#foreach($x in $list)\nLine 4]]#"));

    // 5. Boundary characters: $, $!, $$, etc.
    list.add(
        CompatibilityScenario.simple(
            "lexical.boundary.lone-dollar", ScenarioCategory.LEXICAL, "Cost is $500"));

    list.add(
        CompatibilityScenario.simple(
            "lexical.boundary.double-dollar", ScenarioCategory.LEXICAL, "Two dollars: $$"));

    list.add(
        CompatibilityScenario.simple(
            "lexical.boundary.dollar-exclamation", ScenarioCategory.LEXICAL, "Exclamation: $!"));

    list.add(
        CompatibilityScenario.of(
            "lexical.boundary.adjacent-identifier-formal",
            ScenarioCategory.LEXICAL,
            "${user.name}World",
            ContextFactory.of("user", java.util.Map.of("name", "Hello"))));

    list.add(
        CompatibilityScenario.of(
            "lexical.boundary.adjacent-identifier-simple",
            ScenarioCategory.LEXICAL,
            "$user.name World",
            ContextFactory.of("user", java.util.Map.of("name", "Hello"))));

    return list;
  }
}
