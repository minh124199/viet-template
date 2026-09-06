package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityConfiguration;
import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/** Compatibility scenarios covering space gobbling policies (LINES vs NONE). */
public final class SpaceGobblingCorpus {

  private SpaceGobblingCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    CompatibilityConfiguration linesConfig =
        CompatibilityConfiguration.defaultConfiguration()
            .withSpaceGobbling(CompatibilityConfiguration.SpaceGobblingMode.LINES);

    CompatibilityConfiguration noneConfig =
        CompatibilityConfiguration.defaultConfiguration()
            .withSpaceGobbling(CompatibilityConfiguration.SpaceGobblingMode.NONE);

    // 1. Standalone directive lines in LINES mode
    list.add(
        CompatibilityScenario.builder(
                "space-gobbling.lines.standalone-if", ScenarioCategory.WHITESPACE)
            .template("Header\n#if(true)\nInside\n#end\nFooter")
            .configuration(linesConfig)
            .build());

    // 2. Standalone directive lines in NONE mode (preserves all whitespace and newlines)
    list.add(
        CompatibilityScenario.builder(
                "space-gobbling.none.standalone-if", ScenarioCategory.WHITESPACE)
            .template("Header\n#if(true)\nInside\n#end\nFooter")
            .configuration(noneConfig)
            .build());

    // 3. Indented directives in LINES mode
    list.add(
        CompatibilityScenario.builder(
                "space-gobbling.lines.indented-foreach", ScenarioCategory.WHITESPACE)
            .template("List:\n  #foreach($item in [1..2])\n  - $item\n  #end\nDone")
            .configuration(linesConfig)
            .build());

    // 4. Inline directives in LINES mode (whitespace must be preserved!)
    list.add(
        CompatibilityScenario.builder(
                "space-gobbling.lines.inline-directive", ScenarioCategory.WHITESPACE)
            .template("Status: #if(true)ACTIVE#else INACTIVE#end (verified)")
            .configuration(linesConfig)
            .build());

    // 5. Standalone #set directive line in LINES mode
    list.add(
        CompatibilityScenario.builder(
                "space-gobbling.lines.standalone-set", ScenarioCategory.WHITESPACE)
            .template("Before\n#set($x = 10)\nAfter: $x")
            .configuration(linesConfig)
            .build());

    return list;
  }
}
