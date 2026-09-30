package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefinitionProviderTest {

  @Test
  @DisplayName("Navigate from template parameter reference to on-disk schema line")
  void testNavigateFromReferenceToSchema(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("greeting.vt");
    Files.writeString(templateFile, "Hello $user.name");

    Path schemaFile = tempDir.resolve("greeting.vt-schema.json");
    String schemaContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "greeting",
          "parameters": {
            "user": {
              "name": "user",
              "type": { "kind": "named", "name": "User" },
              "required": true
            }
          },
          "types": {
            "User": {
              "name": "User",
              "properties": {
                "name": {
                  "name": "name",
                  "type": { "kind": "class", "className": "java.lang.String" }
                }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    String docUri = templateFile.toUri().toString();
    resolver.resolveSchema(docUri);

    TemplateDocument doc = new TemplateDocument(docUri, 1, "Hello $user.name");

    // 1. Definition of $user (position on user: offset 8)
    List<LocationInfo> userDefs = DefinitionProvider.definition(doc, Position.of(0, 8), resolver);
    assertFalse(userDefs.isEmpty());
    LocationInfo userLoc = userDefs.get(0);
    assertEquals(schemaFile.toUri().toString(), userLoc.uri());
    assertTrue(userLoc.range().start().line() >= 4);

    // 2. Definition of .name (position on name: offset 13)
    List<LocationInfo> nameDefs = DefinitionProvider.definition(doc, Position.of(0, 13), resolver);
    assertFalse(nameDefs.isEmpty());
    LocationInfo nameLoc = nameDefs.get(0);
    assertEquals(schemaFile.toUri().toString(), nameLoc.uri());
    assertTrue(nameLoc.range().start().line() > userLoc.range().start().line());
  }

  @Test
  @DisplayName("Navigate to in-template #set variable declaration")
  void testNavigateToSetVariable() {
    String text = "#set($message = 'welcome')\n$message";
    TemplateDocument doc = new TemplateDocument("file:///template.vt", 1, text);
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();

    // Position on $message on line 1 (offset 29, line 1, char 3)
    List<LocationInfo> defs = DefinitionProvider.definition(doc, Position.of(1, 3), resolver);
    assertFalse(defs.isEmpty());
    LocationInfo loc = defs.get(0);
    assertEquals("file:///template.vt", loc.uri());
    assertEquals(0, loc.range().start().line()); // Defined on line 0 in #set
  }

  @Test
  @DisplayName("Navigate to in-template #foreach loop variable declaration")
  void testNavigateToForeachVariable() {
    String text = "#foreach($item in $items)\n  Item: $item\n#end";
    TemplateDocument doc = new TemplateDocument("file:///loop.vt", 1, text);
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();

    // Position on $item on line 1 (offset 36, line 1, char 10)
    List<LocationInfo> defs = DefinitionProvider.definition(doc, Position.of(1, 10), resolver);
    assertFalse(defs.isEmpty());
    LocationInfo loc = defs.get(0);
    assertEquals("file:///loop.vt", loc.uri());
    assertEquals(0, loc.range().start().line()); // Defined on line 0 in #foreach
  }
}
