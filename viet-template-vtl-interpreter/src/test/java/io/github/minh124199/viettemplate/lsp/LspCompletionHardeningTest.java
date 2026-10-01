package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspCompletionHardeningTest {

  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "file:///workspace/comp.vt",
        "parameters": {
          "user": {
            "name": "user",
            "type": { "kind": "named", "name": "User" },
            "required": true
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
              },
              "email": {
                "name": "email",
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
  private final String uri = "file:///workspace/comp.vt";

  @BeforeEach
  void setUp() {
    service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.registerSchema(uri, SCHEMA);
  }

  @Test
  @DisplayName("Completion on empty document returns empty list without exception")
  void testEmptyDocumentCompletion() {
    service.openDocument(uri, 1, "");
    CompletionList res = service.complete(uri, Position.of(0, 0));
    assertNotNull(res);
    assertTrue(res.items().isEmpty());
  }

  @Test
  @DisplayName("Completion at # at EOF suggests all directives")
  void testDirectiveTriggerAtEof() {
    service.openDocument(uri, 1, "#");
    CompletionList res = service.complete(uri, Position.of(0, 1));
    assertNotNull(res);
    assertFalse(res.items().isEmpty());
    assertTrue(res.items().stream().anyMatch(i -> "if".equals(i.label())));
    assertTrue(res.items().stream().anyMatch(i -> "foreach".equals(i.label())));
    assertTrue(res.items().stream().anyMatch(i -> "set".equals(i.label())));
  }

  @Test
  @DisplayName("Completion at prefix #i suggests if and include")
  void testDirectivePrefix() {
    service.openDocument(uri, 1, "#i");
    CompletionList res = service.complete(uri, Position.of(0, 2));
    assertNotNull(res);
    assertTrue(res.items().stream().allMatch(i -> i.label().startsWith("i")));
    assertTrue(res.items().stream().anyMatch(i -> "if".equals(i.label())));
  }

  @Test
  @DisplayName("Completion at $ and variations ($!, ${, $!{)")
  void testVariableTriggerVariations() {
    // $ at EOF
    service.openDocument(uri, 1, "$");
    CompletionList res1 = service.complete(uri, Position.of(0, 1));
    assertTrue(res1.items().stream().anyMatch(i -> "user".equals(i.label())));

    // ${ at EOF
    service.openDocument(uri, 2, "${");
    CompletionList res2 = service.complete(uri, Position.of(0, 2));
    assertTrue(res2.items().stream().anyMatch(i -> "user".equals(i.label())));

    // $! at EOF
    service.openDocument(uri, 3, "$!");
    CompletionList res3 = service.complete(uri, Position.of(0, 2));
    assertTrue(res3.items().stream().anyMatch(i -> "user".equals(i.label())));

    // $!{ at EOF
    service.openDocument(uri, 4, "$!{");
    CompletionList res4 = service.complete(uri, Position.of(0, 3));
    assertTrue(res4.items().stream().anyMatch(i -> "user".equals(i.label())));
  }

  @Test
  @DisplayName("Completion on member access with and without braces")
  void testMemberAccessCompletion() {
    service.openDocument(uri, 1, "Hello $user.");
    CompletionList res = service.complete(uri, Position.of(0, 12));
    assertTrue(res.items().stream().anyMatch(i -> "fullName".equals(i.label())));
    assertTrue(res.items().stream().anyMatch(i -> "email".equals(i.label())));

    service.openDocument(uri, 2, "Hello ${user.");
    CompletionList res2 = service.complete(uri, Position.of(0, 13));
    assertTrue(res2.items().stream().anyMatch(i -> "fullName".equals(i.label())));
  }

  @Test
  @DisplayName("Completion on malformed and incomplete templates does not crash")
  void testMalformedTemplatesCompletion() {
    // Double dot $user..
    service.openDocument(uri, 1, "Hello $user..");
    assertDoesNotThrow(() -> service.complete(uri, Position.of(0, 13)));

    // Incomplete #if condition
    service.openDocument(uri, 2, "#if($user.");
    CompletionList ifComp = service.complete(uri, Position.of(0, 10));
    assertNotNull(ifComp);
    assertTrue(ifComp.items().stream().anyMatch(i -> "fullName".equals(i.label())));

    // Incomplete #set assignment
    service.openDocument(uri, 3, "#set($x = $user.");
    CompletionList setComp = service.complete(uri, Position.of(0, 16));
    assertNotNull(setComp);
    assertTrue(setComp.items().stream().anyMatch(i -> "fullName".equals(i.label())));

    // Unclosed string literal
    service.openDocument(uri, 4, "#set($x = \"$user.");
    assertDoesNotThrow(() -> service.complete(uri, Position.of(0, 17)));

    // Stray characters and syntax garbage
    service.openDocument(uri, 5, ")(*&^%$#@! $user.f");
    CompletionList garbageComp = service.complete(uri, Position.of(0, 18));
    assertNotNull(garbageComp);
    assertTrue(garbageComp.items().stream().anyMatch(i -> "fullName".equals(i.label())));
  }

  @Test
  @DisplayName("Completion with Vietnamese text and Unicode emojis")
  void testUnicodeContextCompletion() {
    String text = "Xin chào 🇻🇳 bạn $user.f 🚀";
    service.openDocument(uri, 1, text);

    int posAfterF = text.indexOf("$user.f") + "$user.f".length();
    Position pos = service.getDocument(uri).orElseThrow().offsetToPosition(posAfterF);
    CompletionList comp = service.complete(uri, pos);
    assertNotNull(comp);
    assertTrue(comp.items().stream().anyMatch(i -> "fullName".equals(i.label())));
  }

  @Test
  @DisplayName("Completion at out-of-bounds positions returns empty without exception")
  void testOutOfBoundsCompletion() {
    service.openDocument(uri, 1, "Hello world");
    assertDoesNotThrow(
        () -> {
          CompletionList res1 = service.complete(uri, Position.of(999, 999));
          assertNotNull(res1);
          assertTrue(res1.items().isEmpty());

          CompletionList res2 = service.complete(uri, Position.of(0, 500));
          assertNotNull(res2);
        });
  }

  @Test
  @DisplayName("Completion with unregistered URI returns empty list")
  void testUnregisteredUri() {
    CompletionList res = service.complete("file:///workspace/unknown.vt", Position.of(0, 0));
    assertNotNull(res);
    assertTrue(res.items().isEmpty());
  }
}
