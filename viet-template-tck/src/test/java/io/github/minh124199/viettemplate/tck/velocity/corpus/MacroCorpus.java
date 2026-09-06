package io.github.minh124199.viettemplate.tck.velocity.corpus;

import io.github.minh124199.viettemplate.tck.velocity.scenario.CompatibilityScenario;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ContextFactory;
import io.github.minh124199.viettemplate.tck.velocity.scenario.ScenarioCategory;
import java.util.ArrayList;
import java.util.List;

/** Compatibility scenarios covering #macro, block macros (#@), and #define directives. */
public final class MacroCorpus {

  private MacroCorpus() {}

  public static List<CompatibilityScenario> scenarios() {
    List<CompatibilityScenario> list = new ArrayList<>();

    // 1. Simple macro with no parameters
    list.add(
        CompatibilityScenario.simple(
            "macro.basic.no-params",
            ScenarioCategory.MACRO,
            "#macro(sayHello)Hello World#end#sayHello()"));

    // 2. Macro with parameters
    list.add(
        CompatibilityScenario.simple(
            "macro.basic.with-params",
            ScenarioCategory.MACRO,
            "#macro(greet $name $greeting)$greeting, $name!#end#greet('Alice' 'Hi')"));

    // 3. Macro with default parameter expressions
    list.add(
        CompatibilityScenario.simple(
            "macro.default-params.provided",
            ScenarioCategory.MACRO,
            "#macro(greet $name $greeting='Hello')$greeting, $name!#end#greet('Bob' 'Hey')"));

    list.add(
        CompatibilityScenario.simple(
            "macro.default-params.omitted",
            ScenarioCategory.MACRO,
            "#macro(greet $name $greeting='Hello')$greeting, $name!#end#greet('Bob')"));

    // 4. Call-before-definition (top-level macro discovery pass)
    list.add(
        CompatibilityScenario.simple(
            "macro.call-before-definition",
            ScenarioCategory.MACRO,
            "#forwardCall('test')#macro(forwardCall $arg)called:$arg#end"));

    // 5. Scoping: parameter shadowing and context isolation
    list.add(
        CompatibilityScenario.builder("macro.scope.parameter-shadowing", ScenarioCategory.MACRO)
            .template(
                "#set($x = 'outer')#macro(testScope $x)inside:$x;#end#testScope('inner')after:$x")
            .observeContext("x")
            .build());

    // 6. Block macro (#@) exposing $bodyContent
    list.add(
        CompatibilityScenario.simple(
            "macro.block.body-content-single",
            ScenarioCategory.BLOCK_MACRO,
            "#macro(card $title)<div"
                + " class=\"card\"><h3>$title</h3><div>$bodyContent</div></div>#end#@card('MyTitle')Inner"
                + " Body#end"));

    list.add(
        CompatibilityScenario.simple(
            "macro.block.body-content-multiple",
            ScenarioCategory.BLOCK_MACRO,
            "#macro(repeatTwice)$bodyContent | $bodyContent#end#@repeatTwice()HELLO#end"));

    // 7. #define directive capturing lazy block
    list.add(
        CompatibilityScenario.of(
            "define.lazy-evaluation",
            ScenarioCategory.DEFINE,
            "#define($block)Greeting: $name#end#set($name = 'Alice')$block; #set($name ="
                + " 'Bob')$block",
            ContextFactory.of("name", "Initial")));

    return list;
  }
}
