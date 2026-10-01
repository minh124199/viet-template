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

  @Test
  @DisplayName("Handling $/cancelRequest and unknown notifications without error")
  void testCancelRequestAndUnknownNotifications() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // Notification before initialize is cleanly ignored
    String preInitNotif =
        "{\"jsonrpc\":\"2.0\",\"method\":\"$/cancelRequest\",\"params\":{\"id\":1}}";
    assertNull(server.handleMessage(preInitNotif));

    // Initialize
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

    // $/cancelRequest notification handled cleanly returning null
    String cancelNotif =
        "{\"jsonrpc\":\"2.0\",\"method\":\"$/cancelRequest\",\"params\":{\"id\":42}}";
    assertNull(server.handleMessage(cancelNotif));

    // Unknown notification handled cleanly returning null
    String unknownNotif =
        "{\"jsonrpc\":\"2.0\",\"method\":\"window/workDoneProgress/cancel\",\"params\":{\"token\":\"abc\"}}";
    assertNull(server.handleMessage(unknownNotif));

    // Unknown request returns standard method not found error
    String unknownReq =
        "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"custom/nonExistent\",\"params\":{}}";
    String errResp = server.handleMessage(unknownReq);
    assertNotNull(errResp);
    assertTrue(errResp.contains("-32601"));
    assertTrue(errResp.contains("Method not found"));
  }

  @Test
  @DisplayName("Full lifecycle with stale update rejection over LSP message API")
  void testStaleUpdateRejectionLifecycle() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

    String uri = "file:///workspace/stale-flow.vt";

    // 1. open v1 (valid)
    String openV1 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didOpen",
              "params": {
                "textDocument": {
                  "uri": "%s",
                  "languageId": "viet-template",
                  "version": 1,
                  "text": "Hello v1"
                }
              }
            }
            """,
            uri);
    assertNull(server.handleMessage(openV1));
    List<String> n1 = server.drainNotifications();
    assertEquals(1, n1.size());
    assertTrue(n1.get(0).contains("\"diagnostics\":[]"));

    // 2. change v2 (broken syntax)
    String changeV2 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 2 },
                "contentChanges": [
                  { "text": "#if( broken" }
                ]
              }
            }
            """,
            uri);
    assertNull(server.handleMessage(changeV2));
    List<String> n2 = server.drainNotifications();
    assertEquals(1, n2.size());
    assertTrue(n2.get(0).contains("SYNTAX:PARSE_ERROR"));

    // 3. stale change v1 (trying to overwrite v2 with an older packet) -> rejected!
    String staleV1 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 1 },
                "contentChanges": [
                  { "text": "Stale packet" }
                ]
              }
            }
            """,
            uri);
    assertNull(server.handleMessage(staleV1));
    List<String> nStale = server.drainNotifications();
    assertEquals(0, nStale.size(), "Stale change must be rejected and produce no diagnostics");

    // 4. change v3 (repaired syntax)
    String changeV3 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 3 },
                "contentChanges": [
                  { "text": "Hello v3 repaired" }
                ]
              }
            }
            """,
            uri);
    assertNull(server.handleMessage(changeV3));
    List<String> n3 = server.drainNotifications();
    assertEquals(1, n3.size());
    assertTrue(n3.get(0).contains("\"diagnostics\":[]"));

    // 5. close document
    String close =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didClose",
              "params": {
                "textDocument": { "uri": "%s" }
              }
            }
            """,
            uri);
    assertNull(server.handleMessage(close));
    List<String> nClose = server.drainNotifications();
    assertEquals(1, nClose.size());
    assertTrue(nClose.get(0).contains("\"diagnostics\":[]"));

    // 6. reopen v1 -> accepted
    String reopenV1 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didOpen",
              "params": {
                "textDocument": {
                  "uri": "%s",
                  "languageId": "viet-template",
                  "version": 1,
                  "text": "Reopened at v1"
                }
              }
            }
            """,
            uri);
    assertNull(server.handleMessage(reopenV1));
    List<String> nReopen = server.drainNotifications();
    assertEquals(1, nReopen.size());
    assertTrue(nReopen.get(0).contains("\"diagnostics\":[]"));
  }
}
