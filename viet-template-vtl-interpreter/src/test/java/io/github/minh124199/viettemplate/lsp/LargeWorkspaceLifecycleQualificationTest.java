package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LargeWorkspaceLifecycleQualificationTest {

  /**
   * Constructs an integrated workspace with >= 1,100 model/schema types, >= 5,500 properties, and
   * >= 100 templates with macros across multiple schema formats (.vt-schema.json, .d.ts,
   * .schema.json, .contract).
   */
  private static void setupLargeWorkspace(Path workspaceDir) throws IOException {
    // 1. Contract Schema (.vt-schema.json): 400 types with 5 properties each = 400 types, 2,000
    // properties
    Path vtFile = workspaceDir.resolve("template_00.vt-schema.json");
    StringBuilder vtSb = new StringBuilder(250_000);
    vtSb.append("{\n")
        .append("  \"format\": \"viet-template-contract-schema/1\",\n")
        .append("  \"schemaVersion\": 1,\n")
        .append("  \"templateId\": \"template_00\",\n")
        .append("  \"parameters\": {\n")
        .append(
            "    \"user\": { \"name\": \"user\", \"type\": { \"kind\": \"named\", \"name\":"
                + " \"VtType_0000\" } }\n")
        .append("  },\n")
        .append("  \"types\": {\n");

    for (int t = 0; t < 400; t++) {
      if (t > 0) vtSb.append(",\n");
      String typeName = String.format("VtType_%04d", t);
      vtSb.append("    \"")
          .append(typeName)
          .append("\": {\n")
          .append("      \"name\": \"")
          .append(typeName)
          .append("\",\n")
          .append("      \"properties\": {\n");
      for (int p = 0; p < 5; p++) {
        if (p > 0) vtSb.append(",\n");
        String propName = String.format("prop_%d", p);
        vtSb.append("        \"")
            .append(propName)
            .append("\": { \"name\": \"")
            .append(propName)
            .append("\", \"type\": { \"kind\": \"class\", \"className\": \"java.lang.String\" } }");
      }
      vtSb.append("\n      }\n    }");
    }
    vtSb.append("\n  }\n}\n");
    Files.writeString(vtFile, vtSb.toString(), StandardCharsets.UTF_8);

    // 2. TypeScript .d.ts: 350 interfaces with 5 properties each = 350 types, 1,750 properties
    Path dtsFile = workspaceDir.resolve("types.d.ts");
    StringBuilder dtsSb = new StringBuilder(200_000);
    for (int t = 0; t < 350; t++) {
      String typeName = String.format("TsType_%04d", t);
      dtsSb.append("export interface ").append(typeName).append(" {\n");
      for (int p = 0; p < 5; p++) {
        dtsSb.append(String.format("  prop_%d: string;\n", p));
      }
      dtsSb.append("}\n\n");
    }
    dtsSb.append("export interface TemplateParameters {\n  tsUser: TsType_0000;\n}\n");
    Files.writeString(dtsFile, dtsSb.toString(), StandardCharsets.UTF_8);

    // 3. JSON Schema (.schema.json): 300 types under $defs with 5 properties each = 300 types,
    // 1,500 properties
    Path jsonFile = workspaceDir.resolve("types.schema.json");
    StringBuilder jsonSb = new StringBuilder(200_000);
    jsonSb
        .append("{\n")
        .append("  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n")
        .append("  \"title\": \"JsonCatalog\",\n")
        .append("  \"$defs\": {\n");
    for (int t = 0; t < 300; t++) {
      if (t > 0) jsonSb.append(",\n");
      String typeName = String.format("JsonType_%04d", t);
      jsonSb
          .append("    \"")
          .append(typeName)
          .append("\": {\n")
          .append("      \"type\": \"object\",\n")
          .append("      \"properties\": {\n");
      for (int p = 0; p < 5; p++) {
        if (p > 0) jsonSb.append(",\n");
        jsonSb.append(String.format("        \"prop_%d\": { \"type\": \"string\" }", p));
      }
      jsonSb.append("\n      }\n    }");
    }
    jsonSb
        .append("\n  },\n")
        .append("  \"properties\": {\n")
        .append("    \"catalogItem\": { \"$ref\": \"#/$defs/JsonType_0000\" }\n")
        .append("  }\n}\n");
    Files.writeString(jsonFile, jsonSb.toString(), StandardCharsets.UTF_8);

    // 4. Contract (.contract): 50 types with 5 properties each = 50 types, 250 properties
    Path contractFile = workspaceDir.resolve("types.contract");
    StringBuilder cSb = new StringBuilder(5_000);
    for (int t = 0; t < 50; t++) {
      cSb.append(
          String.format(
              "model_%02d=io.github.minh124199.viettemplate.lsp.models.NavUserRecord\n", t));
    }
    Files.writeString(contractFile, cSb.toString(), StandardCharsets.UTF_8);

    // 5. Templates with Macros: 100 templates each containing 2 macros (200 macros total)
    for (int docIdx = 0; docIdx < 100; docIdx++) {
      Path tmplPath = workspaceDir.resolve(String.format("template_%02d.vtl", docIdx));
      String content =
          String.format(
              """
              #macro(macro_%02d_0 $arg)
                <div>$arg</div>
              #end
              #macro(macro_%02d_1 $val)
                <span>$val</span>
              #end
              Hello $user.prop_0!
              """,
              docIdx, docIdx);
      Files.writeString(tmplPath, content, StandardCharsets.UTF_8);
    }
  }

  // =========================================================================
  // 1. Large Workspace Fixture Capacity & Discovery
  // =========================================================================

  @Test
  @DisplayName(
      "Fixture Capacity: >= 1,100 types, >= 5,500 properties, 100 templates, >= 6,500 total"
          + " symbols")
  void testLargeWorkspaceCapacityAndIndexing(@TempDir Path workspaceDir) throws IOException {
    setupLargeWorkspace(workspaceDir);

    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);

    int totalSymbols = service.symbolIndex().symbolCount();
    assertTrue(
        totalSymbols >= 6500,
        "Total symbols must be >= 6,500 (1,100 types + 5,500 props + 200 macros), found: "
            + totalSymbols);

    int templateCount = service.referenceIndex().templateCount();
    assertTrue(templateCount >= 100, "Template count must be >= 100, found: " + templateCount);

    // Verify symbols across formats:
    // Contract schema
    List<SymbolInformation> vtSyms = service.workspaceSymbols("VtType_0050");
    assertFalse(vtSyms.isEmpty(), "VtType_0050 must be found");
    assertEquals(SymbolKind.CLASS, vtSyms.get(0).kind());

    // TypeScript
    List<SymbolInformation> tsSyms = service.workspaceSymbols("TsType_0100");
    assertFalse(tsSyms.isEmpty(), "TsType_0100 must be found");
    assertEquals(SymbolKind.INTERFACE, tsSyms.get(0).kind());

    // JSON Schema
    List<SymbolInformation> jsonSyms = service.workspaceSymbols("JsonType_0150");
    assertFalse(jsonSyms.isEmpty(), "JsonType_0150 must be found");
    assertEquals(SymbolKind.CLASS, jsonSyms.get(0).kind());

    // Macros
    List<SymbolInformation> macroSyms = service.workspaceSymbols("macro_42_0");
    assertFalse(macroSyms.isEmpty(), "macro_42_0 must be found");
    assertEquals(SymbolKind.FUNCTION, macroSyms.get(0).kind());
  }

  // =========================================================================
  // 2. Full Server Lifecycle Scenario: End-to-End Flow
  // =========================================================================

  @Test
  @DisplayName(
      "Lifecycle Scenario: initialize -> discovery -> open -> completion -> hover -> def -> ref ->"
          + " prepRename -> symbol -> edit tmpl -> edit schema -> delete schema -> restore -> add"
          + " tmpl -> delete tmpl -> shutdown")
  void testServerLifecycleEndToEndScenario(@TempDir Path workspaceDir) throws IOException {
    setupLargeWorkspace(workspaceDir);

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();

    // 1. Initialize
    String initReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri());
    String initResp = server.handleMessage(initReq);
    assertNotNull(initResp);
    assertTrue(initResp.contains("\"capabilities\""), "Initialize must return capabilities");
    assertTrue(initResp.contains("\"completionProvider\""), "Must advertise completionProvider");
    assertTrue(initResp.contains("\"hoverProvider\""), "Must advertise hoverProvider");
    assertTrue(initResp.contains("\"definitionProvider\""), "Must advertise definitionProvider");
    assertTrue(initResp.contains("\"referencesProvider\""), "Must advertise referencesProvider");
    assertTrue(initResp.contains("\"renameProvider\""), "Must advertise renameProvider");
    assertTrue(
        initResp.contains("\"workspaceSymbolProvider\": true")
            || initResp.contains("\"workspaceSymbolProvider\":true"));

    // 2. Initialized notification
    assertNull(
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));

    // 3. Workspace discovery via workspace/symbol
    String symResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"VtType_0025\"}}");
    assertNotNull(symResp);
    assertTrue(
        symResp.contains("\"name\":\"VtType_0025\""), "Must find discovered symbol VtType_0025");

    String macroResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"macro_15_0\"}}");
    assertNotNull(macroResp);
    assertTrue(
        macroResp.contains("\"name\":\"macro_15_0\""), "Must find discovered macro macro_15_0");

    // 4. textDocument/didOpen on template_00.vtl
    Path tmpl00 = workspaceDir.resolve("template_00.vtl");
    String tmplContent = Files.readString(tmpl00, StandardCharsets.UTF_8);
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
            tmpl00.toUri(), tmplContent.replace("\n", "\\n").replace("\"", "\\\""));
    assertNull(server.handleMessage(didOpen));

    // 5. textDocument/completion on line 6 (Hello $user.)
    String compReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":12}}}",
            tmpl00.toUri());
    String compResp = server.handleMessage(compReq);
    assertNotNull(compResp);
    assertTrue(compResp.contains("\"items\""));
    assertTrue(compResp.contains("prop_0"));

    // 6. textDocument/hover on prop_0
    String hoverReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"textDocument/hover\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14}}}",
            tmpl00.toUri());
    String hoverResp = server.handleMessage(hoverReq);
    assertNotNull(hoverResp);
    assertTrue(hoverResp.contains("**prop_0**"));

    // 7. textDocument/definition on prop_0
    String defReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14}}}",
            tmpl00.toUri());
    String defResp = server.handleMessage(defReq);
    assertNotNull(defResp);
    assertTrue(defResp.contains("template_00.vt-schema.json"));

    // 8. textDocument/references on prop_0
    String refReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"textDocument/references\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14},\"context\":{\"includeDeclaration\":false}}}",
            tmpl00.toUri());
    String refResp = server.handleMessage(refReq);
    assertNotNull(refResp);
    assertTrue(refResp.contains("template_00.vtl"));

    // 9. textDocument/prepareRename on prop_0 (unsupported schema format returns null result)
    String prepReq =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"textDocument/prepareRename\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14}}}",
            tmpl00.toUri());
    String prepResp = server.handleMessage(prepReq);
    assertNotNull(prepResp);
    assertTrue(
        prepResp.contains("\"result\":null"),
        "prepareRename on .vt-schema.json returns null as format is read-only");

    // 10. Edit template via textDocument/didChange (add new macro)
    String updatedContent =
        tmplContent + "\n#macro(lifecycleDynamicMacro $arg)\n  <div>$arg</div>\n#end\n";
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
            tmpl00.toUri(), updatedContent.replace("\n", "\\n").replace("\"", "\\\""));
    assertNull(server.handleMessage(didChange));

    // Verify newly added macro appears in workspace symbols
    String newMacroResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"lifecycleDynamicMacro\"}}");
    assertNotNull(newMacroResp);
    assertTrue(
        newMacroResp.contains("\"name\":\"lifecycleDynamicMacro\""),
        "Updated macro must be searchable");

    // 11. Edit schema: append dynamic interface to types.d.ts
    Path dtsFile = workspaceDir.resolve("types.d.ts");
    String dtsCurrent = Files.readString(dtsFile, StandardCharsets.UTF_8);
    Files.writeString(
        dtsFile,
        dtsCurrent + "\nexport interface DynamicallyAddedTsType {\n  dynamicProp: string;\n}\n",
        StandardCharsets.UTF_8);
    String didChangeDts =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [{ "uri": "%s", "type": 2 }]
              }
            }
            """,
            dtsFile.toUri());
    assertNull(server.handleMessage(didChangeDts));

    // Verify dynamically added TS type is searchable
    String newTsTypeResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"DynamicallyAddedTsType\"}}");
    assertNotNull(newTsTypeResp);
    assertTrue(
        newTsTypeResp.contains("\"name\":\"DynamicallyAddedTsType\""),
        "Newly added TS type must be indexed");

    // 12. Delete schema: delete types.schema.json
    Path jsonFile = workspaceDir.resolve("types.schema.json");
    Files.delete(jsonFile);
    String didDeleteJson =
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
            jsonFile.toUri());
    assertNull(server.handleMessage(didDeleteJson));

    // Verify JsonType_0010 is evicted
    String evictedJsonResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"JsonType_0010\"}}");
    assertNotNull(evictedJsonResp);
    assertTrue(
        evictedJsonResp.contains("\"result\":[]"),
        "Deleted schema types must be evicted from index");

    // 13. Restore schema: re-create types.schema.json
    String restoredJson =
        """
        {
          "title": "JsonCatalogRestored",
          "$defs": {
            "JsonType_0010": {
              "type": "object",
              "properties": { "restoredProp": { "type": "string" } }
            }
          }
        }
        """;
    Files.writeString(jsonFile, restoredJson, StandardCharsets.UTF_8);
    String didRestoreJson =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [{ "uri": "%s", "type": 1 }]
              }
            }
            """,
            jsonFile.toUri());
    assertNull(server.handleMessage(didRestoreJson));

    // Verify JsonType_0010 is restored
    String restoredResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"JsonType_0010\"}}");
    assertNotNull(restoredResp);
    assertTrue(
        restoredResp.contains("\"name\":\"JsonType_0010\""), "Restored schema must be re-indexed");

    // 14. Add template: write extra_tmpl.vtl
    Path extraTmpl = workspaceDir.resolve("extra_tmpl.vtl");
    Files.writeString(
        extraTmpl,
        "#macro(extraLifecycleMacro $m)\n  <span>$m</span>\n#end\n",
        StandardCharsets.UTF_8);
    String didCreateTmpl =
        String.format(
            """
            {
              "jsonrpc": "2.0",
              "method": "workspace/didChangeWatchedFiles",
              "params": {
                "changes": [{ "uri": "%s", "type": 1 }]
              }
            }
            """,
            extraTmpl.toUri());
    assertNull(server.handleMessage(didCreateTmpl));

    String extraMacroResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"extraLifecycleMacro\"}}");
    assertNotNull(extraMacroResp);
    assertTrue(
        extraMacroResp.contains("\"name\":\"extraLifecycleMacro\""),
        "Added template macro must be indexed");

    // 15. Delete template: delete extra_tmpl.vtl
    Files.delete(extraTmpl);
    String didDeleteTmpl =
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
            extraTmpl.toUri());
    assertNull(server.handleMessage(didDeleteTmpl));

    String extraMacroAfterDelete =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":14,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"extraLifecycleMacro\"}}");
    assertNotNull(extraMacroAfterDelete);
    assertTrue(
        extraMacroAfterDelete.contains("\"result\":[]"), "Deleted template macro must be evicted");

    // 16. Repeat searches: verify deterministic results
    String repeat1 =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":15,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"VtType_0001\"}}");
    String repeat2 =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":16,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"VtType_0001\"}}");
    assertNotNull(repeat1);
    assertNotNull(repeat2);
    assertEquals(
        repeat1.replaceAll("\"id\":15", "\"id\":0"),
        repeat2.replaceAll("\"id\":16", "\"id\":0"),
        "Repeat searches must be deterministic");

    // 17. Shutdown and exit
    String shutdownResp =
        server.handleMessage("{\"jsonrpc\":\"2.0\",\"id\":17,\"method\":\"shutdown\"}");
    assertNotNull(shutdownResp);
    assertTrue(shutdownResp.contains("\"result\":null"), "Shutdown must return result: null");

    // Request after shutdown fails with -32600
    String postShutdownResp =
        server.handleMessage(
            "{\"jsonrpc\":\"2.0\",\"id\":18,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"VtType_0001\"}}");
    assertTrue(
        postShutdownResp.contains("-32600"), "Request after shutdown must return error -32600");

    // Exit notification
    assertNull(server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}"));
    assertEquals(LspProtocolAdapter.ServerState.EXITED, server.adapter().state());
  }

  // =========================================================================
  // 3. Incremental Index Stress Under Repeated Edits
  // =========================================================================

  @Test
  @DisplayName(
      "Incremental Index Stress: 50 sequential add/update/close cycles maintain exact index bounds")
  void testIncrementalIndexStressUnderRepeatedEdits(@TempDir Path workspaceDir) throws IOException {
    setupLargeWorkspace(workspaceDir);

    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);

    int baselineSymbols = service.symbolIndex().symbolCount();
    int baselineTemplates = service.referenceIndex().templateCount();

    // Perform 50 rapid sequential cycles of open, update, and close on an active template
    String docUri = "file:///workspace/stress_live.vtl";
    for (int i = 0; i < 50; i++) {
      String macroName = "stressMacro_" + i;
      String v1 = String.format("#macro(%s $arg)\n  <div>$arg</div>\n#end\n", macroName);
      service.openDocument(docUri, 1, v1);

      // Verify macro is indexed
      List<SymbolInformation> found = service.workspaceSymbols(macroName);
      assertEquals(1, found.size(), "Macro must be indexed in cycle " + i);
      assertEquals(macroName, found.get(0).name());

      // Update document with a second macro
      String macroNameB = "bMacro_" + i;
      String v2 = v1 + String.format("#macro(%s $b)\n  <span>$b</span>\n#end\n", macroNameB);
      service.updateDocument(docUri, 2, v2);

      assertEquals(1, service.workspaceSymbols(macroName).size());
      assertEquals(1, service.workspaceSymbols(macroNameB).size());

      // Close document
      service.closeDocument(docUri);

      // Verify both macros are evicted
      assertTrue(
          service.workspaceSymbols(macroName).isEmpty(),
          "Macro must be evicted on close in cycle " + i);
      assertTrue(
          service.workspaceSymbols(macroNameB).isEmpty(),
          "Macro B must be evicted on close in cycle " + i);
    }

    // Verify indices strictly preserved without drift
    assertEquals(
        baselineSymbols,
        service.symbolIndex().symbolCount(),
        "Symbol count must return to exact baseline after 50 cycles");
    assertEquals(
        baselineTemplates,
        service.referenceIndex().templateCount(),
        "Template count must return to exact baseline after 50 cycles");
  }

  // =========================================================================
  // 4. Concurrent LSP Requests Under High Contention
  // =========================================================================

  @Test
  @DisplayName(
      "Concurrent LSP Requests: 240 multithreaded requests across 8 threads with zero deadlocks or"
          + " exceptions")
  void testConcurrentLspRequestsUnderHighContention(@TempDir Path workspaceDir)
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    setupLargeWorkspace(workspaceDir);

    VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
    server.handleMessage(
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
            workspaceDir.toUri()));
    server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}");

    // Open first 5 templates
    for (int i = 0; i < 5; i++) {
      Path p = workspaceDir.resolve(String.format("template_%02d.vtl", i));
      String content = Files.readString(p, StandardCharsets.UTF_8);
      server.handleMessage(
          String.format(
              """
              {
                "jsonrpc": "2.0",
                "method": "textDocument/didOpen",
                "params": {
                  "textDocument": { "uri": "%s", "languageId": "viet-template", "version": 1, "text": "%s" }
                }
              }
              """,
              p.toUri(), content.replace("\n", "\\n").replace("\"", "\\\"")));
    }

    ExecutorService executor = Executors.newFixedThreadPool(8);
    List<Callable<Boolean>> tasks = new ArrayList<>();

    // 240 diverse requests executed in parallel
    for (int i = 0; i < 240; i++) {
      final int taskId = i;
      int op = taskId % 6;
      tasks.add(
          () -> {
            switch (op) {
              case 0 -> {
                // Workspace symbol search
                String query = (taskId % 2 == 0) ? "VtType_" : "macro_";
                String resp =
                    server.handleMessage(
                        String.format(
                            "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"%s\"}}",
                            1000 + taskId, query));
                assertNotNull(resp);
              }
              case 1 -> {
                // Completion request
                int docNum = taskId % 5;
                Path p = workspaceDir.resolve(String.format("template_%02d.vtl", docNum));
                String resp =
                    server.handleMessage(
                        String.format(
                            "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"textDocument/completion\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":12}}}",
                            1000 + taskId, p.toUri()));
                assertNotNull(resp);
              }
              case 2 -> {
                // Hover request
                int docNum = taskId % 5;
                Path p = workspaceDir.resolve(String.format("template_%02d.vtl", docNum));
                String resp =
                    server.handleMessage(
                        String.format(
                            "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"textDocument/hover\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14}}}",
                            1000 + taskId, p.toUri()));
                assertNotNull(resp);
              }
              case 3 -> {
                // Definition request
                int docNum = taskId % 5;
                Path p = workspaceDir.resolve(String.format("template_%02d.vtl", docNum));
                String resp =
                    server.handleMessage(
                        String.format(
                            "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"textDocument/definition\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14}}}",
                            1000 + taskId, p.toUri()));
                assertNotNull(resp);
              }
              case 4 -> {
                // References request
                int docNum = taskId % 5;
                Path p = workspaceDir.resolve(String.format("template_%02d.vtl", docNum));
                String resp =
                    server.handleMessage(
                        String.format(
                            "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"textDocument/references\",\"params\":{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":6,\"character\":14},\"context\":{\"includeDeclaration\":false}}}",
                            1000 + taskId, p.toUri()));
                assertNotNull(resp);
              }
              default -> {
                // didChange notification
                int docNum = taskId % 5;
                Path p = workspaceDir.resolve(String.format("template_%02d.vtl", docNum));
                String content =
                    String.format(
                        "#macro(concurrentMacro_%d $v)\n  <b>$v</b>\n#end\n$user.prop_0", taskId);
                server.handleMessage(
                    String.format(
                        """
                        {
                          "jsonrpc": "2.0",
                          "method": "textDocument/didChange",
                          "params": {
                            "textDocument": { "uri": "%s", "version": %d },
                            "contentChanges": [{ "text": "%s" }]
                          }
                        }
                        """,
                        p.toUri(), taskId + 2, content.replace("\n", "\\n").replace("\"", "\\\"")));
              }
            }
            return true;
          });
    }

    List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(240, futures.size());
    for (Future<Boolean> f : futures) {
      assertTrue(f.isDone());
      assertTrue(f.get(1, TimeUnit.SECONDS), "Concurrent task must complete successfully");
    }
  }

  // =========================================================================
  // 5. Workspace Shutdown & Restart Cycles
  // =========================================================================

  @Test
  @DisplayName(
      "Shutdown & Restart Cycles: 5 consecutive server lifecycles execute cleanly and"
          + " independently")
  void testWorkspaceShutdownAndRestartCycles(@TempDir Path workspaceDir) throws IOException {
    setupLargeWorkspace(workspaceDir);

    for (int cycle = 0; cycle < 5; cycle++) {
      VietTemplateLanguageServer server = VietTemplateLanguageServer.create();
      assertEquals(
          LspProtocolAdapter.ServerState.UNINITIALIZED,
          server.adapter().state(),
          "New server must start in UNINITIALIZED state in cycle " + cycle);

      // 1. Initialize
      String initReq =
          String.format(
              "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"initialize\",\"params\":{\"rootUri\":\"%s\"}}",
              cycle * 10 + 1, workspaceDir.toUri());
      String initResp = server.handleMessage(initReq);
      assertNotNull(initResp);
      assertEquals(LspProtocolAdapter.ServerState.INITIALIZED, server.adapter().state());

      // 2. Initialized
      assertNull(
          server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));

      // 3. Query
      String queryReq =
          String.format(
              "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"workspace/symbol\",\"params\":{\"query\":\"VtType_0001\"}}",
              cycle * 10 + 2);
      String queryResp = server.handleMessage(queryReq);
      assertNotNull(queryResp);
      assertTrue(queryResp.contains("VtType_0001"), "Must find symbol in cycle " + cycle);

      // 4. Shutdown
      String shutdownReq =
          String.format("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"shutdown\"}", cycle * 10 + 3);
      String shutdownResp = server.handleMessage(shutdownReq);
      assertNotNull(shutdownResp);
      assertTrue(shutdownResp.contains("\"result\":null"));
      assertEquals(LspProtocolAdapter.ServerState.SHUTDOWN, server.adapter().state());

      // 5. Post-shutdown rejection
      String postResp = server.handleMessage(queryReq);
      assertTrue(postResp.contains("-32600"));

      // 6. Exit
      assertNull(server.handleMessage("{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}"));
      assertEquals(LspProtocolAdapter.ServerState.EXITED, server.adapter().state());
    }
  }
}
