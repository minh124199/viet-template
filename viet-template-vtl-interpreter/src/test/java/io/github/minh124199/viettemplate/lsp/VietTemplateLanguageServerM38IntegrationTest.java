package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.lsp.models.NavUserRecord;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VietTemplateLanguageServerM38IntegrationTest {

  @Test
  @DisplayName(
      "End-to-end LSP JSON-RPC message flow: initialize, didOpen, definition navigates to Java"
          + " source")
  void testEndToEndJavaSourceDefinitionFlow(@TempDir Path workspaceDir) throws IOException {
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
    Files.writeString(templateFile, "Welcome $user.name!");
    String docUri = templateFile.toUri().toString();

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // 1. Initialize with rootUri
    String initReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri());
    String initResp = server.handleMessage(initReq);
    assertNotNull(initResp);
    assertTrue(initResp.contains("\"definitionProvider\""));

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
                  "text": "Welcome $user.name!"
                }
              }
            }
            """,
            docUri);
    assertNull(server.handleMessage(didOpen));

    // 5. textDocument/definition for $user.name (line 0, col 15)
    String defReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "textDocument/definition",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 0, "character": 15 }
              }
            }
            """,
            docUri);
    String defResp = server.handleMessage(defReq);
    assertNotNull(defResp);
    assertTrue(defResp.contains("\"result\""));
    assertTrue(defResp.contains("NavUserRecord.java"));
    assertTrue(defResp.contains("\"start\":{\"line\":3")); // component name is on line 3

    // 6. textDocument/definition for root $user (line 0, col 10)
    String rootDefReq =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "textDocument/definition",
              "params": {
                "textDocument": { "uri": "%s" },
                "position": { "line": 0, "character": 10 }
              }
            }
            """,
            docUri);
    String rootDefResp = server.handleMessage(rootDefReq);
    assertNotNull(rootDefResp);
    assertTrue(rootDefResp.contains("\"result\""));
    assertTrue(rootDefResp.contains("NavUserRecord.java"));
    assertTrue(rootDefResp.contains("\"start\":{\"line\":2")); // record class is on line 2

    // 7. workspace/didChangeWatchedFiles modifying NavUserRecord.java
    String modifiedJava =
        """
        // Header comment 1
        // Header comment 2
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavUserRecord(
            String name,
            int age
        ) {}
        """;
    try {
      Thread.sleep(50);
    } catch (InterruptedException ignored) {
    }
    Files.writeString(javaFile, modifiedJava);

    String watchNotification =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [
                  { "uri": "%s", "type": 2 }
                ]
              }
            }
            """,
            javaFile.toUri());
    assertNull(server.handleMessage(watchNotification));

    // 8. Re-query definition after file modification
    String defRespAfterEdit = server.handleMessage(defReq);
    assertNotNull(defRespAfterEdit);
    assertTrue(defRespAfterEdit.contains("\"start\":{\"line\":5")); // shifted by 2 lines: 3 + 2 = 5
  }

  @Test
  @DisplayName("Full stream transport session with Java definition navigation")
  void testStreamTransportDefinitionSession(@TempDir Path workspaceDir) throws IOException {
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
    Files.writeString(templateFile, "Welcome $user.name!");
    String docUri = templateFile.toUri().toString();

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // Register Java model schema
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", docUri);
    server.languageService().schemaResolver().registerSchema(docUri, res.schemas().get(docUri));

    String initMsg =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri());
    String openMsg =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{\"textDocument\":{\"uri\":\"%s\",\"languageId\":\"viet-template\",\"version\":1,\"text\":\"Welcome"
                + " $user.name!\"}}}",
            docUri);
    String defMsg =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":0,\"character\":15}}}",
            docUri);
    String shutdownMsg = "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"shutdown\"}";
    String exitMsg = "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}";

    ByteArrayOutputStream clientIn = new ByteArrayOutputStream();
    writeFrame(clientIn, initMsg);
    writeFrame(clientIn, openMsg);
    writeFrame(clientIn, defMsg);
    writeFrame(clientIn, shutdownMsg);
    writeFrame(clientIn, exitMsg);

    ByteArrayInputStream serverIn = new ByteArrayInputStream(clientIn.toByteArray());
    ByteArrayOutputStream serverOut = new ByteArrayOutputStream();

    server.run(serverIn, serverOut);

    String output = serverOut.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("\"id\":10"), "Expected initialize response");
    assertTrue(output.contains("\"id\":11"), "Expected definition response");
    assertTrue(
        output.contains("NavUserRecord.java"), "Definition response should point to Java file");
    assertTrue(output.contains("\"id\":12"), "Expected shutdown response");
  }

  private static void writeFrame(ByteArrayOutputStream out, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    String header = "Content-Length: " + body.length + "\r\n\r\n";
    out.write(header.getBytes(StandardCharsets.US_ASCII));
    out.write(body);
  }
}
