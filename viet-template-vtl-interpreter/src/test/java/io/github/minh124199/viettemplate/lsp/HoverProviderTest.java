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
  @DisplayName("Hover on root variable returns parameter markdown signature")
  void testHoverOnRootVariable() {
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, "Hello $user");
    // Position on "user" (e.g. index 8, character 8)
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 8), schemaResolver);

    assertTrue(hover.isPresent());
    HoverInfo info = hover.get();
    assertTrue(info.markdown().contains("**$user**: `User` *(required)*"));
    assertTrue(info.markdown().contains("Primary user profile."));
    assertNotNull(info.range());
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
  @DisplayName("Hover on non-code plain text returns empty")
  void testHoverOnPlainText() {
    TemplateDocument doc = new TemplateDocument("hover.vt", 1, "Just plain text without symbols");
    Optional<HoverInfo> hover = HoverProvider.hover(doc, Position.of(0, 5), schemaResolver);

    assertTrue(hover.isEmpty());
  }
}
