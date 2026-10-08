package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateLanguageServerM41IntegrationTest {

  @Test
  @DisplayName(
      "Full LSP JSON-RPC flow: initialize advertises workspaceSymbolProvider, handles queries, "
          + "empty query returns empty array, and dynamically invalidates on watched file events")
  void testEndToEndWorkspaceSymbolFlow(@TempDir Path workspaceDir) throws IOException {
    // 1. Prepare schema and template files in the workspace directory
    Path schemaFile = workspaceDir.resolve("order.vt-schema.json");
    String schemaContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "order",
          "parameters": {
            "order": {
              "name": "order",
              "type": { "kind": "named", "name": "OrderHeader" }
            }
          },
          "types": {
            "OrderHeader": {
              "name": "OrderHeader",
              "properties": {
                "orderId": {
                  "name": "orderId",
                  "type": { "kind": "class", "className": "java.lang.String" }
                },
                "totalAmount": {
                  "name": "totalAmount",
                  "type": { "kind": "class", "className": "java.lang.Double" }
                }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaContent, StandardCharsets.UTF_8);

    Path templateFile = workspaceDir.resolve("order.vtl");
    String templateContent =
        """
        #macro(renderOrder $o)
          <div>Order #$o.orderId</div>
        #end
        #macro(formatCurrency $amt)
          $$amt
        #end
        """;
    Files.writeString(templateFile, templateContent, StandardCharsets.UTF_8);

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // 2. Initialize
    String initReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri());
    String initResp = server.handleMessage(initReq);
    assertNotNull(initResp);
    assertTrue(
        initResp.contains("\"workspaceSymbolProvider\": true")
            || initResp.contains("\"workspaceSymbolProvider\":true"),
        "initialize must advertise workspaceSymbolProvider: true");

    // 3. Initialized notification
    assertNull(
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));

    // 4. Empty / blank / null queries must return empty array result: []
    String emptyQueryReq =
        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"\"}}";
    String emptyQueryResp = server.handleMessage(emptyQueryReq);
    assertNotNull(emptyQueryResp);
    assertTrue(
        emptyQueryResp.contains("\"result\":[]"),
        "Empty query must return empty array: " + emptyQueryResp);

    String blankQueryReq =
        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"  "
            + " \"}}";
    String blankQueryResp = server.handleMessage(blankQueryReq);
    assertNotNull(blankQueryResp);
    assertTrue(
        blankQueryResp.contains("\"result\":[]"),
        "Blank query must return empty array: " + blankQueryResp);

    String missingQueryReq =
        "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"workspace/symbol\",\"params\":{}}";
    String missingQueryResp = server.handleMessage(missingQueryReq);
    assertNotNull(missingQueryResp);
    assertTrue(
        missingQueryResp.contains("\"result\":[]"),
        "Missing query param must return empty array: " + missingQueryResp);

    // 5. Query schema type "OrderHeader"
    String typeReq =
        "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"OrderHeader\"}}";
    String typeResp = server.handleMessage(typeReq);
    assertNotNull(typeResp);
    assertTrue(typeResp.contains("\"name\":\"OrderHeader\""), "Must return OrderHeader symbol");
    assertTrue(typeResp.contains("\"kind\":5"), "OrderHeader must have SymbolKind.CLASS (5)");
    assertTrue(
        typeResp.contains("order.vt-schema.json"), "Must point to order.vt-schema.json location");

    // 6. Query property "orderId"
    String propReq =
        "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"orderId\"}}";
    String propResp = server.handleMessage(propReq);
    assertNotNull(propResp);
    assertTrue(propResp.contains("\"name\":\"orderId\""), "Must return orderId symbol");
    assertTrue(propResp.contains("\"kind\":7"), "orderId must have SymbolKind.PROPERTY (7)");
    assertTrue(
        propResp.contains("\"containerName\":\"OrderHeader\""), "Container must be OrderHeader");

    // 7. Query root parameter "order"
    String paramReq =
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"order\"}}";
    String paramResp = server.handleMessage(paramReq);
    assertNotNull(paramResp);
    assertTrue(paramResp.contains("\"name\":\"order\""), "Must return root parameter order");
    assertTrue(
        paramResp.contains("\"kind\":13"), "Root parameter must have SymbolKind.VARIABLE (13)");

    // 8. Query macro "renderOrder"
    String macroReq =
        "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"renderOrder\"}}";
    String macroResp = server.handleMessage(macroReq);
    assertNotNull(macroResp);
    assertTrue(macroResp.contains("\"name\":\"renderOrder\""), "Must return renderOrder macro");
    assertTrue(macroResp.contains("\"kind\":12"), "Macro must have SymbolKind.FUNCTION (12)");
    assertTrue(macroResp.contains("order.vtl"), "Must point to order.vtl location");

    // 9. CamelCase query "OH" -> OrderHeader
    String camelReq =
        "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"OH\"}}";
    String camelResp = server.handleMessage(camelReq);
    assertNotNull(camelResp);
    assertTrue(
        camelResp.contains("\"name\":\"OrderHeader\""), "CamelCase OH must match OrderHeader");

    // 10. Dynamic invalidation via workspace/didChangeWatchedFiles (delete template file)
    String didChangeTemplateDelete =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [
                  {
                    "uri": "%s",
                    "type": 3
                  }
                ]
              }
            }
            """,
            templateFile.toUri());
    assertNull(server.handleMessage(didChangeTemplateDelete));

    // renderOrder should now be evicted
    String macroAfterDeleteResp = server.handleMessage(macroReq);
    assertNotNull(macroAfterDeleteResp);
    assertFalse(
        macroAfterDeleteResp.contains("\"name\":\"renderOrder\""),
        "Deleted template macros must be invalidated from index");

    // 11. Dynamic invalidation via workspace/didChangeWatchedFiles (delete schema file)
    String didChangeSchemaDelete =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [
                  {
                    "uri": "%s",
                    "type": 3
                  }
                ]
              }
            }
            """,
            schemaFile.toUri());
    assertNull(server.handleMessage(didChangeSchemaDelete));

    // OrderHeader should now be evicted
    String typeAfterDeleteResp = server.handleMessage(typeReq);
    assertNotNull(typeAfterDeleteResp);
    assertFalse(
        typeAfterDeleteResp.contains("\"name\":\"OrderHeader\""),
        "Deleted schema types must be invalidated from index");

    // 12. Dynamic creation of a new schema via watched files
    Path newSchema = workspaceDir.resolve("product.vt-schema.json");
    String newSchemaContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "product",
          "types": {
            "ProductDetail": {
              "name": "ProductDetail",
              "properties": {
                "sku": { "name": "sku", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """;
    Files.writeString(newSchema, newSchemaContent, StandardCharsets.UTF_8);

    String didChangeSchemaCreate =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [
                  {
                    "uri": "%s",
                    "type": 1
                  }
                ]
              }
            }
            """,
            newSchema.toUri());
    assertNull(server.handleMessage(didChangeSchemaCreate));

    // Now query "ProductDetail"
    String productReq =
        "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"ProductDetail\"}}";
    String productResp = server.handleMessage(productReq);
    assertNotNull(productResp);
    assertTrue(
        productResp.contains("\"name\":\"ProductDetail\""),
        "Newly created schema type must be indexed and searchable");
  }
}
