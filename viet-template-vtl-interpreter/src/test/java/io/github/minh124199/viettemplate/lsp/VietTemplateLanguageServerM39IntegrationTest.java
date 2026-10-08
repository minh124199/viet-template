package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.lsp.models.NavUserRecord;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateLanguageServerM39IntegrationTest {

  @Test
  @DisplayName(
      "Full LSP JSON-RPC flow: initialize advertises referencesProvider, textDocument/references"
          + " returns results")
  void testEndToEndReferencesFlow(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavUserRecord.java");
    String javaSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavUserRecord(
            String name,
            int age
        ) {}
        """;
    Files.writeString(javaFile, javaSource);

    Path templateFile = workspaceDir.resolve("welcome.vtl");
    Files.writeString(templateFile, "Welcome $user.name! Again $user.name!");
    String docUri = templateFile.toUri().toString();

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // 1. Initialize with workspace root
    String initReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri());
    String initResp = server.handleMessage(initReq);
    assertNotNull(initResp);
    assertTrue(
        initResp.contains("\"referencesProvider\": true")
            || initResp.contains("\"referencesProvider\":true"),
        "initialize must advertise referencesProvider: true");

    // 2. Initialized notification
    assertNull(
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));

    // 3. Register Java model schema
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", docUri);
    server.languageService().schemaResolver().registerSchema(docUri, res.schemas().get(docUri));

    // 4. didOpen
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
                  "text": "Welcome $user.name! Again $user.name!"
                }
              }
            }
            """,
            docUri);
    assertNull(server.handleMessage(didOpen));

    // 5. textDocument/references with includeDeclaration = false
    String refReqWithoutDecl =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "textDocument/references",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 0, "character": 15 },
                "context": { "includeDeclaration": false }
              }
            }
            """,
            docUri);
    String refRespWithoutDecl = server.handleMessage(refReqWithoutDecl);
    assertNotNull(refRespWithoutDecl);
    assertTrue(refRespWithoutDecl.contains("\"result\":["));
    // Expect 2 template locations, 0 java source locations
    assertFalse(refRespWithoutDecl.contains("NavUserRecord.java"));

    // 6. textDocument/references with includeDeclaration = true
    String refReqWithDecl =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "textDocument/references",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 0, "character": 15 },
                "context": { "includeDeclaration": true }
              }
            }
            """,
            docUri);
    String refRespWithDecl = server.handleMessage(refReqWithDecl);
    assertNotNull(refRespWithDecl);
    assertTrue(refRespWithDecl.contains("NavUserRecord.java"));

    // 7. didChange: update content to have only 1 usage
    String didChange =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "textDocument/didChange",
              "params": {
                "textDocument": { "uri": "%s", "version": 2 },
                "contentChanges": [{ "text": "Welcome $user.name!" }]
              }
            }
            """,
            docUri);
    assertNull(server.handleMessage(didChange));

    String refReqAfterChange =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 4,
              "method": "textDocument/references",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 0, "character": 15 },
                "context": { "includeDeclaration": false }
              }
            }
            """,
            docUri);
    String refRespAfterChange = server.handleMessage(refReqAfterChange);
    assertNotNull(refRespAfterChange);
    // Now exactly 1 template usage
    assertTrue(refRespAfterChange.contains("\"line\":0"));

    // 8. didChangeWatchedFiles (delete template file)
    String watchedDelete =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [{ "uri": "%s", "type": 3 }]
              }
            }
            """,
            docUri);
    assertNull(server.handleMessage(watchedDelete));

    // 9. Shutdown & exit
    String shutdownResp =
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"shutdown\"}");
    assertNotNull(shutdownResp);
    assertTrue(shutdownResp.contains("\"result\":null"));
    assertNull(server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}"));
  }

  @Test
  @DisplayName("LSP textDocument/references recovers gracefully from malformed syntax")
  void testMalformedRecovery() {
    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}");

    String uri = "file:///malformed.vtl";
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
                  "text": "#set($x = "
                }
              }
            }
            """,
            uri);
    server.handleMessage(didOpen);

    String refReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "textDocument/references",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 0, "character": 6 },
                "context": { "includeDeclaration": true }
              }
            }
            """,
            uri);
    String resp = server.handleMessage(refReq);
    assertNotNull(resp);
    assertTrue(
        resp.contains("\"result\":[]"),
        "Malformed template should return empty list without error");
  }
}
