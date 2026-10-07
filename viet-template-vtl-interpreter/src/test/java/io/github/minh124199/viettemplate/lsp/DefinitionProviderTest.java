package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
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

  @Test
  @DisplayName("Scope isolation across sibling foreach loops")
  void testForeachScopeIsolationAcrossSiblingLoops() {
    String text =
        """
        #foreach($item in $users)
          User: $item
        #end
        #foreach($item in $products)
          Product: $item
        #end
        """;
    TemplateDocument doc = new TemplateDocument("file:///sibling-loops.vt", 1, text);
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();

    // Line 4 has "  Product: $item". Position (4, 12)
    List<LocationInfo> defs = DefinitionProvider.definition(doc, Position.of(4, 12), resolver);
    assertFalse(defs.isEmpty());
    LocationInfo loc = defs.get(0);
    // Must navigate to line 3 (second foreach), NOT line 0 (first foreach)
    assertEquals(3, loc.range().start().line());
  }

  @Test
  @DisplayName("Foreach loop variable does not shadow schema parameter outside the loop")
  void testForeachVariableDoesNotShadowSchemaOutsideLoop() {
    String schema =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "file:///scope-test.vt",
          "parameters": {
            "item": {
              "name": "item",
              "type": { "kind": "primitive", "name": "string" },
              "required": true
            }
          }
        }
        """;
    String text = "$item\n#foreach($item in $items)\n  $item\n#end";
    TemplateDocument doc = new TemplateDocument("file:///scope-test.vt", 1, text);
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    resolver.registerSchema("file:///scope-test.vt", schema);

    // Position on $item at line 0 (outside loop)
    List<LocationInfo> defs = DefinitionProvider.definition(doc, Position.of(0, 2), resolver);
    assertFalse(defs.isEmpty());
    LocationInfo loc = defs.get(0);
    // Must navigate to schema URI, NOT in-template line 1
    assertTrue(loc.uri().startsWith("schema://"));
    assertTrue(loc.uri().contains("item"));

    // Position on $item at line 2 (inside loop)
    List<LocationInfo> loopDefs = DefinitionProvider.definition(doc, Position.of(2, 3), resolver);
    assertFalse(loopDefs.isEmpty());
    LocationInfo loopLoc = loopDefs.get(0);
    assertEquals("file:///scope-test.vt", loopLoc.uri());
    assertEquals(1, loopLoc.range().start().line());
  }

  @Test
  @DisplayName("Disambiguate properties with same name across different schema types")
  void testDisambiguatePropertiesWithSameNameAcrossTypes(@TempDir Path tempDir) throws IOException {
    Path templateFile = tempDir.resolve("orders.vt");
    Files.writeString(templateFile, "$user.id\n$order.id");

    Path schemaFile = tempDir.resolve("orders.vt-schema.json");
    String schemaContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "orders",
          "parameters": {
            "user": {
              "name": "user",
              "type": { "kind": "named", "name": "User" }
            },
            "order": {
              "name": "order",
              "type": { "kind": "named", "name": "Order" }
            }
          },
          "types": {
            "User": {
              "name": "User",
              "properties": {
                "id": { "name": "id", "type": { "kind": "primitive", "name": "long" } }
              }
            },
            "Order": {
              "name": "Order",
              "properties": {
                "id": { "name": "id", "type": { "kind": "primitive", "name": "long" } }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaContent);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    String docUri = templateFile.toUri().toString();
    resolver.resolveSchema(docUri);

    TemplateDocument doc = new TemplateDocument(docUri, 1, "$user.id\n$order.id");

    // $user.id -> line 0, char 7
    List<LocationInfo> userDefs = DefinitionProvider.definition(doc, Position.of(0, 7), resolver);
    assertFalse(userDefs.isEmpty());
    int userLine = userDefs.get(0).range().start().line();

    // $order.id -> line 1, char 8
    List<LocationInfo> orderDefs = DefinitionProvider.definition(doc, Position.of(1, 8), resolver);
    assertFalse(orderDefs.isEmpty());
    int orderLine = orderDefs.get(0).range().start().line();

    // Order's id must be defined on a line STRICTLY AFTER User's id in the schema file
    assertTrue(
        orderLine > userLine,
        "Order id line (" + orderLine + ") should follow User id line (" + userLine + ")");
  }
}
