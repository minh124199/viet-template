package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VietTemplateLanguageServerTest {

  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "file:///workspace/test.vt",
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

  @Test
  @DisplayName("Full LSP lifecycle over handleMessage API")
  void testFullLifecycleOverHandleMessage() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.registerSchema("file:///workspace/test.vt", SCHEMA);

    // 1. Request before initialize fails
    String preInitResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/completion\",\"params\":{}}");
    assertTrue(preInitResp.contains("-32002"));
    assertTrue(preInitResp.contains("Server not initialized"));

    // 2. Initialize
    String initResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{}}");
    assertTrue(initResp.contains("\"capabilities\""));
    assertTrue(initResp.contains("\"completionProvider\""));
    assertTrue(initResp.contains("\"hoverProvider\""));
    assertTrue(initResp.contains("\"definitionProvider\""));

    // 3. Initialized notification
    assertNull(
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));

    // 4. didOpen notification -> triggers push diagnostics
    String didOpen =
        """
        {
          "jsonrpc": "2.0",
          "method": "textDocument/didOpen",
          "params": {
            "textDocument": {
              "uri": "file:///workspace/test.vt",
              "languageId": "viet-template",
              "version": 1,
              "text": "Hello $user.name"
            }
          }
        }
        """;
    assertNull(server.handleMessage(didOpen));

    List<String> notifications = server.drainNotifications();
    assertEquals(1, notifications.size());
    assertTrue(notifications.get(0).contains("textDocument/publishDiagnostics"));
    assertTrue(notifications.get(0).contains("file:///workspace/test.vt"));

    // 5. Completion request
    String completionReq =
        """
        {
          "jsonrpc": "2.0",
          "id": 3,
          "method": "textDocument/completion",
          "params": {
            "textDocument": { "uri": "file:///workspace/test.vt" },
            "position": { "line": 0, "character": 7 }
          }
        }
        """;
    String compResp = server.handleMessage(completionReq);
    assertTrue(compResp.contains("\"label\":\"user\""));

    // 6. Hover request
    String hoverReq =
        """
        {
          "jsonrpc": "2.0",
          "id": 4,
          "method": "textDocument/hover",
          "params": {
            "textDocument": { "uri": "file:///workspace/test.vt" },
            "position": { "line": 0, "character": 8 }
          }
        }
        """;
    String hoverResp = server.handleMessage(hoverReq);
    assertTrue(hoverResp.contains("**$user**: `User`"));

    // 7. didChange notification
    String didChange =
        """
        {
          "jsonrpc": "2.0",
          "method": "textDocument/didChange",
          "params": {
            "textDocument": { "uri": "file:///workspace/test.vt", "version": 2 },
            "contentChanges": [
              { "text": "Hello $unknownVar" }
            ]
          }
        }
        """;
    assertNull(server.handleMessage(didChange));

    List<String> changeNotifications = server.drainNotifications();
    assertEquals(1, changeNotifications.size());
    assertTrue(
        changeNotifications.get(0).contains("VTLS:2101")); // Unresolved variable diagnostic emitted

    // 8. didClose notification
    String didClose =
        """
        {
          "jsonrpc": "2.0",
          "method": "textDocument/didClose",
          "params": {
            "textDocument": { "uri": "file:///workspace/test.vt" }
          }
        }
        """;
    assertNull(server.handleMessage(didClose));

    // 9. Shutdown request
    String shutdownResp =
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"shutdown\"}");
    assertTrue(shutdownResp.contains("\"result\":null"));

    // 10. Request after shutdown fails
    String postShutdown =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"textDocument/completion\",\"params\":{}}");
    assertTrue(postShutdown.contains("-32600"));

    // 11. Exit notification
    assertNull(server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}"));
    assertEquals(LspProtocolAdapter.ServerState.EXITED, server.adapter().state());
  }

  @Test
  @DisplayName("Full LSP stream session via run(InputStream, OutputStream)")
  void testStreamSession() throws IOException {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    String initMsg = "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{}}";
    String shutdownMsg = "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"shutdown\"}";
    String exitMsg = "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}";

    ByteArrayOutputStream clientInStream = new ByteArrayOutputStream();
    LspStreamTransport.writeMessage(clientInStream, initMsg);
    LspStreamTransport.writeMessage(clientInStream, shutdownMsg);
    LspStreamTransport.writeMessage(clientInStream, exitMsg);

    ByteArrayInputStream in = new ByteArrayInputStream(clientInStream.toByteArray());
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    server.run(in, out);

    ByteArrayInputStream serverResponses = new ByteArrayInputStream(out.toByteArray());
    String resp1 = LspStreamTransport.readMessage(serverResponses);
    assertNotNull(resp1);
    assertTrue(resp1.contains("\"id\":10"));
    assertTrue(resp1.contains("\"capabilities\""));

    String resp2 = LspStreamTransport.readMessage(serverResponses);
    assertNotNull(resp2);
    assertTrue(resp2.contains("\"id\":11"));
    assertTrue(resp2.contains("\"result\":null"));

    assertNull(LspStreamTransport.readMessage(serverResponses));
  }
}
