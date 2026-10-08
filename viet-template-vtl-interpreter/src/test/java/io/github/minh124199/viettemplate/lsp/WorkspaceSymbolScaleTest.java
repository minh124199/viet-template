package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSymbolScaleTest {

  @TempDir Path tempDir;

  private CanonicalSchemaResolver schemaResolver;
  private WorkspaceSchemaIndex schemaIndex;
  private WorkspaceSymbolIndex symbolIndex;

  @BeforeEach
  void setUp() throws IOException {
    schemaResolver = new CanonicalSchemaResolver();
    schemaIndex = new WorkspaceSchemaIndex(schemaResolver);
    symbolIndex =
        new WorkspaceSymbolIndex(schemaResolver, schemaIndex, MemberAccessPolicy.standard());

    // 1. Build a synthetic schema containing 1,000 types and 5,000 properties (5 props per type)
    Path schemaFile = tempDir.resolve("scale-test.vt-schema.json");
    StringBuilder sb = new StringBuilder(500_000);
    sb.append("{\n")
        .append("  \"format\": \"viet-template-contract-schema/1\",\n")
        .append("  \"schemaVersion\": 1,\n")
        .append("  \"templateId\": \"scale-test\",\n")
        .append("  \"types\": {\n");

    for (int t = 0; t < 1000; t++) {
      if (t > 0) sb.append(",\n");
      String typeName = String.format("Type%04d", t);
      sb.append("    \"")
          .append(typeName)
          .append("\": {\n")
          .append("      \"name\": \"")
          .append(typeName)
          .append("\",\n")
          .append("      \"properties\": {\n");
      for (int p = 0; p < 5; p++) {
        if (p > 0) sb.append(",\n");
        String propName = String.format("prop%d", p);
        sb.append("        \"")
            .append(propName)
            .append("\": {\n")
            .append("          \"name\": \"")
            .append(propName)
            .append("\",\n")
            .append(
                "          \"type\": { \"kind\": \"class\", \"className\": \"java.lang.String\""
                    + " }\n")
            .append("        }");
      }
      sb.append("\n      }\n    }");
    }
    sb.append("\n  }\n}\n");

    Files.writeString(schemaFile, sb.toString(), StandardCharsets.UTF_8);
    schemaResolver.registerSchemaFile(schemaFile);
    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(schemaFile.toUri().toString());
    assertTrue(schema.isPresent(), "Schema must be successfully parsed and registered");
    symbolIndex.indexSchema(schemaFile.toUri().toString(), schema.get(), Optional.of(schemaFile));

    // 2. Build 20 template documents each containing 5 macros (total 100 macros)
    for (int docIdx = 0; docIdx < 20; docIdx++) {
      StringBuilder tmpl = new StringBuilder();
      for (int m = 0; m < 5; m++) {
        tmpl.append(
            String.format(
                "#macro(scaleMacro_%02d_%02d $arg)\n  <div>$arg</div>\n#end\n", docIdx, m));
      }
      String docUri = "file:///workspace/template_" + docIdx + ".vtl";
      symbolIndex.indexTemplateMacros(new TemplateDocument(docUri, 1, tmpl.toString()));
    }
  }

  @Test
  @DisplayName("Scale index must hold >= 6100 symbols (1,000 types, 5,000 properties, 100 macros)")
  void testScaleIndexCapacity() {
    int count = symbolIndex.symbolCount();
    assertTrue(
        count >= 6100,
        "Scale index should have at least 6100 symbols (1000 types + 5000 properties + 100 macros),"
            + " found: "
            + count);
  }

  @Test
  @DisplayName("Search execution latency across 6,100+ symbols must remain strictly under 50ms")
  void testSearchLatencyUnder50ms() {
    // Warmup query to ensure JIT warmup
    symbolIndex.search("Type0001");

    // 1. Exact match search
    long startExact = System.nanoTime();
    List<SymbolInformation> exactRes = symbolIndex.search("Type0500");
    long elapsedExactMs = (System.nanoTime() - startExact) / 1_000_000L;
    assertTrue(
        elapsedExactMs < 50, "Exact search latency was " + elapsedExactMs + "ms, expected < 50ms");
    assertFalse(exactRes.isEmpty());
    assertEquals("Type0500", exactRes.get(0).name());

    // 2. Prefix search matching a subset of types
    long startPrefix = System.nanoTime();
    List<SymbolInformation> prefixRes = symbolIndex.search("Type005");
    long elapsedPrefixMs = (System.nanoTime() - startPrefix) / 1_000_000L;
    assertTrue(
        elapsedPrefixMs < 50,
        "Prefix search latency was " + elapsedPrefixMs + "ms, expected < 50ms");
    assertFalse(prefixRes.isEmpty());

    // 3. High-match query hitting the 500 limit cap (e.g. "prop0" matches 1,000 types)
    long startCap = System.nanoTime();
    List<SymbolInformation> capRes = symbolIndex.search("prop0");
    long elapsedCapMs = (System.nanoTime() - startCap) / 1_000_000L;
    assertTrue(
        elapsedCapMs < 50, "High-match search latency was " + elapsedCapMs + "ms, expected < 50ms");
    assertEquals(500, capRes.size(), "Result size must be strictly capped at 500");

    // 4. Macro search
    long startMacro = System.nanoTime();
    List<SymbolInformation> macroRes = symbolIndex.search("scaleMacro_12_03");
    long elapsedMacroMs = (System.nanoTime() - startMacro) / 1_000_000L;
    assertTrue(
        elapsedMacroMs < 50, "Macro search latency was " + elapsedMacroMs + "ms, expected < 50ms");
    assertEquals(1, macroRes.size());
    assertEquals("scaleMacro_12_03", macroRes.get(0).name());
    assertEquals(SymbolKind.FUNCTION, macroRes.get(0).kind());

    // 5. Non-matching query
    long startMiss = System.nanoTime();
    List<SymbolInformation> missRes = symbolIndex.search("NonExistentSymbolQuery");
    long elapsedMissMs = (System.nanoTime() - startMiss) / 1_000_000L;
    assertTrue(
        elapsedMissMs < 50,
        "Non-matching search latency was " + elapsedMissMs + "ms, expected < 50ms");
    assertTrue(missRes.isEmpty());
  }

  @Test
  @DisplayName("Multiple consecutive queries must reliably perform in sub-50ms")
  void testConsecutiveSearchLatency() {
    // Warmup
    symbolIndex.search("Type");

    String[] queries = {
      "Type0100",
      "Type0200",
      "Type0300",
      "prop1",
      "prop2",
      "scaleMacro_05_02",
      "scaleMacro_18_04",
      "prop4",
      "Type0999",
      "Type0000"
    };

    for (String query : queries) {
      long start = System.nanoTime();
      List<SymbolInformation> res = symbolIndex.search(query);
      long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
      assertTrue(elapsedMs < 50, "Query '" + query + "' took " + elapsedMs + "ms, expected < 50ms");
      assertFalse(res.isEmpty(), "Query '" + query + "' should return results");
    }
  }
}
