package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HoverProviderTest {

  private CanonicalSchemaResolver schemaResolver;
  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "hover.vt",
        "parameters": {
          "user": {
            "name": "user",
            "type": { "kind": "named", "name": "User" },
            "required": true,
            "documentation": "Primary user profile."
          }
        },
        "types": {
          "User": {
            "name": "User",
            "properties": {
              "name": {
                "name": "name",
                "type": { "kind": "class", "className": "java.lang.String" },
                "nullable": false
              }
            }
          }
        }
      }
      """;

  @BeforeEach
  void setUp() {
    schemaResolver = new CanonicalSchemaResolver();
    schemaResolver.registerSchema("hover.vt", SCHEMA);
  }

  @Test
  @DisplayName("Hover on root variable returns parameter markdown signature with exact range")
  void testHoverOnRootVariable() {
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, "Hello $user");
    // Position on "user" (e.g. index 8, character 8)
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 8), schemaResolver);

    assertTrue(hover.isPresent());
    HoverInfo info = hover.get();
    assertTrue(info.markdown().contains("**$user**: `User` *(required)*"));
    assertTrue(info.markdown().contains("Primary user profile."));
    assertTrue(info.range().isPresent());
    assertEquals(Range.of(0, 7, 0, 11), info.range().get());
  }

  @Test
  @DisplayName("Hover on property step returns property type")
  void testHoverOnPropertyStep() {
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, "Hello $user.name");
    // Position on "name" (index 13)
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 13), schemaResolver);

    assertTrue(hover.isPresent());
    HoverInfo info = hover.get();
    assertTrue(info.markdown().contains("**name**: `String`"));
  }

  @Test
  @DisplayName("Hover on #foreach directive keyword returns documentation")
  void testHoverOnDirective() {
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, "#foreach($item in $items) #end");
    // Position on "foreach" (index 3)
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 3), schemaResolver);

    assertTrue(hover.isPresent());
    HoverInfo info = hover.get();
    assertTrue(info.markdown().contains("`#foreach`"));
    assertTrue(info.markdown().contains("loop"));
  }

  @Test
  @DisplayName("Hover on in-scope local variable from #set and loop variable from #foreach")
  void testHoverOnLocalAndLoopVariables() {
    String text =
        "#set($localMsg = 'hi')\n$localMsg\n#foreach($item in $user)\n$item\n$foreach.index\n#end";
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, text);

    // 1. Hover on $localMsg on line 1
    Optional<HoverInfo> localHover = HoverProvider.hover(doc, Position.of(1, 2), schemaResolver);
    assertTrue(localHover.isPresent());
    assertTrue(localHover.get().markdown().contains("**$localMsg**: `Object`"));
    assertTrue(localHover.get().markdown().contains("template local variable"));

    // 2. Hover on $item inside loop on line 3
    Optional<HoverInfo> loopHover = HoverProvider.hover(doc, Position.of(3, 2), schemaResolver);
    assertTrue(loopHover.isPresent());
    assertTrue(loopHover.get().markdown().contains("**$item**"));
    assertTrue(loopHover.get().markdown().contains("loop item variable"));

    // 3. Hover on $foreach metadata on line 4
    Optional<HoverInfo> foreachHover = HoverProvider.hover(doc, Position.of(4, 2), schemaResolver);
    assertTrue(foreachHover.isPresent());
    assertTrue(foreachHover.get().markdown().contains("**$foreach**: `ForeachMetadata`"));
  }

  @Test
  @DisplayName("Hover on reference inside #if condition")
  void testHoverInsideIfCondition() {
    String text = "#if($user.name)\nWelcome\n#end";
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, text);

    // Hover on 'name' in $user.name (line 0, col 11)
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 11), schemaResolver);
    assertTrue(hover.isPresent());
    assertTrue(hover.get().markdown().contains("**name**: `String`"));
  }

  @Test
  @DisplayName("Hover on non-code plain text returns empty")
  void testHoverOnPlainText() {
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, "Just plain text without symbols");
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 5), schemaResolver);

    assertTrue(hover.isEmpty());
  }
}
