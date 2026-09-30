package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompletionProviderTest {

  private CanonicalSchemaResolver schemaResolver;
  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "test.vt",
        "parameters": {
          "user": {
            "name": "user",
            "type": { "kind": "named", "name": "User" },
            "required": true
          },
          "title": {
            "name": "title",
            "type": { "kind": "class", "className": "java.lang.String" },
            "required": false
          }
        },
        "types": {
          "User": {
            "name": "User",
            "properties": {
              "id": {
                "name": "id",
                "type": { "kind": "primitive", "primitiveKind": "LONG" }
              },
              "name": {
                "name": "name",
                "type": { "kind": "class", "className": "java.lang.String" }
              },
              "email": {
                "name": "email",
                "type": { "kind": "class", "className": "java.lang.String" }
              }
            }
          }
        }
      }
      """;

  @BeforeEach
  void setUp() {
    schemaResolver = new CanonicalSchemaResolver();
    schemaResolver.registerSchema("test.vt", SCHEMA);
  }

  @Test
  @DisplayName("Complete root variables at '$' sigil")
  void testCompleteRootVariables() {
    TemplateDocument doc = new TemplateDocument("test.vt", 1, "Hello $");
    // Position at '$' (offset 7)
    CompletionList list =
        CompletionProvider.complete(
            doc, Position.of(0, 7), schemaResolver, MemberAccessPolicy.standard());

    assertFalse(list.isIncomplete());
    List<CompletionItem> items = list.items();
    assertTrue(items.stream().anyMatch(i -> i.label().equals("user")));
    assertTrue(items.stream().anyMatch(i -> i.label().equals("title")));

    // Deterministic sorting check: sorted alphabetically by sortText / label
    for (int i = 0; i < items.size() - 1; i++) {
      assertTrue(items.get(i).sortText().compareTo(items.get(i + 1).sortText()) <= 0);
    }
  }

  @Test
  @DisplayName("Complete root variables with prefix '$u'")
  void testCompleteRootVariablesWithPrefix() {
    TemplateDocument doc = new TemplateDocument("test.vt", 1, "Hello $u");
    CompletionList list =
        CompletionProvider.complete(
            doc, Position.of(0, 8), schemaResolver, MemberAccessPolicy.standard());

    List<CompletionItem> items = list.items();
    assertEquals(1, items.size());
    assertEquals("user", items.get(0).label());
  }

  @Test
  @DisplayName("Complete directives at '#' sigil")
  void testCompleteDirectives() {
    TemplateDocument doc = new TemplateDocument("test.vt", 1, "#");
    CompletionList list =
        CompletionProvider.complete(
            doc, Position.of(0, 1), schemaResolver, MemberAccessPolicy.standard());

    List<CompletionItem> items = list.items();
    assertTrue(items.stream().anyMatch(i -> i.label().equals("foreach")));
    assertTrue(items.stream().anyMatch(i -> i.label().equals("if")));
    assertTrue(items.stream().anyMatch(i -> i.label().equals("set")));
  }

  @Test
  @DisplayName("Complete receiver members on '$user.'")
  void testCompleteReceiverMembers() {
    TemplateDocument doc = new TemplateDocument("test.vt", 1, "Hello $user.");
    CompletionList list =
        CompletionProvider.complete(
            doc, Position.of(0, 12), schemaResolver, MemberAccessPolicy.standard());

    List<CompletionItem> items = list.items();
    assertEquals(3, items.size());
    assertEquals("email", items.get(0).label());
    assertEquals("id", items.get(1).label());
    assertEquals("name", items.get(2).label());

    // Verify sensitive properties like "class" or "classLoader" are absent
    assertTrue(items.stream().noneMatch(i -> i.label().equalsIgnoreCase("class")));
  }

  @Test
  @DisplayName("Complete in-template local variables from #set and #foreach")
  void testCompleteLocalVariables() {
    String text = "#set($localMsg = 'hi')\n#foreach($item in $user.email)\n$i\n#end";
    TemplateDocument doc = new TemplateDocument("test.vt", 1, text);

    // Position after "$i" on line 2 (offset in line 2)
    // Line 2 is "$i" -> length 2. Position (2, 2)
    CompletionList list =
        CompletionProvider.complete(
            doc, Position.of(2, 2), schemaResolver, MemberAccessPolicy.standard());

    List<CompletionItem> items = list.items();
    assertTrue(items.stream().anyMatch(i -> i.label().equals("item")));
  }
}
