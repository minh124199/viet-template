package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.lsp.models.NavBeanUser;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateLanguageServerM40IntegrationTest {

  @Test
  @DisplayName(
      "Full LSP JSON-RPC flow: initialize advertises renameProvider, prepareRename returns range, "
          + "rename returns WorkspaceEdit, JVM member returns null/error, conflict returns -32600")
  void testEndToEndRenameFlow(@TempDir Path workspaceDir) throws IOException {
    Path templateFile = workspaceDir.resolve("rename.vtl");
    String initialContent = "#set($user = \"Alice\")\nHello $user!\n";
    Files.writeString(templateFile, initialContent);
    String docUri = templateFile.toUri().toString();

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // 1. Initialize
    String initReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri());
    String initResp = server.handleMessage(initReq);
    assertNotNull(initResp);
    assertTrue(
        initResp.contains("\"renameProvider\""),
        "initialize must advertise renameProvider capability");
    assertTrue(
        initResp.contains("\"prepareProvider\": true")
            || initResp.contains("\"prepareProvider\":true"),
        "renameProvider must advertise prepareProvider: true");

    // 2. Initialized notification
    assertNull(
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));

    // 3. didOpen
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
                  "text": "%s"
                }
              }
            }
            """,
            docUri, initialContent.replace("\n", "\\n").replace("\"", "\\\""));
    assertNull(server.handleMessage(didOpen));

    // 4. textDocument/prepareRename on $user (line 1, col 7)
    String prepReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "textDocument/prepareRename",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 1, "character": 7 }
              }
            }
            """,
            docUri);
    String prepResp = server.handleMessage(prepReq);
    assertNotNull(prepResp);
    assertTrue(prepResp.contains("\"placeholder\":\"user\""), "Must return placeholder 'user'");
    assertTrue(prepResp.contains("\"range\""), "Must return range");

    // 5. textDocument/rename on $user to 'admin'
    String renameReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "textDocument/rename",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 1, "character": 7 },
                "newName": "admin"
              }
            }
            """,
            docUri);
    String renameResp = server.handleMessage(renameReq);
    assertNotNull(renameResp);
    assertTrue(renameResp.contains("\"changes\""), "Must return WorkspaceEdit changes");
    assertTrue(
        renameResp.contains("\"newText\":\"admin\""), "Must contain replacement with 'admin'");

    // 6. Conflict error: rename with invalid identifier -> code -32600
    String badIdReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 4,
              "method": "textDocument/rename",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 1, "character": 7 },
                "newName": "123invalid"
              }
            }
            """,
            docUri);
    String badIdResp = server.handleMessage(badIdReq);
    assertNotNull(badIdResp);
    assertTrue(
        badIdResp.contains("\"code\":-32600"),
        "Invalid identifier must return JSON-RPC error -32600");

    // 7. Register JVM model to test JVM member rejection
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "beanUser", docUri);
    server.languageService().schemaResolver().registerSchema(docUri, res.schemas().get(docUri));

    // Update document with JVM member usage
    String docWithJvm = "#set($user = \"Alice\")\nHello $beanUser.name!\n";
    String didChange =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 2 },
                "contentChanges": [{ "text": "%s" }]
              }
            }
            """,
            docUri, docWithJvm.replace("\n", "\\n").replace("\"", "\\\""));
    assertNull(server.handleMessage(didChange));

    // prepareRename on JVM getter (.name at line 1, col 17) -> returns result: null
    String jvmPrepReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 5,
              "method": "textDocument/prepareRename",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 1, "character": 17 }
              }
            }
            """,
            docUri);
    String jvmPrepResp = server.handleMessage(jvmPrepReq);
    assertNotNull(jvmPrepResp);
    assertTrue(
        jvmPrepResp.contains("\"result\":null"), "prepareRename on JVM member must return null");

    // rename on JVM getter -> returns code -32600
    String jvmRenameReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 6,
              "method": "textDocument/rename",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 1, "character": 17 },
                "newName": "renamedName"
              }
            }
            """,
            docUri);
    String jvmRenameResp = server.handleMessage(jvmRenameReq);
    assertNotNull(jvmRenameResp);
    assertTrue(
        jvmRenameResp.contains("\"code\":-32600"),
        "rename on JVM member must return error code -32600");
    assertTrue(
        jvmRenameResp.contains("outside Viet Template's ownership"),
        "Error message must explain JVM rejection");

    // 8. Shutdown & exit
    String shutdownResp =
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"shutdown\"}");
    assertNotNull(shutdownResp);
    assertTrue(shutdownResp.contains("\"result\":null"));
    assertNull(server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}"));
  }
}
