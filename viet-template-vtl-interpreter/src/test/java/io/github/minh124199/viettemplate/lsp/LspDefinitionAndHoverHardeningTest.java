package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspDefinitionAndHoverHardeningTest {

  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "file:///workspace/defhover.vt",
        "parameters": {
          "user": {
            "name": "user",
            "type": { "kind": "named", "name": "User" },
            "required": true,
            "documentation": "Authenticated user object"
          },
          "items": {
            "name": "items",
            "type": {
              "kind": "parameterized",
              "rawType": "java.util.List",
              "arguments": [{ "kind": "named", "name": "Item" }]
            },
            "required": true
          }
        },
        "types": {
          "User": {
            "name": "User",
            "properties": {
              "fullName": {
                "name": "fullName",
                "type": { "kind": "class", "className": "java.lang.String" }
              }
            }
          },
          "Item": {
            "name": "Item",
            "properties": {
              "title": {
                "name": "title",
                "type": { "kind": "class", "className": "java.lang.String" }
              }
            }
          }
        }
      }
      """;

  private TemplateLanguageService service;
  private final String uri = "file:///workspace/defhover.vt";

  @BeforeEach
  void setUp() {
    service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.registerSchema(uri, SCHEMA);
  }

  // --- Hover Hardening Tests ---

  @Test
  @DisplayName("Hover directly on # character and directive keywords")
  void testHoverOnDirectiveKeywordAndHash() {
    String text = "#if($true) Yes #end";
    service.openDocument(uri, 1, text);

    // Hover directly on '#' at offset 0
    Optional<HoverInfo> hashHover = service.hover(uri, Position.of(0, 0));
    assertTrue(hashHover.isPresent(), "Hover on '#' should recognize '#if' directive");
    assertTrue(hashHover.get().markdownValue().contains("#if"));

    // Hover on 'i' in '#if'
    Optional<HoverInfo> ifHover = service.hover(uri, Position.of(0, 1));
    assertTrue(ifHover.isPresent());
    assertTrue(ifHover.get().markdownValue().contains("#if"));

    // Hover on 'end' in '#end'
    int endOffset = text.indexOf("end");
    Position endPos = service.getDocument(uri).orElseThrow().offsetToPosition(endOffset);
    Optional<HoverInfo> endHover = service.hover(uri, endPos);
    assertTrue(endHover.isPresent());
    assertTrue(endHover.get().markdownValue().contains("#end"));
  }

  @Test
  @DisplayName("Hover on isolated # or invalid directive text does not crash")
  void testHoverOnIsolatedHash() {
    service.openDocument(uri, 1, "# not_a_directive");
    assertDoesNotThrow(
        () -> {
          Optional<HoverInfo> h = service.hover(uri, Position.of(0, 0));
          assertTrue(h.isEmpty());
        });

    service.openDocument(uri, 2, "###");
    assertDoesNotThrow(
        () -> {
          Optional<HoverInfo> h = service.hover(uri, Position.of(0, 1));
          assertTrue(h.isEmpty());
        });
  }

  @Test
  @DisplayName("Hover on malformed templates without unhandled exceptions")
  void testHoverOnMalformedTemplates() {
    // Unclosed if
    service.openDocument(uri, 1, "#if($user.fullName");
    assertDoesNotThrow(
        () -> {
          Optional<HoverInfo> h = service.hover(uri, Position.of(0, 6));
          // Should safely return either hover info or empty, never throw
          assertNotNull(h);
        });

    // Incomplete set
    service.openDocument(uri, 2, "#set($x = ");
    assertDoesNotThrow(
        () -> {
          Optional<HoverInfo> h = service.hover(uri, Position.of(0, 5));
          assertNotNull(h);
        });

    // Corrupted syntax
    service.openDocument(uri, 3, "><{{{}}}}{{{{$$$%%%%");
    assertDoesNotThrow(
        () -> {
          Optional<HoverInfo> h = service.hover(uri, Position.of(0, 3));
          assertTrue(h.isEmpty());
        });
  }

  @Test
  @DisplayName("Hover on schema parameters, properties, and loop variables")
  void testHoverOnLanguageSymbols() {
    String text = "#foreach($item in $items) $item.title $foreach.index #end $user.fullName";
    service.openDocument(uri, 1, text);

    TemplateDocument doc = service.getDocument(uri).orElseThrow();

    // Hover on $user
    int userIdx = text.indexOf("user");
    Optional<HoverInfo> userHover = service.hover(uri, doc.offsetToPosition(userIdx));
    assertTrue(userHover.isPresent());
    assertTrue(userHover.get().markdownValue().contains("**$user**: `User`"));
    assertTrue(userHover.get().markdownValue().contains("Authenticated user object"));

    // Hover on $user.fullName property
    int nameIdx = text.indexOf("fullName");
    Optional<HoverInfo> nameHover = service.hover(uri, doc.offsetToPosition(nameIdx));
    assertTrue(nameHover.isPresent());
    assertTrue(nameHover.get().markdownValue().contains("fullName"));

    // Hover on loop item $item
    int itemIdx = text.indexOf("$item.title") + 2;
    Optional<HoverInfo> itemHover = service.hover(uri, doc.offsetToPosition(itemIdx));
    assertTrue(itemHover.isPresent());
    assertTrue(itemHover.get().markdownValue().contains("$item"));

    // Hover on $foreach metadata
    int feIdx = text.indexOf("foreach.index") + 2;
    Optional<HoverInfo> feHover = service.hover(uri, doc.offsetToPosition(feIdx));
    assertTrue(feHover.isPresent());
    assertTrue(feHover.get().markdownValue().contains("ForeachMetadata"));
  }

  @Test
  @DisplayName("Hover with Vietnamese diacritics and emojis in template")
  void testHoverWithUnicodeContext() {
    String text = "Xin chào 🇻🇳 $user.fullName 😀 Chúc mừng!";
    service.openDocument(uri, 1, text);

    TemplateDocument doc = service.getDocument(uri).orElseThrow();
    int userOffset = text.indexOf("user");
    Optional<HoverInfo> hover = service.hover(uri, doc.offsetToPosition(userOffset));
    assertTrue(hover.isPresent());
    assertTrue(hover.get().markdownValue().contains("**$user**: `User`"));
  }

  @Test
  @DisplayName("Hover at out-of-bounds positions returns empty without exception")
  void testHoverOutOfBounds() {
    service.openDocument(uri, 1, "Hello world");
    assertDoesNotThrow(
        () -> {
          Optional<HoverInfo> h1 = service.hover(uri, Position.of(999, 999));
          assertTrue(h1.isEmpty());

          Optional<HoverInfo> h2 = service.hover(uri, Position.of(0, 500));
          assertTrue(h2.isEmpty());
        });
  }

  // --- Definition Hardening Tests ---

  @Test
  @DisplayName("Definition lookup on schema parameter and property")
  void testDefinitionLookup() {
    String text = "Hello $user.fullName";
    service.openDocument(uri, 1, text);
    TemplateDocument doc = service.getDocument(uri).orElseThrow();

    // Definition of $user
    int userIdx = text.indexOf("user");
    List<LocationInfo> userLocs = service.definition(uri, doc.offsetToPosition(userIdx));
    assertFalse(userLocs.isEmpty());
    assertTrue(userLocs.get(0).uri().contains("parameters/user"));

    // Definition of fullName property
    int nameIdx = text.indexOf("fullName");
    List<LocationInfo> nameLocs = service.definition(uri, doc.offsetToPosition(nameIdx));
    assertFalse(nameLocs.isEmpty());
    assertTrue(nameLocs.get(0).uri().contains("fullName"));
  }

  @Test
  @DisplayName("Definition lookup on in-template #set and #foreach declarations")
  void testInTemplateDefinitions() {
    String text = "#set($local = 42)\nValue: $local\n#foreach($elem in $items)\nItem: $elem\n#end";
    service.openDocument(uri, 1, text);
    TemplateDocument doc = service.getDocument(uri).orElseThrow();

    // Definition of $local usage
    int localUsage = text.indexOf("Value: $local") + "Value: $".length();
    List<LocationInfo> localLocs = service.definition(uri, doc.offsetToPosition(localUsage));
    assertFalse(localLocs.isEmpty());
    assertEquals(uri, localLocs.get(0).uri());
    assertEquals(0, localLocs.get(0).range().start().line()); // Defined on line 0 (#set)

    // Definition of $elem usage
    int elemUsage = text.indexOf("Item: $elem") + "Item: $".length();
    List<LocationInfo> elemLocs = service.definition(uri, doc.offsetToPosition(elemUsage));
    assertFalse(elemLocs.isEmpty());
    assertEquals(uri, elemLocs.get(0).uri());
    assertEquals(2, elemLocs.get(0).range().start().line()); // Defined on line 2 (#foreach)
  }

  @Test
  @DisplayName("Definition lookup on malformed templates without unhandled exceptions")
  void testDefinitionOnMalformedTemplates() {
    service.openDocument(uri, 1, "#if( unclosed $user.");
    assertDoesNotThrow(
        () -> {
          List<LocationInfo> locs = service.definition(uri, Position.of(0, 15));
          assertNotNull(locs);
        });

    service.openDocument(uri, 2, "random junk !@#$%^&*()");
    assertDoesNotThrow(
        () -> {
          List<LocationInfo> locs = service.definition(uri, Position.of(0, 5));
          assertNotNull(locs);
          assertTrue(locs.isEmpty());
        });
  }

  @Test
  @DisplayName("Definition lookup at out-of-bounds positions returns empty list")
  void testDefinitionOutOfBounds() {
    service.openDocument(uri, 1, "Hello world");
    assertDoesNotThrow(
        () -> {
          List<LocationInfo> locs1 = service.definition(uri, Position.of(999, 999));
          assertNotNull(locs1);
          assertTrue(locs1.isEmpty());

          List<LocationInfo> locs2 = service.definition(uri, Position.of(0, 500));
          assertNotNull(locs2);
        });
  }

  @Test
  @DisplayName("Definition lookup on unregistered URI returns empty list")
  void testDefinitionUnregisteredUri() {
    List<LocationInfo> locs = service.definition("file:///unknown.vt", Position.of(0, 0));
    assertNotNull(locs);
    assertTrue(locs.isEmpty());
  }
}
