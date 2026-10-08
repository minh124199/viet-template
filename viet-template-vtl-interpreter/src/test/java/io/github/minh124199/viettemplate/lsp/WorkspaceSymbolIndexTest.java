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

class WorkspaceSymbolIndexTest {

  @TempDir Path tempDir;

  private CanonicalSchemaResolver schemaResolver;
  private WorkspaceSchemaIndex schemaIndex;
  private WorkspaceSymbolIndex symbolIndex;

  @BeforeEach
  void setUp() {
    schemaResolver = new CanonicalSchemaResolver();
    schemaIndex = new WorkspaceSchemaIndex(schemaResolver);
    symbolIndex =
        new WorkspaceSymbolIndex(schemaResolver, schemaIndex, MemberAccessPolicy.standard());
  }

  @Test
  @DisplayName(
      "Should index canonical schema types, properties, and root parameters with exact locations")
  void testIndexSchemaTypesAndProperties() throws IOException {
    Path schemaFile = tempDir.resolve("test.vt-schema.json");
    String schemaJson =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "test",
          "parameters": {
            "account": {
              "name": "account",
              "type": { "kind": "named", "name": "Account" }
            }
          },
          "types": {
            "Account": {
              "name": "Account",
              "properties": {
                "email": {
                  "name": "email",
                  "type": { "kind": "class", "className": "java.lang.String" }
                }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaJson, StandardCharsets.UTF_8);

    schemaResolver.registerSchemaFile(schemaFile);
    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(schemaFile.toUri().toString());
    assertTrue(schema.isPresent());
    symbolIndex.indexSchema(schemaFile.toUri().toString(), schema.get(), Optional.of(schemaFile));

    // 1. Search for type Account
    List<SymbolInformation> accountResults = symbolIndex.search("Account");
    assertFalse(accountResults.isEmpty());
    SymbolInformation accountSym = accountResults.get(0);
    assertEquals("Account", accountSym.name());
    assertEquals(SymbolKind.CLASS, accountSym.kind());
    assertTrue(accountSym.location().uri().contains("test.vt-schema.json"));

    // 2. Search for property email
    List<SymbolInformation> emailResults = symbolIndex.search("email");
    assertFalse(emailResults.isEmpty());
    SymbolInformation emailSym = emailResults.get(0);
    assertEquals("email", emailSym.name());
    assertEquals(SymbolKind.PROPERTY, emailSym.kind());
    assertEquals("Account", emailSym.containerName());

    // 3. Search for root parameter account
    List<SymbolInformation> paramResults = symbolIndex.search("account");
    assertFalse(paramResults.isEmpty());
    SymbolInformation paramSym = paramResults.get(0);
    assertEquals("account", paramSym.name());
    assertEquals(SymbolKind.VARIABLE, paramSym.kind());
  }

  @Test
  @DisplayName("Should index template macros with exact identifier range")
  void testIndexTemplateMacros() {
    String vtl =
        """
        #macro(renderHeader $title)
          <h1>$title</h1>
        #end
        #macro(renderFooter $year)
          <footer>$year</footer>
        #end
        """;
    TemplateDocument doc = new TemplateDocument("file:///workspace/header.vtl", 1, vtl);
    symbolIndex.indexTemplateMacros(doc);

    List<SymbolInformation> headerResults = symbolIndex.search("renderHeader");
    assertEquals(1, headerResults.size());
    SymbolInformation headerSym = headerResults.get(0);
    assertEquals("renderHeader", headerSym.name());
    assertEquals(SymbolKind.FUNCTION, headerSym.kind());
    assertEquals("file:///workspace/header.vtl", headerSym.location().uri());
    assertEquals("header.vtl", headerSym.containerName());

    // Exact identifier range: line 0, "renderHeader"
    Range r = headerSym.location().range();
    assertEquals(0, r.start().line());
    assertEquals(7, r.start().character());
    assertEquals(0, r.end().line());
    assertEquals(19, r.end().character());

    // Search for footer
    List<SymbolInformation> footerResults = symbolIndex.search("renderFooter");
    assertEquals(1, footerResults.size());
    assertEquals("renderFooter", footerResults.get(0).name());
  }

  @Test
  @DisplayName("Should strictly exclude template-local variables by default")
  void testExcludeTemplateLocalVariables() {
    String vtl =
        """
        #set($localVar = 42)
        #foreach($loopItem in $items)
          $loopItem
        #end
        #macro(myMacro)
          #set($innerLocal = 100)
        #end
        """;
    TemplateDocument doc = new TemplateDocument("file:///workspace/locals.vtl", 1, vtl);
    symbolIndex.indexTemplateMacros(doc);

    assertTrue(
        symbolIndex.search("localVar").isEmpty(), "Template-local #set variable must be excluded");
    assertTrue(
        symbolIndex.search("loopItem").isEmpty(), "Template-local loop variable must be excluded");
    assertTrue(symbolIndex.search("innerLocal").isEmpty(), "Inner local variable must be excluded");
    // But macro itself should be indexed
    assertFalse(symbolIndex.search("myMacro").isEmpty());
  }

  @Test
  @DisplayName("Should respect MemberAccessPolicy and exclude denied and dynamic members")
  void testRespectMemberAccessPolicy() throws IOException {
    Path schemaFile = tempDir.resolve("policy.vt-schema.json");
    String schemaJson =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "policy",
          "types": {
            "SecureModel": {
              "name": "SecureModel",
              "properties": {
                "publicData": {
                  "name": "publicData",
                  "type": { "kind": "class", "className": "java.lang.String" }
                },
                "class": {
                  "name": "class",
                  "type": { "kind": "class", "className": "java.lang.Class" }
                },
                "password": {
                  "name": "password",
                  "type": { "kind": "class", "className": "java.lang.String" }
                }
              }
            }
          }
        }
        """;
    Files.writeString(schemaFile, schemaJson, StandardCharsets.UTF_8);

    MemberAccessPolicy customPolicy =
        MemberAccessPolicy.builder().denyMethod("getPassword").denyClass(Class.class).build();
    symbolIndex.setMemberAccessPolicy(customPolicy);

    schemaResolver.registerSchemaFile(schemaFile);
    Optional<CanonicalSchema> schema = schemaResolver.resolveSchema(schemaFile.toUri().toString());
    symbolIndex.indexSchema(schemaFile.toUri().toString(), schema.get(), Optional.of(schemaFile));

    assertFalse(symbolIndex.search("publicData").isEmpty(), "publicData should be permitted");
    assertTrue(
        symbolIndex.search("class").isEmpty(), "Sensitive 'class' property must be excluded");
  }

  @Test
  @DisplayName("Should deduplicate identical JVM types referenced across multiple schemas")
  void testDeduplicateIdenticalJvmTypes() throws IOException {
    Path srcRoot = tempDir.resolve("src/main/java");
    Files.createDirectories(srcRoot.resolve("io/github/minh124199/viettemplate/lsp/models"));
    Path javaFile =
        srcRoot.resolve("io/github/minh124199/viettemplate/lsp/models/NavUserRecord.java");
    Files.writeString(
        javaFile,
        "package io.github.minh124199.viettemplate.lsp.models;\n"
            + "public record NavUserRecord(String name, int age) {}\n",
        StandardCharsets.UTF_8);
    schemaIndex.javaSourceLocator().addSourceRoot(srcRoot);

    String schemaAJson =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "schemaA",
          "types": {
            "io.github.minh124199.viettemplate.lsp.models.NavUserRecord": {
              "name": "io.github.minh124199.viettemplate.lsp.models.NavUserRecord",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """;
    String schemaBJson =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "schemaB",
          "types": {
            "io.github.minh124199.viettemplate.lsp.models.NavUserRecord": {
              "name": "io.github.minh124199.viettemplate.lsp.models.NavUserRecord",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """;

    Path schemaA = tempDir.resolve("schemaA.vt-schema.json");
    Path schemaB = tempDir.resolve("schemaB.vt-schema.json");
    Files.writeString(schemaA, schemaAJson, StandardCharsets.UTF_8);
    Files.writeString(schemaB, schemaBJson, StandardCharsets.UTF_8);

    schemaResolver.registerSchemaFile(schemaA);
    schemaResolver.registerSchemaFile(schemaB);

    symbolIndex.indexSchema(
        schemaA.toUri().toString(),
        schemaResolver.resolveSchema(schemaA.toUri().toString()).get(),
        Optional.of(schemaA));
    symbolIndex.indexSchema(
        schemaB.toUri().toString(),
        schemaResolver.resolveSchema(schemaB.toUri().toString()).get(),
        Optional.of(schemaB));

    // NavUserRecord should be deduplicated to exactly 1 result!
    List<SymbolInformation> users = symbolIndex.search("NavUserRecord");
    assertEquals(
        1,
        users.size(),
        "Identical JVM type referenced in multiple schemas must be deduplicated to 1 result");

    // Remove schemaA -> NavUserRecord still remains because schemaB still references it
    symbolIndex.removeSchema(schemaA.toUri().toString());
    List<SymbolInformation> usersAfterA = symbolIndex.search("NavUserRecord");
    assertEquals(
        1,
        usersAfterA.size(),
        "NavUserRecord should still be present because schemaB references it");

    // Remove schemaB -> NavUserRecord is now cleanly evicted
    symbolIndex.removeSchema(schemaB.toUri().toString());
    List<SymbolInformation> usersAfterB = symbolIndex.search("NavUserRecord");
    assertTrue(
        usersAfterB.isEmpty(),
        "NavUserRecord should be removed after all referencing schemas are removed");
  }

  @Test
  @DisplayName("Should not merge distinct types with identical simple name")
  void testDoNotMergeDistinctTypes() throws IOException {
    Path schemaA = tempDir.resolve("schemaA.vt-schema.json");
    Path schemaB = tempDir.resolve("schemaB.vt-schema.json");

    String schemaAJson =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "schemaA",
          "types": {
            "Customer": {
              "name": "Customer",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """;
    String schemaBJson =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "schemaB",
          "types": {
            "Product": {
              "name": "Product",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """;

    Files.writeString(schemaA, schemaAJson, StandardCharsets.UTF_8);
    Files.writeString(schemaB, schemaBJson, StandardCharsets.UTF_8);

    schemaResolver.registerSchemaFile(schemaA);
    schemaResolver.registerSchemaFile(schemaB);

    symbolIndex.indexSchema(
        schemaA.toUri().toString(),
        schemaResolver.resolveSchema(schemaA.toUri().toString()).get(),
        Optional.of(schemaA));
    symbolIndex.indexSchema(
        schemaB.toUri().toString(),
        schemaResolver.resolveSchema(schemaB.toUri().toString()).get(),
        Optional.of(schemaB));

    // Both Customer.name and Product.name exist as distinct properties
    List<SymbolInformation> nameResults = symbolIndex.search("name");
    assertEquals(2, nameResults.size(), "Customer.name and Product.name must not be merged");
    assertTrue(nameResults.stream().anyMatch(s -> "Customer".equals(s.containerName())));
    assertTrue(nameResults.stream().anyMatch(s -> "Product".equals(s.containerName())));
  }

  @Test
  @DisplayName("Should return empty list for null, empty, or blank query")
  void testEmptyQueryReturnsEmptyList() {
    String vtl = "#macro(testMacro) hello #end";
    symbolIndex.indexTemplateMacros(new TemplateDocument("file:///test.vtl", 1, vtl));

    assertTrue(symbolIndex.search(null).isEmpty());
    assertTrue(symbolIndex.search("").isEmpty());
    assertTrue(symbolIndex.search("   ").isEmpty());
  }

  @Test
  @DisplayName("Should cap search results to maximum limit of 500")
  void testResultCap() throws IOException {
    Path schemaFile = tempDir.resolve("large.vt-schema.json");
    StringBuilder sb = new StringBuilder();
    sb.append(
        "{\n"
            + "\"format\":\"viet-template-contract-schema/1\",\n"
            + "\"schemaVersion\":1,\n"
            + "\"templateId\":\"large\",\n"
            + "\"types\":{\n"
            + "\"LargeType\":{\n"
            + "\"name\":\"LargeType\",\n"
            + "\"properties\":{\n");
    for (int i = 0; i < 600; i++) {
      if (i > 0) sb.append(",\n");
      sb.append(
          String.format(
              "\"field%03d\":{\"name\":\"field%03d\",\"type\":{\"kind\":\"class\",\"className\":\"java.lang.String\"}}",
              i, i));
    }
    sb.append("\n}\n}\n}\n}\n");

    Files.writeString(schemaFile, sb.toString(), StandardCharsets.UTF_8);
    schemaResolver.registerSchemaFile(schemaFile);
    symbolIndex.indexSchema(
        schemaFile.toUri().toString(),
        schemaResolver.resolveSchema(schemaFile.toUri().toString()).get(),
        Optional.of(schemaFile));

    List<SymbolInformation> results = symbolIndex.search("field");
    assertEquals(500, results.size(), "Search results must be capped at 500");
  }

  @Test
  @DisplayName("Should support atomic template removal")
  void testAtomicTemplateRemoval() {
    String vtl = "#macro(tempMacro) body #end";
    TemplateDocument doc = new TemplateDocument("file:///temp.vtl", 1, vtl);
    symbolIndex.indexTemplateMacros(doc);

    assertFalse(symbolIndex.search("tempMacro").isEmpty());

    symbolIndex.removeTemplate("file:///temp.vtl");
    assertTrue(symbolIndex.search("tempMacro").isEmpty());
  }
}
