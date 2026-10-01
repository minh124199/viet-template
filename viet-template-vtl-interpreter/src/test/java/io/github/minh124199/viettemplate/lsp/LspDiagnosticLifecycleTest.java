package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspDiagnosticLifecycleTest {

  private static final String SCHEMA =
      """
      {
        "format": "viet-template-contract-schema/1",
        "schemaVersion": 1,
        "templateId": "file:///workspace/diag-test.vt",
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
  @DisplayName("Diagnostics cleared after syntax error is repaired")
  void testDiagnosticsClearedAfterSyntaxRepair() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

    String uri = "file:///workspace/syntax-repair.vt";

    // 1. Open with syntax error (unclosed #if)
    String didOpen =
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
                  "text": "#if($true) unclosed block"
                }
              }
            }
            """,
            uri);
    server.handleMessage(didOpen);

    List<String> notifications1 = server.drainNotifications();
    assertEquals(1, notifications1.size());
    String n1 = notifications1.get(0);
    assertTrue(n1.contains("textDocument/publishDiagnostics"));
    assertTrue(n1.contains("SYNTAX:PARSE_ERROR"));
    assertFalse(n1.contains("\"diagnostics\":[]"));

    // 2. Change repairing syntax error (v2)
    String didChangeRepair =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 2 },
                "contentChanges": [
                  { "text": "#if($true) closed block #end" }
                ]
              }
            }
            """,
            uri);
    server.handleMessage(didChangeRepair);

    List<String> notifications2 = server.drainNotifications();
    assertEquals(1, notifications2.size());
    String n2 = notifications2.get(0);
    assertTrue(n2.contains("textDocument/publishDiagnostics"));
    assertTrue(
        n2.contains("\"diagnostics\":[]"), "Expected empty diagnostics array after repair: " + n2);
  }

  @Test
  @DisplayName("Diagnostics cleared with empty array on document close")
  void testDiagnosticsClearedOnDocumentClose() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

    String uri = "file:///workspace/close-clean.vt";

    // Open with error
    String didOpen =
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
                  "text": "Broken #if"
                }
              }
            }
            """,
            uri);
    server.handleMessage(didOpen);
    server.drainNotifications();

    // Close document
    String didClose =
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
    server.handleMessage(didClose);

    List<String> notifications = server.drainNotifications();
    assertEquals(1, notifications.size());
    String n = notifications.get(0);
    assertTrue(n.contains("textDocument/publishDiagnostics"));
    assertTrue(n.contains("\"diagnostics\":[]"));
  }

  @Test
  @DisplayName("Stale change rejection does not publish diagnostics")
  void testStaleChangeDoesNotPublishDiagnostics() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

    String uri = "file:///workspace/stale-diag.vt";

    // Open v1 with valid text
    String didOpen =
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
                  "text": "Valid template v1"
                }
              }
            }
            """,
            uri);
    server.handleMessage(didOpen);
    server.drainNotifications();

    // Change v2 with valid text
    String didChangeV2 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 2 },
                "contentChanges": [
                  { "text": "Valid template v2" }
                ]
              }
            }
            """,
            uri);
    server.handleMessage(didChangeV2);
    List<String> notificationsV2 = server.drainNotifications();
    assertEquals(1, notificationsV2.size());
    assertTrue(notificationsV2.get(0).contains("\"diagnostics\":[]"));

    // Stale change v1 with syntax error -> MUST be rejected and produce NO notifications
    String staleChangeV1 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 1 },
                "contentChanges": [
                  { "text": "#if( broken" }
                ]
              }
            }
            """,
            uri);
    server.handleMessage(staleChangeV1);

    List<String> staleNotifications = server.drainNotifications();
    assertEquals(0, staleNotifications.size(), "Stale update must not publish diagnostics");

    // Change v3 -> accepted and publishes diagnostics
    String didChangeV3 =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 3 },
                "contentChanges": [
                  { "text": "Valid template v3" }
                ]
              }
            }
            """,
            uri);
    server.handleMessage(didChangeV3);
    List<String> notificationsV3 = server.drainNotifications();
    assertEquals(1, notificationsV3.size());
    assertTrue(notificationsV3.get(0).contains("\"diagnostics\":[]"));
  }

  @Test
  @DisplayName("Authoritative schema diagnostics cleared after semantic repair")
  void testSchemaDiagnosticsClearedAfterRepair() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.registerSchema("file:///workspace/diag-test.vt", SCHEMA);
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

    String uri = "file:///workspace/diag-test.vt";

    // Open v1 with invalid property access $user.nonExistent
    String didOpen =
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
                  "text": "Hello $user.nonExistent"
                }
              }
            }
            """,
            uri);
    server.handleMessage(didOpen);

    List<String> n1 = server.drainNotifications();
    assertEquals(1, n1.size());
    assertTrue(n1.get(0).contains("VTLS:2104")); // Property not found

    // Repair to valid property $user.name in v2
    String didChange =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 2 },
                "contentChanges": [
                  { "text": "Hello $user.name" }
                ]
              }
            }
            """,
            uri);
    server.handleMessage(didChange);

    List<String> n2 = server.drainNotifications();
    assertEquals(1, n2.size());
    assertTrue(n2.get(0).contains("\"diagnostics\":[]"));
  }
}
