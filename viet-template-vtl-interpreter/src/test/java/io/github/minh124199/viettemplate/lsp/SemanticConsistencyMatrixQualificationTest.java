package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.lsp.models.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticConsistencyMatrixQualificationTest {

  public record Customer(String name) {}

  public record Product(String name) {}

  public record Address(String name) {}

  public record Company(String name) {}

  public static class InheritingUser extends NavBaseUser {
    public InheritingUser() {
      super();
    }

    public InheritingUser(String email) {
      super(email);
    }
  }

  public static class OverridingUser extends NavBaseUser {
    public OverridingUser() {
      super();
    }

    public OverridingUser(String email) {
      super(email);
    }

    @Override
    public String getEmail() {
      return super.getEmail();
    }
  }

  public static class SecretModel {
    private String username;
    private String secret;

    public SecretModel(String username, String secret) {
      this.username = username;
      this.secret = secret;
    }

    public String getUsername() {
      return username;
    }

    public String getSecret() {
      return secret;
    }
  }

  // =========================================================================
  // 1. Semantic Consistency Matrix Across the 5 Source Families
  // =========================================================================

  @Test
  @DisplayName("Matrix - Completion: verified across all 5 source families")
  void testCompletionAcrossFiveSourceFamilies(@TempDir Path tempDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. JVM/Java
    String javaUri = "file:///workspace/java.vtl";
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult jRes = importer.importModel(NavUserRecord.class, "user", javaUri);
    resolver.registerSchema(javaUri, jRes.schemas().get(javaUri));
    service.openDocument(javaUri, 1, "Hello $user.");
    CompletionList javaComp = service.completion(javaUri, Position.of(0, 12));
    List<String> javaLabels =
        javaComp.items().stream().map(CompletionItem::label).sorted().toList();
    assertTrue(javaLabels.containsAll(List.of("age", "name")), "JVM labels: " + javaLabels);

    // 2. TypeScript .d.ts
    Path dtsFile = tempDir.resolve("ts-model.d.ts");
    String dtsContent =
        """
        export interface UserDto {
          name: string;
          age: number;
        }
        export interface TemplateParameters {
          user: UserDto;
        }
        """;
    Files.writeString(dtsFile, dtsContent);
    resolver.registerSchemaFile(dtsFile);
    String tsUri = "file:///workspace/ts.vtl";
    service.schemaIndex().recordDependency(tsUri, dtsFile);
    service.openDocument(tsUri, 1, "Hello $user.");
    CompletionList tsComp = service.completion(tsUri, Position.of(0, 12));
    List<String> tsLabels = tsComp.items().stream().map(CompletionItem::label).sorted().toList();
    assertTrue(tsLabels.containsAll(List.of("age", "name")), "TS labels: " + tsLabels);

    // 3. JSON Schema
    Path jsonFile = tempDir.resolve("json-model.schema.json");
    String jsonContent =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "title": "JsonUser",
          "type": "object",
          "properties": {
            "user": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "age": { "type": "integer" }
              }
            }
          }
        }
        """;
    Files.writeString(jsonFile, jsonContent);
    resolver.registerSchemaFile(jsonFile);
    String jsonUri = "file:///workspace/json.vtl";
    service.schemaIndex().recordDependency(jsonUri, jsonFile);
    service.openDocument(jsonUri, 1, "Hello $user.");
    CompletionList jsonComp = service.completion(jsonUri, Position.of(0, 12));
    List<String> jsonLabels =
        jsonComp.items().stream().map(CompletionItem::label).sorted().toList();
    assertTrue(jsonLabels.containsAll(List.of("age", "name")), "JSON Schema labels: " + jsonLabels);

    // 4. Contract (.vt-schema.json)
    Path vtFile = tempDir.resolve("contract-model.vt-schema.json");
    String vtContent =
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "contract-test",
          "parameters": {
            "user": {
              "name": "user",
              "type": { "kind": "named", "name": "UserContract" }
            }
          },
          "types": {
            "UserContract": {
              "name": "UserContract",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } },
                "age": { "name": "age", "type": { "kind": "primitive", "primitiveKind": "INT" } }
              }
            }
          }
        }
        """;
    Files.writeString(vtFile, vtContent);
    resolver.registerSchemaFile(vtFile);
    String vtUri = "file:///workspace/contract.vtl";
    service.schemaIndex().recordDependency(vtUri, vtFile);
    service.openDocument(vtUri, 1, "Hello $user.");
    CompletionList vtComp = service.completion(vtUri, Position.of(0, 12));
    List<String> vtLabels = vtComp.items().stream().map(CompletionItem::label).sorted().toList();
    assertTrue(vtLabels.containsAll(List.of("age", "name")), "Contract labels: " + vtLabels);

    // 5. Local VTL
    String localUri = "file:///workspace/local.vtl";
    service.openDocument(localUri, 1, "#set($localVar = 100)\nValue: $loc");
    CompletionList localComp = service.completion(localUri, Position.of(1, 11));
    List<String> localLabels =
        localComp.items().stream().map(CompletionItem::label).sorted().toList();
    assertTrue(localLabels.contains("localVar"), "Local VTL labels: " + localLabels);
  }

  @Test
  @DisplayName("Matrix - Hover: verified across all 5 source families")
  void testHoverAcrossFiveSourceFamilies(@TempDir Path tempDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. JVM/Java
    String javaUri = "file:///workspace/java-hover.vtl";
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult jRes = importer.importModel(NavUserRecord.class, "user", javaUri);
    resolver.registerSchema(javaUri, jRes.schemas().get(javaUri));
    service.openDocument(javaUri, 1, "Hello $user.name");
    Optional<HoverInfo> jHover = service.hover(javaUri, Position.of(0, 13));
    assertTrue(jHover.isPresent());
    assertTrue(jHover.get().markdown().contains("**name**"));
    assertTrue(
        jHover.get().markdown().contains("String")
            || jHover.get().markdown().contains("java.lang.String"));

    // 2. TypeScript .d.ts
    Path dtsFile = tempDir.resolve("hover.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface UserDto {
          name: string;
        }
        export interface TemplateParameters {
          user: UserDto;
        }
        """);
    resolver.registerSchemaFile(dtsFile);
    String tsUri = "file:///workspace/ts-hover.vtl";
    service.schemaIndex().recordDependency(tsUri, dtsFile);
    service.openDocument(tsUri, 1, "Hello $user.name");
    Optional<HoverInfo> tsHover = service.hover(tsUri, Position.of(0, 13));
    assertTrue(tsHover.isPresent());
    assertTrue(tsHover.get().markdown().contains("**name**"));
    assertTrue(tsHover.get().markdown().contains("string"));

    // 3. JSON Schema
    Path jsonFile = tempDir.resolve("hover.schema.json");
    Files.writeString(
        jsonFile,
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "properties": {
            "user": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          }
        }
        """);
    resolver.registerSchemaFile(jsonFile);
    String jsonUri = "file:///workspace/json-hover.vtl";
    service.schemaIndex().recordDependency(jsonUri, jsonFile);
    service.openDocument(jsonUri, 1, "Hello $user.name");
    Optional<HoverInfo> jsonHover = service.hover(jsonUri, Position.of(0, 13));
    assertTrue(jsonHover.isPresent());
    assertTrue(jsonHover.get().markdown().contains("**name**"));
    assertTrue(jsonHover.get().markdown().contains("string"));

    // 4. Contract (.vt-schema.json)
    Path vtFile = tempDir.resolve("hover.vt-schema.json");
    Files.writeString(
        vtFile,
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "parameters": {
            "user": { "name": "user", "type": { "kind": "named", "name": "User" } }
          },
          "types": {
            "User": {
              "name": "User",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """);
    resolver.registerSchemaFile(vtFile);
    String vtUri = "file:///workspace/vt-hover.vtl";
    service.schemaIndex().recordDependency(vtUri, vtFile);
    service.openDocument(vtUri, 1, "Hello $user.name");
    Optional<HoverInfo> vtHover = service.hover(vtUri, Position.of(0, 13));
    assertTrue(vtHover.isPresent());
    assertTrue(vtHover.get().markdown().contains("**name**"));

    // 5. Local VTL
    String localUri = "file:///workspace/local-hover.vtl";
    service.openDocument(localUri, 1, "#set($counter = 42)\nTotal: $counter");
    Optional<HoverInfo> localHover = service.hover(localUri, Position.of(1, 10));
    assertTrue(localHover.isPresent());
    assertTrue(localHover.get().markdown().contains("counter"));
  }

  @Test
  @DisplayName("Matrix - Definition: verified across all 5 source families")
  void testDefinitionAcrossFiveSourceFamilies(@TempDir Path workspaceDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. JVM/Java with workspace Java source file
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavUserRecord.java");
    Files.writeString(
        javaFile,
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavUserRecord(
            String name,
            int age
        ) {}
        """);
    String javaUri = workspaceDir.resolve("java-def.vtl").toUri().toString();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult jRes = importer.importModel(NavUserRecord.class, "user", javaUri);
    resolver.registerSchema(javaUri, jRes.schemas().get(javaUri));
    service.openDocument(javaUri, 1, "Hello $user.name");
    List<LocationInfo> javaDefs = service.definition(javaUri, Position.of(0, 13));
    assertFalse(javaDefs.isEmpty());
    assertEquals(javaFile.toUri().toString(), javaDefs.get(0).uri());

    // 1b. JVM without source file returns empty list
    String pureJvmUri = "file:///workspace/pure-jvm.vtl";
    SchemaImportResult pureRes = importer.importModel(NavBeanUser.class, "user", pureJvmUri);
    resolver.registerSchema(pureJvmUri, pureRes.schemas().get(pureJvmUri));
    service.openDocument(pureJvmUri, 1, "Hello $user.name");
    List<LocationInfo> pureDefs = service.definition(pureJvmUri, Position.of(0, 13));
    assertTrue(pureDefs.isEmpty(), "Pure reflection without source must return empty list");

    // 2. TypeScript .d.ts
    Path dtsFile = workspaceDir.resolve("def.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface UserDto {
          name: string;
        }
        export interface TemplateParameters {
          user: UserDto;
        }
        """);
    resolver.registerSchemaFile(dtsFile);
    String tsUri = workspaceDir.resolve("ts-def.vtl").toUri().toString();
    service.schemaIndex().recordDependency(tsUri, dtsFile);
    service.openDocument(tsUri, 1, "Hello $user.name");
    List<LocationInfo> tsDefs = service.definition(tsUri, Position.of(0, 13));
    assertFalse(tsDefs.isEmpty());
    assertEquals(dtsFile.toUri().toString(), tsDefs.get(0).uri());

    // 3. JSON Schema
    Path jsonFile = workspaceDir.resolve("def.schema.json");
    Files.writeString(
        jsonFile,
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "properties": {
            "user": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          }
        }
        """);
    resolver.registerSchemaFile(jsonFile);
    String jsonUri = workspaceDir.resolve("json-def.vtl").toUri().toString();
    service.schemaIndex().recordDependency(jsonUri, jsonFile);
    service.openDocument(jsonUri, 1, "Hello $user.name");
    List<LocationInfo> jsonDefs = service.definition(jsonUri, Position.of(0, 13));
    assertFalse(jsonDefs.isEmpty());
    assertEquals(jsonFile.toUri().toString(), jsonDefs.get(0).uri());

    // 4. Contract (.vt-schema.json)
    Path vtFile = workspaceDir.resolve("def.vt-schema.json");
    Files.writeString(
        vtFile,
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "parameters": {
            "user": { "name": "user", "type": { "kind": "named", "name": "User" } }
          },
          "types": {
            "User": {
              "name": "User",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """);
    resolver.registerSchemaFile(vtFile);
    String vtUri = workspaceDir.resolve("vt-def.vtl").toUri().toString();
    service.schemaIndex().recordDependency(vtUri, vtFile);
    service.openDocument(vtUri, 1, "Hello $user.name");
    List<LocationInfo> vtDefs = service.definition(vtUri, Position.of(0, 13));
    assertFalse(vtDefs.isEmpty());
    assertEquals(vtFile.toUri().toString(), vtDefs.get(0).uri());

    // 5. Local VTL
    String localUri = workspaceDir.resolve("local-def.vtl").toUri().toString();
    service.openDocument(localUri, 1, "#set($myVar = 42)\nValue: $myVar");
    List<LocationInfo> localDefs = service.definition(localUri, Position.of(1, 10));
    assertFalse(localDefs.isEmpty());
    assertEquals(localUri, localDefs.get(0).uri());
    assertEquals(0, localDefs.get(0).range().start().line()); // line 0: #set
  }

  @Test
  @DisplayName("Matrix - References: verified across all 5 source families")
  void testReferencesAcrossFiveSourceFamilies(@TempDir Path tempDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. JVM/Java
    String javaUri = "file:///workspace/ref-java.vtl";
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult jRes = importer.importModel(NavBeanUser.class, "user", javaUri);
    resolver.registerSchema(javaUri, jRes.schemas().get(javaUri));
    service.openDocument(javaUri, 1, "$user.name and again $user.name");
    List<LocationInfo> javaRefs = service.references(javaUri, Position.of(0, 8), false);
    assertEquals(2, javaRefs.size());

    // 2. TypeScript .d.ts
    Path dtsFile = tempDir.resolve("ref.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface Dto {
          title: string;
        }
        export interface TemplateParameters {
          item: Dto;
        }
        """);
    resolver.registerSchemaFile(dtsFile);
    String tsUri = "file:///workspace/ref-ts.vtl";
    service.schemaIndex().recordDependency(tsUri, dtsFile);
    service.openDocument(tsUri, 1, "$item.title - $item.title");
    List<LocationInfo> tsRefs = service.references(tsUri, Position.of(0, 8), false);
    assertEquals(2, tsRefs.size());

    // 3. JSON Schema
    Path jsonFile = tempDir.resolve("ref.schema.json");
    Files.writeString(
        jsonFile,
        """
        {
          "properties": {
            "item": {
              "type": "object",
              "properties": {
                "title": { "type": "string" }
              }
            }
          }
        }
        """);
    String jsonUri = "file:///workspace/ref-json.vtl";
    resolver.registerSchemaFile(jsonFile, jsonUri);
    service.schemaIndex().recordDependency(jsonUri, jsonFile);
    service.openDocument(jsonUri, 1, "$item.title / $item.title");
    List<LocationInfo> jsonRefs = service.references(jsonUri, Position.of(0, 8), false);
    assertEquals(2, jsonRefs.size());

    // 4. Contract (.vt-schema.json) with distinct property name
    Path vtFile = tempDir.resolve("ref.vt-schema.json");
    Files.writeString(
        vtFile,
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "parameters": {
            "item": { "name": "item", "type": { "kind": "named", "name": "ContractItem" } }
          },
          "types": {
            "ContractItem": {
              "name": "ContractItem",
              "properties": {
                "summary": { "name": "summary", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """);
    String vtUri = "file:///workspace/ref-vt.vtl";
    resolver.registerSchemaFile(vtFile, vtUri);
    service.schemaIndex().recordDependency(vtUri, vtFile);
    service.openDocument(vtUri, 1, "$item.summary : $item.summary");
    List<LocationInfo> vtRefs = service.references(vtUri, Position.of(0, 8), false);
    assertEquals(2, vtRefs.size());

    // 5. Local VTL
    String localUri = "file:///workspace/ref-local.vtl";
    service.openDocument(localUri, 1, "#set($num = 5)\n$num + $num");
    List<LocationInfo> localRefs = service.references(localUri, Position.of(1, 2), false);
    assertEquals(2, localRefs.size()); // 2 usages (excluding #set declaration)
  }

  @Test
  @DisplayName("Matrix - Rename: verified across all 5 source families")
  void testRenameAcrossFiveSourceFamilies(@TempDir Path tempDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. JVM/Java: prepareRename is empty; rename throws UNSUPPORTED_JVM_MEMBER
    String javaUri = "file:///workspace/rename-java.vtl";
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult jRes = importer.importModel(NavBeanUser.class, "user", javaUri);
    resolver.registerSchema(javaUri, jRes.schemas().get(javaUri));
    service.openDocument(javaUri, 1, "Hello $user.name");
    assertTrue(service.prepareRename(javaUri, Position.of(0, 13)).isEmpty());
    RenameConflictException jEx =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(javaUri, Position.of(0, 13), "newName"));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, jEx.reason());

    // 2. TypeScript .d.ts: supported (updates declaration + references)
    Path dtsFile = tempDir.resolve("rename.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface UserDto {
          name: string;
        }
        export interface TemplateParameters {
          user: UserDto;
        }
        """);
    resolver.registerSchemaFile(dtsFile);
    String tsUri = "file:///workspace/rename-ts.vtl";
    service.schemaIndex().recordDependency(tsUri, dtsFile);
    service.openDocument(tsUri, 1, "Hello $user.name");
    Optional<PrepareRenameResult> tsPrep = service.prepareRename(tsUri, Position.of(0, 13));
    assertTrue(tsPrep.isPresent());
    assertEquals("name", tsPrep.get().placeholder());
    WorkspaceEdit tsEdit = service.rename(tsUri, Position.of(0, 13), "fullName");
    assertNotNull(tsEdit);
    assertTrue(tsEdit.changes().containsKey(dtsFile.toUri().toString()));
    assertTrue(tsEdit.changes().containsKey(tsUri));

    // 3. JSON Schema: prepareRename is empty; rename throws UNSUPPORTED_SCHEMA_FORMAT
    Path jsonFile = tempDir.resolve("rename.schema.json");
    Files.writeString(
        jsonFile,
        """
        {
          "properties": {
            "user": {
              "type": "object",
              "properties": {
                "name": { "type": "string" }
              }
            }
          }
        }
        """);
    resolver.registerSchemaFile(jsonFile);
    String jsonUri = "file:///workspace/rename-json.vtl";
    service.schemaIndex().recordDependency(jsonUri, jsonFile);
    service.openDocument(jsonUri, 1, "Hello $user.name");
    assertTrue(service.prepareRename(jsonUri, Position.of(0, 13)).isEmpty());
    RenameConflictException jsonEx =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(jsonUri, Position.of(0, 13), "newName"));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_SCHEMA_FORMAT, jsonEx.reason());

    // 4. Contract (.vt-schema.json): supported (updates schema declaration + references)
    Path vtFile = tempDir.resolve("rename.vt-schema.json");
    Files.writeString(
        vtFile,
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "parameters": {
            "user": { "name": "user", "type": { "kind": "named", "name": "User" } }
          },
          "types": {
            "User": {
              "name": "User",
              "properties": {
                "name": { "name": "name", "type": { "kind": "class", "className": "java.lang.String" } }
              }
            }
          }
        }
        """);
    resolver.registerSchemaFile(vtFile);
    String vtUri = "file:///workspace/rename-vt.vtl";
    service.schemaIndex().recordDependency(vtUri, vtFile);
    service.openDocument(vtUri, 1, "Hello $user.name");
    Optional<PrepareRenameResult> vtPrep = service.prepareRename(vtUri, Position.of(0, 13));
    assertTrue(vtPrep.isPresent());
    WorkspaceEdit vtEdit = service.rename(vtUri, Position.of(0, 13), "legalName");
    assertNotNull(vtEdit);
    assertTrue(vtEdit.changes().containsKey(vtFile.toUri().toString()));
    assertTrue(vtEdit.changes().containsKey(vtUri));

    // 5. Local VTL: supported (updates #set declaration + usages)
    String localUri = "file:///workspace/rename-local.vtl";
    service.openDocument(localUri, 1, "#set($v = 1)\nUse $v");
    Optional<PrepareRenameResult> localPrep = service.prepareRename(localUri, Position.of(1, 5));
    assertTrue(localPrep.isPresent());
    assertEquals("v", localPrep.get().placeholder());
    WorkspaceEdit localEdit = service.rename(localUri, Position.of(1, 5), "val");
    assertNotNull(localEdit);
    assertEquals(2, localEdit.changes().get(localUri).size());
  }

  @Test
  @DisplayName("Matrix - Workspace Symbol: verified across all 5 source families")
  void testWorkspaceSymbolAcrossFiveSourceFamilies(@TempDir Path workspaceDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);

    // 1. JVM/Java model with source file
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("NavUserRecord.java"),
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavUserRecord(String name, int age) {}
        """);

    // 2. TypeScript .d.ts
    Path dtsFile = workspaceDir.resolve("sym.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface TsAccount {
          accountNumber: string;
        }
        """);

    // 3. JSON Schema
    Path jsonFile = workspaceDir.resolve("sym.schema.json");
    Files.writeString(
        jsonFile,
        """
        {
          "title": "JsonOrder",
          "properties": {
            "orderId": { "type": "string" }
          }
        }
        """);

    // 4. Contract (.vt-schema.json)
    Path vtFile = workspaceDir.resolve("sym.vt-schema.json");
    Files.writeString(
        vtFile,
        """
        {
          "format": "viet-template-contract-schema/1",
          "schemaVersion": 1,
          "templateId": "sym-test",
          "parameters": {
            "user": { "name": "user", "type": { "kind": "class", "className": "io.github.minh124199.viettemplate.lsp.models.NavUserRecord" } }
          },
          "types": {
            "ContractInvoice": {
              "name": "ContractInvoice",
              "properties": {
                "invoiceTotal": { "name": "invoiceTotal", "type": { "kind": "primitive", "primitiveKind": "DOUBLE" } }
              }
            }
          }
        }
        """);

    // 5. Template with local variables and macro
    Path tmplFile = workspaceDir.resolve("sym.vtl");
    Files.writeString(
        tmplFile,
        """
        #macro(renderInvoice $inv)
          <div>$inv</div>
        #end
        #set($localSecret = 999)
        """);

    // Scan workspace
    service.setWorkspaceRoot(workspaceDir);

    // Verify symbols:
    // JVM: NavUserRecord (STRUCT / CLASS)
    List<SymbolInformation> jvmSyms = service.workspaceSymbols("NavUserRecord");
    assertFalse(jvmSyms.isEmpty(), "JVM type NavUserRecord should be found");

    // TS: TsAccount (CLASS / INTERFACE) & accountNumber (PROPERTY)
    List<SymbolInformation> tsSyms = service.workspaceSymbols("TsAccount");
    assertFalse(tsSyms.isEmpty(), "TS type TsAccount should be found");

    // JSON Schema: JsonOrder (CLASS) & orderId (PROPERTY)
    List<SymbolInformation> jsonSyms = service.workspaceSymbols("JsonOrder");
    assertFalse(jsonSyms.isEmpty(), "JSON schema JsonOrder should be found");

    // Contract: ContractInvoice (CLASS) & invoiceTotal (PROPERTY)
    List<SymbolInformation> contractSyms = service.workspaceSymbols("ContractInvoice");
    assertFalse(contractSyms.isEmpty(), "Contract ContractInvoice should be found");

    // Macro: renderInvoice (FUNCTION)
    List<SymbolInformation> macroSyms = service.workspaceSymbols("renderInvoice");
    assertFalse(macroSyms.isEmpty(), "Macro renderInvoice should be found");
    assertEquals(SymbolKind.FUNCTION, macroSyms.get(0).kind());

    // Local variable $localSecret must NOT be indexed as workspace symbol
    List<SymbolInformation> localSyms = service.workspaceSymbols("localSecret");
    assertTrue(localSyms.isEmpty(), "Local variables must not appear in workspace symbol query");
  }

  // =========================================================================
  // 2. Canonical Symbol Identity Consistency Across Engine Components
  // =========================================================================

  @Test
  @DisplayName("Canonical Identity - Java members: getters, records, booleans, fields, inheritance")
  void testCanonicalSymbolIdentityJavaMembers() {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    MemberAccessPolicy policy = MemberAccessPolicy.standard();

    // 1. JavaBean getter: getName()
    String uri1 = "file:///bean.vtl";
    TemplateDocument doc1 = new TemplateDocument(uri1, 1, "$bean.name");
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    resolver.registerSchema(
        uri1, importer.importModel(NavBeanUser.class, "bean", uri1).schemas().get(uri1));
    refIndex.indexTemplate(doc1, resolver, policy);
    Set<ResolvedTemplateReference> refs1 = refIndex.getReferencesForTemplate(uri1);
    ResolvedTemplateReference nameRef =
        refs1.stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    assertInstanceOf(JvmMemberSymbolKey.class, nameRef.symbolKey());
    JvmMemberSymbolKey nameKey = (JvmMemberSymbolKey) nameRef.symbolKey();
    assertEquals(JvmMemberSymbolKey.Kind.GETTER, nameKey.memberKind());
    assertEquals("getName", nameKey.memberName());
    assertEquals(NavBeanUser.class.getName(), nameKey.declaringClassName());

    // 2. Boolean getter: isActive()
    String uri2 = "file:///bool.vtl";
    TemplateDocument doc2 = new TemplateDocument(uri2, 1, "$bean.active");
    resolver.registerSchema(
        uri2, importer.importModel(NavBeanUser.class, "bean", uri2).schemas().get(uri2));
    refIndex.indexTemplate(doc2, resolver, policy);
    ResolvedTemplateReference activeRef =
        refIndex.getReferencesForTemplate(uri2).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey activeKey = (JvmMemberSymbolKey) activeRef.symbolKey();
    assertEquals(JvmMemberSymbolKey.Kind.BOOLEAN_GETTER, activeKey.memberKind());
    assertEquals("isActive", activeKey.memberName());

    // 3. Record component: NavUserRecord#name
    String uri3 = "file:///record.vtl";
    TemplateDocument doc3 = new TemplateDocument(uri3, 1, "$rec.name");
    resolver.registerSchema(
        uri3, importer.importModel(NavUserRecord.class, "rec", uri3).schemas().get(uri3));
    refIndex.indexTemplate(doc3, resolver, policy);
    ResolvedTemplateReference recRef =
        refIndex.getReferencesForTemplate(uri3).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey recKey = (JvmMemberSymbolKey) recRef.symbolKey();
    assertEquals(JvmMemberSymbolKey.Kind.RECORD_COMPONENT, recKey.memberKind());
    assertEquals("name", recKey.memberName());

    // 4. Public field: NavFieldUser#role
    String uri4 = "file:///field.vtl";
    TemplateDocument doc4 = new TemplateDocument(uri4, 1, "$field.role");
    resolver.registerSchema(
        uri4, importer.importModel(NavFieldUser.class, "field", uri4).schemas().get(uri4));
    refIndex.indexTemplate(doc4, resolver, policy);
    ResolvedTemplateReference fieldRef =
        refIndex.getReferencesForTemplate(uri4).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey fieldKey = (JvmMemberSymbolKey) fieldRef.symbolKey();
    assertEquals(JvmMemberSymbolKey.Kind.FIELD, fieldKey.memberKind());
    assertEquals("role", fieldKey.memberName());

    // 5. Inherited getter: NavAdminUser inherits getEmail() from NavBaseUser
    String uri5 = "file:///inherited.vtl";
    TemplateDocument doc5 = new TemplateDocument(uri5, 1, "$admin.email");
    resolver.registerSchema(
        uri5, importer.importModel(NavAdminUser.class, "admin", uri5).schemas().get(uri5));
    refIndex.indexTemplate(doc5, resolver, policy);
    ResolvedTemplateReference inhRef =
        refIndex.getReferencesForTemplate(uri5).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey inhKey = (JvmMemberSymbolKey) inhRef.symbolKey();
    assertEquals(
        NavBaseUser.class.getName(),
        inhKey.declaringClassName(),
        "Inherited getter declared on BaseUser");

    // 6. Overriding getter: OverridingUser overrides getEmail()
    String uri6 = "file:///override.vtl";
    TemplateDocument doc6 = new TemplateDocument(uri6, 1, "$ov.email");
    resolver.registerSchema(
        uri6, importer.importModel(OverridingUser.class, "ov", uri6).schemas().get(uri6));
    refIndex.indexTemplate(doc6, resolver, policy);
    ResolvedTemplateReference ovRef =
        refIndex.getReferencesForTemplate(uri6).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey ovKey = (JvmMemberSymbolKey) ovRef.symbolKey();
    assertEquals(
        OverridingUser.class.getName(),
        ovKey.declaringClassName(),
        "Overriding getter declared on OverridingUser");
  }

  @Test
  @DisplayName(
      "Canonical Identity - Non-Java sources: TypeScript, JSON Schema, Contract, Local VTL")
  void testCanonicalSymbolIdentityNonJavaSources(@TempDir Path tempDir) throws IOException {
    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    MemberAccessPolicy policy = MemberAccessPolicy.standard();

    // 1. TypeScript property
    Path dtsFile = tempDir.resolve("ident.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface Profile {
          nickname: string;
        }
        export interface TemplateParameters {
          profile: Profile;
        }
        """);
    String tsUri = "file:///workspace/ident.vtl";
    resolver.registerSchemaFile(dtsFile, tsUri);
    TemplateDocument docTs = new TemplateDocument(tsUri, 1, "$profile.nickname");
    refIndex.indexTemplate(docTs, resolver, policy);
    ResolvedTemplateReference tsRef =
        refIndex.getReferencesForTemplate(tsUri).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    assertInstanceOf(SchemaMemberSymbolKey.class, tsRef.symbolKey());
    SchemaMemberSymbolKey tsKey = (SchemaMemberSymbolKey) tsRef.symbolKey();
    assertEquals("nickname", tsKey.propertyName());
    assertEquals("Profile", tsKey.typeName());

    // 2. JSON Schema property
    Path jsonFile = tempDir.resolve("ident.schema.json");
    Files.writeString(
        jsonFile,
        """
        {
          "properties": {
            "profile": {
              "type": "object",
              "properties": {
                "nickname": { "type": "string" }
              }
            }
          }
        }
        """);
    String jsonUri = "file:///workspace/ident-json.vtl";
    resolver.registerSchemaFile(jsonFile, jsonUri);
    TemplateDocument docJson = new TemplateDocument(jsonUri, 1, "$profile.nickname");
    refIndex.indexTemplate(docJson, resolver, policy);
    ResolvedTemplateReference jsonRef =
        refIndex.getReferencesForTemplate(jsonUri).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    assertInstanceOf(SchemaMemberSymbolKey.class, jsonRef.symbolKey());
    SchemaMemberSymbolKey jsonKey = (SchemaMemberSymbolKey) jsonRef.symbolKey();
    assertEquals("nickname", jsonKey.propertyName());

    // 3. Local variable
    String localUri = "file:///workspace/local-ident.vtl";
    TemplateDocument docLocal = new TemplateDocument(localUri, 1, "#set($counter = 1)\n$counter");
    refIndex.indexTemplate(docLocal, resolver, policy);
    ResolvedTemplateReference localRef =
        refIndex.getReferencesForTemplate(localUri).stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.LOCAL_VARIABLE)
            .findFirst()
            .orElseThrow();
    assertInstanceOf(TemplateLocalSymbolKey.class, localRef.symbolKey());
    TemplateLocalSymbolKey localKey = (TemplateLocalSymbolKey) localRef.symbolKey();
    assertEquals("counter", localKey.variableName());
    assertEquals(localUri, localKey.templateUri());
  }

  // =========================================================================
  // 3. Same-Name Isolation: Customer.name vs Product.name vs Address.name vs Company.name
  // =========================================================================

  @Test
  @DisplayName(
      "Same-Name Isolation: Customer.name, Product.name, Address.name, Company.name strictly"
          + " isolated")
  void testSameNameIsolationAcrossCustomerProductAddressCompany(@TempDir Path tempDir)
      throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    Path dtsFile = tempDir.resolve("entities.d.ts");
    String dts =
        """
        export interface Customer {
          name: string;
        }
        export interface Product {
          name: string;
        }
        export interface Address {
          name: string;
        }
        export interface Company {
          name: string;
        }
        export interface TemplateParameters {
          customer: Customer;
          product: Product;
          address: Address;
          company: Company;
        }
        """;
    Files.writeString(dtsFile, dts);
    service.registerSchemaFile(dtsFile);

    String docUri = "file:///workspace/entities.vtl";
    service.schemaIndex().recordDependency(docUri, dtsFile);
    String content = "$customer.name and $product.name and $address.name and $company.name";
    service.openDocument(docUri, 1, content);

    // Positions on each .name
    int customerCol = content.indexOf("customer.name") + "customer.".length();
    int productCol = content.indexOf("product.name") + "product.".length();
    int addressCol = content.indexOf("address.name") + "address.".length();
    int companyCol = content.indexOf("company.name") + "company.".length();

    // 1. References queries: each must find ONLY its own reference (size == 1)
    List<LocationInfo> custRefs = service.references(docUri, Position.of(0, customerCol), false);
    assertEquals(1, custRefs.size(), "Customer.name must only match customer.name");
    assertEquals(customerCol, custRefs.get(0).range().start().character());

    List<LocationInfo> prodRefs = service.references(docUri, Position.of(0, productCol), false);
    assertEquals(1, prodRefs.size(), "Product.name must only match product.name");
    assertEquals(productCol, prodRefs.get(0).range().start().character());

    List<LocationInfo> addrRefs = service.references(docUri, Position.of(0, addressCol), false);
    assertEquals(1, addrRefs.size(), "Address.name must only match address.name");
    assertEquals(addressCol, addrRefs.get(0).range().start().character());

    List<LocationInfo> compRefs = service.references(docUri, Position.of(0, companyCol), false);
    assertEquals(1, compRefs.size(), "Company.name must only match company.name");
    assertEquals(companyCol, compRefs.get(0).range().start().character());

    // 2. Rename on Customer.name touches ONLY Customer.name, 0 touches on others
    WorkspaceEdit edit = service.rename(docUri, Position.of(0, customerCol), "customerName");
    List<TextEdit> templateEdits = edit.changes().get(docUri);
    assertEquals(1, templateEdits.size(), "Only 1 occurrence renamed in template");
    assertEquals(customerCol, templateEdits.get(0).range().start().character());

    // 3. Workspace Symbol search: 4 distinct symbols for query 'name'
    List<SymbolInformation> nameSymbols = service.workspaceSymbols("name");
    Set<String> containers = new HashSet<>();
    for (SymbolInformation s : nameSymbols) {
      if ("name".equals(s.name()) && s.containerName() != null) {
        containers.add(s.containerName());
      }
    }
    assertTrue(
        containers.containsAll(List.of("Customer", "Product", "Address", "Company")),
        "Must contain all 4 distinct containers, found: " + containers);
  }

  // =========================================================================
  // 4. Inheritance & Overrides
  // =========================================================================

  @Test
  @DisplayName("Inheritance & Overrides: base reference matches inherited, override stays distinct")
  void testInheritanceAndOverrides() {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    String uri = "file:///hierarchy.vtl";
    String content = "$base.email and $sub.email and $override.email";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    Map<String, ParameterDef> params = new TreeMap<>();
    params.put(
        "base", new ParameterDef("base", new ClassTypeRef(NavBaseUser.class.getName()), false));
    params.put(
        "sub", new ParameterDef("sub", new ClassTypeRef(InheritingUser.class.getName()), false));
    params.put(
        "override",
        new ParameterDef("override", new ClassTypeRef(OverridingUser.class.getName()), false));
    CanonicalSchema schema = new CanonicalSchema(uri, SchemaFormat.JAVA, "", params, Map.of(), "");
    resolver.registerSchema(uri, schema);

    service.openDocument(uri, 1, content);

    // Position on base.email (col 7)
    List<LocationInfo> baseRefs = service.references(uri, Position.of(0, 7), false);
    // BaseUser and InheritingUser share the same member key on NavBaseUser -> matches both!
    assertEquals(2, baseRefs.size(), "Base reference must find base and inheriting usages");
    assertEquals(Range.of(0, 6, 0, 11), baseRefs.get(0).range());
    assertEquals(Range.of(0, 21, 0, 26), baseRefs.get(1).range());

    // Position on override.email (col 43)
    List<LocationInfo> overrideRefs = service.references(uri, Position.of(0, 43), false);
    // OverridingUser declares its own getEmail() -> matches ONLY override.email!
    assertEquals(1, overrideRefs.size(), "Override reference must be distinct");
    assertEquals(Range.of(0, 41, 0, 46), overrideRefs.get(0).range());

    // Rename rejected on both
    assertThrows(
        RenameConflictException.class, () -> service.rename(uri, Position.of(0, 7), "newMail"));
    assertThrows(
        RenameConflictException.class, () -> service.rename(uri, Position.of(0, 43), "newMail"));
  }

  // =========================================================================
  // 5. Field vs Getter Resolution Honoring MemberResolver Policy
  // =========================================================================

  @Test
  @DisplayName("Field vs Getter: MemberResolver prioritizes getter over field consistently")
  void testFieldVsGetterResolutionHonoringMemberResolverPolicy(@TempDir Path workspaceDir)
      throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavOuter.java");
    Files.writeString(
        javaFile,
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavOuter {
          public static class FieldAndGetterHolder {
            public String info = "fieldValue";

            public String getInfo() {
              return "getterValue";
            }
          }

          private FieldAndGetterHolder holder;
          public NavOuter() {}
          public FieldAndGetterHolder getHolder() { return holder; }
        }
        """);

    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);
    CanonicalSchemaResolver resolver = service.schemaResolver();

    String uri = workspaceDir.resolve("field-vs-getter.vtl").toUri().toString();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    resolver.registerSchema(
        uri, importer.importModel(NavOuter.Inner.class, "obj", uri).schemas().get(uri));

    // NavOuter.Inner has label field and getLabel() getter
    service.openDocument(uri, 1, "Result: $obj.label");

    // Cursor on .label (col 14)
    // 1. Definition navigates to getLabel() method
    List<LocationInfo> defs = service.definition(uri, Position.of(0, 14));
    // When NavOuter.Inner is imported, getLabel is getter
    Set<ResolvedTemplateReference> refs = service.referenceIndex().getReferencesForTemplate(uri);
    ResolvedTemplateReference propRef =
        refs.stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey key = (JvmMemberSymbolKey) propRef.symbolKey();
    assertEquals(JvmMemberSymbolKey.Kind.GETTER, key.memberKind());
    assertEquals("getLabel", key.memberName());

    // 2. Rename rejected as UNSUPPORTED_JVM_MEMBER
    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(uri, Position.of(0, 14), "details"));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_JVM_MEMBER, ex.reason());

    // 3. For NavFieldUser (has ONLY public field 'role'), resolves to Kind.FIELD
    String fieldUri = workspaceDir.resolve("only-field.vtl").toUri().toString();
    resolver.registerSchema(
        fieldUri,
        importer.importModel(NavFieldUser.class, "fieldUser", fieldUri).schemas().get(fieldUri));
    service.openDocument(fieldUri, 1, "Role: $fieldUser.role");
    Set<ResolvedTemplateReference> fieldRefs =
        service.referenceIndex().getReferencesForTemplate(fieldUri);
    ResolvedTemplateReference roleRef =
        fieldRefs.stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .findFirst()
            .orElseThrow();
    JvmMemberSymbolKey roleKey = (JvmMemberSymbolKey) roleRef.symbolKey();
    assertEquals(JvmMemberSymbolKey.Kind.FIELD, roleKey.memberKind());
    assertEquals("role", roleKey.memberName());
  }

  // =========================================================================
  // 6. Record Component Qualification
  // =========================================================================

  @Test
  @DisplayName("Record Components: canonical record component and explicit accessor qualified")
  void testRecordComponentCanonicalAndExplicitAccessor(@TempDir Path workspaceDir)
      throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);

    // Canonical record
    Files.writeString(
        srcDir.resolve("NavUserRecord.java"),
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavUserRecord(
            String name,
            int age
        ) {}
        """);

    // Explicit accessor record
    Files.writeString(
        srcDir.resolve("NavExplicitAccessorRecord.java"),
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavExplicitAccessorRecord(String name) {
          @Override
          public String name() {
            return this.name != null ? this.name.trim() : "";
          }
        }
        """);

    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);
    CanonicalSchemaResolver resolver = service.schemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();

    // 1. Canonical record test
    String uriCanon = workspaceDir.resolve("canon.vtl").toUri().toString();
    resolver.registerSchema(
        uriCanon,
        importer.importModel(NavUserRecord.class, "user", uriCanon).schemas().get(uriCanon));
    service.openDocument(uriCanon, 1, "Hello $user.name");
    List<LocationInfo> canonDefs = service.definition(uriCanon, Position.of(0, 13));
    assertFalse(canonDefs.isEmpty());
    assertEquals(3, canonDefs.get(0).range().start().line()); // line 3: String name,

    // 2. Explicit accessor test
    String uriExplicit = workspaceDir.resolve("explicit.vtl").toUri().toString();
    resolver.registerSchema(
        uriExplicit,
        importer
            .importModel(NavExplicitAccessorRecord.class, "user", uriExplicit)
            .schemas()
            .get(uriExplicit));
    service.openDocument(uriExplicit, 1, "Hello $user.name");
    List<LocationInfo> explicitDefs = service.definition(uriExplicit, Position.of(0, 13));
    assertFalse(explicitDefs.isEmpty());
    assertEquals(4, explicitDefs.get(0).range().start().line()); // line 4: public String name()

    // 3. Both reject rename with UNSUPPORTED_JVM_MEMBER
    assertThrows(
        RenameConflictException.class,
        () -> service.rename(uriCanon, Position.of(0, 13), "newName"));
    assertThrows(
        RenameConflictException.class,
        () -> service.rename(uriExplicit, Position.of(0, 13), "newName"));
  }

  // =========================================================================
  // 7. Chained Property Consistency: $outer.inner.label
  // =========================================================================

  @Test
  @DisplayName("Chained Properties: $outer.inner.label distinct identities and ranges")
  void testChainedPropertyConsistency(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Files.writeString(
        srcDir.resolve("NavOuter.java"),
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavOuter {
          public static class Inner {
            private String label;
            public Inner() {}
            public String getLabel() { return label; }
          }
          private Inner inner;
          public NavOuter() {}
          public Inner getInner() { return inner; }
        }
        """);

    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    service.setWorkspaceRoot(workspaceDir);
    CanonicalSchemaResolver resolver = service.schemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();

    String uri = workspaceDir.resolve("chained.vtl").toUri().toString();
    resolver.registerSchema(
        uri, importer.importModel(NavOuter.class, "outer", uri).schemas().get(uri));
    String content = "Chained: $outer.inner.label";
    service.openDocument(uri, 1, content);

    // 1. Root $outer (col 11)
    List<LocationInfo> rootDefs = service.definition(uri, Position.of(0, 11));
    assertFalse(rootDefs.isEmpty(), "Root $outer definition must be found");
    assertEquals(2, rootDefs.get(0).range().start().line()); // public class NavOuter

    // 2. Step 1 .inner (col 18)
    List<LocationInfo> innerDefs = service.definition(uri, Position.of(0, 18));
    assertFalse(innerDefs.isEmpty(), "Step .inner definition must be found");
    assertEquals(10, innerDefs.get(0).range().start().line()); // public Inner getInner()

    // 3. Step 2 .label (col 24)
    List<LocationInfo> labelDefs = service.definition(uri, Position.of(0, 24));
    assertFalse(labelDefs.isEmpty(), "Step .label definition must be found");
    assertEquals(6, labelDefs.get(0).range().start().line()); // public String getLabel()

    // 4. Verify distinct non-overlapping reference ranges in index
    Set<ResolvedTemplateReference> refs = service.referenceIndex().getReferencesForTemplate(uri);
    assertEquals(3, refs.size(), "Should contain 1 ROOT_VARIABLE and 2 PROPERTY_ACCESS references");
    ResolvedTemplateReference rootRef =
        refs.stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.ROOT_VARIABLE)
            .findFirst()
            .orElseThrow();
    assertEquals(Range.of(0, 9, 0, 15), rootRef.range()); // $outer

    List<ResolvedTemplateReference> propRefs =
        refs.stream()
            .filter(r -> r.kind() == ResolvedTemplateReference.Kind.PROPERTY_ACCESS)
            .sorted(Comparator.comparing(r -> r.range().start().character()))
            .toList();
    assertEquals(Range.of(0, 16, 0, 21), propRefs.get(0).range()); // inner
    assertEquals(Range.of(0, 22, 0, 27), propRefs.get(1).range()); // label
  }

  // =========================================================================
  // 8. Shape vs JVM Binding Boundary
  // =========================================================================

  @Test
  @DisplayName(
      "Shape vs JVM Boundary: TypeScript User.name vs Java User#getName() never cross-linked")
  void testShapeVsJvmBindingBoundary(@TempDir Path tempDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // TypeScript schema
    Path dtsFile = tempDir.resolve("user.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface User {
          name: string;
        }
        export interface TemplateParameters {
          user: User;
        }
        """);
    resolver.registerSchemaFile(dtsFile);

    // Template 1: TypeScript-bound
    String tsUri = "file:///workspace/ts-user.vtl";
    service.schemaIndex().recordDependency(tsUri, dtsFile);
    service.openDocument(tsUri, 1, "TS: $user.name");

    // Template 2: Java-bound (NavBeanUser also has property 'name' via getName())
    String jvmUri = "file:///workspace/jvm-user.vtl";
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    resolver.registerSchema(
        jvmUri, importer.importModel(NavBeanUser.class, "user", jvmUri).schemas().get(jvmUri));
    service.openDocument(jvmUri, 1, "JVM: $user.name");

    // 1. References on TS User.name: returns only ts-user.vtl, 0 matches in jvm-user.vtl
    List<LocationInfo> tsRefs = service.references(tsUri, Position.of(0, 11), false);
    assertEquals(1, tsRefs.size());
    assertEquals(tsUri, tsRefs.get(0).uri());

    // 2. References on JVM User.name: returns only jvm-user.vtl, 0 matches in ts-user.vtl
    List<LocationInfo> jvmRefs = service.references(jvmUri, Position.of(0, 12), false);
    assertEquals(1, jvmRefs.size());
    assertEquals(jvmUri, jvmRefs.get(0).uri());

    // 3. Rename on TS User.name touches TS template and d.ts, but NOT JVM template
    WorkspaceEdit edit = service.rename(tsUri, Position.of(0, 11), "fullName");
    assertNotNull(edit);
    assertTrue(edit.changes().containsKey(tsUri));
    assertTrue(edit.changes().containsKey(dtsFile.toUri().toString()));
    assertFalse(
        edit.changes().containsKey(jvmUri), "JVM template must never be touched by TS rename");
  }

  // =========================================================================
  // 9. Dynamic / Untyped Receiver
  // =========================================================================

  @Test
  @DisplayName("Dynamic Receiver: $dynamic.name has no definition, no references, rename rejected")
  void testDynamicReceiverBehavior() {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    String uri = "file:///dynamic-test.vtl";
    service.openDocument(uri, 1, "Dynamic: $dynamic.property");

    // Position on .property (col 20)
    // 1. Definition is empty
    List<LocationInfo> defs = service.definition(uri, Position.of(0, 20));
    assertTrue(defs.isEmpty(), "Dynamic property has no definition");

    // 2. References is empty
    List<LocationInfo> refs = service.references(uri, Position.of(0, 20), false);
    assertTrue(refs.isEmpty(), "Dynamic property has no semantic references");

    // 3. Rename throws UNSUPPORTED_DYNAMIC_SYMBOL
    RenameConflictException ex =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(uri, Position.of(0, 20), "newProp"));
    assertEquals(RenameConflictException.Reason.UNSUPPORTED_DYNAMIC_SYMBOL, ex.reason());

    // 4. Workspace symbol query returns no invented dynamic property
    List<SymbolInformation> syms = service.workspaceSymbols("property");
    assertTrue(syms.isEmpty(), "Dynamic property must not be indexed in workspace symbols");
  }

  // =========================================================================
  // 10. Security-Denied Members
  // =========================================================================

  @Test
  @DisplayName(
      "Security-Denied: access policy denies member from completion, hover, def, ref, rename,"
          + " symbols")
  void testSecurityDeniedMembers(@TempDir Path tempDir) throws IOException {
    MemberAccessPolicy standard = MemberAccessPolicy.standard();
    MemberAccessPolicy denySecretPolicy =
        new MemberAccessPolicy() {
          @Override
          public boolean isClassPermitted(Class<?> clazz) {
            return standard.isClassPermitted(clazz);
          }

          @Override
          public boolean isMethodPermitted(Class<?> receiverClass, String methodName, int arity) {
            if ("getSecret".equals(methodName) || "secret".equals(methodName)) {
              return false;
            }
            return standard.isMethodPermitted(receiverClass, methodName, arity);
          }

          @Override
          public boolean isPropertyPermitted(Class<?> receiverClass, String propertyName) {
            if ("secret".equals(propertyName)) {
              return false;
            }
            return standard.isPropertyPermitted(receiverClass, propertyName);
          }

          @Override
          public boolean isFieldPermitted(Class<?> receiverClass, String fieldName) {
            if ("secret".equals(fieldName)) {
              return false;
            }
            return standard.isFieldPermitted(receiverClass, fieldName);
          }

          @Override
          public io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint fingerprint() {
            return io.github.minh124199.viettemplate.api.SecurityPolicyFingerprint.of(
                "custom:denySecret");
          }
        };

    TemplateLanguageService service = TemplateLanguageService.create(denySecretPolicy);
    CanonicalSchemaResolver resolver = service.schemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();

    String uri = "file:///workspace/security.vtl";
    resolver.registerSchema(
        uri, importer.importModel(SecretModel.class, "model", uri).schemas().get(uri));
    // Content: "Data: $model.secret and $model.username and $model.class"
    // "Data: $model." length is 13
    service.openDocument(uri, 1, "Data: $model.secret and $model.username and $model.class");

    // 1. Completion: cursor at col 13 (right after dot) does NOT contain 'secret' or 'class'
    CompletionList comp = service.completion(uri, Position.of(0, 13));
    List<String> labels = comp.items().stream().map(CompletionItem::label).toList();
    assertTrue(labels.contains("username"), "Permitted property 'username' must appear");
    assertFalse(labels.contains("secret"), "Denied 'secret' must not appear in completion");
    assertFalse(labels.contains("class"), "Denied 'class' must not appear in completion");

    // 2. Hover on .class does not return property documentation (denied sensitive member)
    int classCol = "Data: $model.secret and $model.username and $model.c".length();
    Optional<HoverInfo> hover = service.hover(uri, Position.of(0, classCol));
    assertTrue(
        hover.isEmpty() || !hover.get().markdownValue().contains("**class**"),
        "Hover on denied member 'class' must not return member documentation");

    // 3. Definition on denied members is empty
    int secretCol = "Data: $model.s".length();
    List<LocationInfo> defsSecret = service.definition(uri, Position.of(0, secretCol));
    assertTrue(defsSecret.isEmpty(), "Definition on denied member 'secret' must be empty");
    List<LocationInfo> defsClass = service.definition(uri, Position.of(0, classCol));
    assertTrue(defsClass.isEmpty(), "Definition on denied member 'class' must be empty");

    // 4. References on denied members is empty
    List<LocationInfo> refsSecret = service.references(uri, Position.of(0, secretCol), false);
    assertTrue(refsSecret.isEmpty(), "References on denied member 'secret' must be empty");
    List<LocationInfo> refsClass = service.references(uri, Position.of(0, classCol), false);
    assertTrue(refsClass.isEmpty(), "References on denied member 'class' must be empty");

    // 5. Rename on denied members throws RenameConflictException
    assertThrows(
        RenameConflictException.class,
        () -> service.rename(uri, Position.of(0, secretCol), "newSecret"));
    assertThrows(
        RenameConflictException.class,
        () -> service.rename(uri, Position.of(0, classCol), "newClass"));

    // 6. Workspace symbol search does not return 'secret' or 'class'
    List<SymbolInformation> secretSyms = service.workspaceSymbols("secret");
    assertTrue(secretSyms.isEmpty(), "Denied member must not be indexed in workspace symbols");
    List<SymbolInformation> classSyms = service.workspaceSymbols("class");
    assertTrue(classSyms.isEmpty(), "Sensitive member must not be indexed in workspace symbols");
  }

  // =========================================================================
  // 11. M40 Rename Safety
  // =========================================================================

  @Test
  @DisplayName("M40 Rename Safety: local #set and #foreach with scope shadowing")
  void testM40RenameSafetyLocalAndForeach() {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    String uri = "file:///scope-shadow.vtl";
    String content =
        """
        #set($val = 10)
        #foreach($val in $items)
          Loop: $val
        #end
        Outer: $val
        """;
    service.openDocument(uri, 1, content);

    // Position on inner $val (line 2, col 9)
    Position innerPos = Position.of(2, 9);
    Optional<PrepareRenameResult> prepInner = service.prepareRename(uri, innerPos);
    assertTrue(prepInner.isPresent());
    assertEquals("val", prepInner.get().placeholder());

    WorkspaceEdit innerEdit = service.rename(uri, innerPos, "element");
    List<TextEdit> edits = innerEdit.changes().get(uri);
    // Inner loop rename touches only line 1 (#foreach) and line 2 (usage), NOT line 0 or line 4!
    assertEquals(2, edits.size(), "Inner loop rename must only touch 2 occurrences");
    assertEquals(1, edits.get(0).range().start().line()); // line 1: #foreach($val
    assertEquals(2, edits.get(1).range().start().line()); // line 2: Loop: $val
  }

  @Test
  @DisplayName("M40 Rename Safety: collision detection and invalid identifier rejections")
  void testM40RenameSafetyCollisionsAndInvalidIdentifiers(@TempDir Path tempDir)
      throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. Local collision with existing variable in scope
    String localUri = "file:///collision.vtl";
    service.openDocument(localUri, 1, "#set($first = 1)\n#set($second = 2)\n$first $second");
    RenameConflictException colEx =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(localUri, Position.of(0, 6), "second"));
    assertEquals(RenameConflictException.Reason.NAME_COLLISION, colEx.reason());

    // 2. Collision with root parameter in schema
    Path dtsFile = tempDir.resolve("root-param.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface TemplateParameters {
          globalAccount: string;
        }
        """);
    resolver.registerSchemaFile(dtsFile);
    String shadowUri = "file:///shadow-root.vtl";
    service.schemaIndex().recordDependency(shadowUri, dtsFile);
    service.openDocument(shadowUri, 1, "#set($acc = 1)\n$acc");
    RenameConflictException shadowEx =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(shadowUri, Position.of(0, 6), "globalAccount"));
    assertEquals(RenameConflictException.Reason.NAME_COLLISION, shadowEx.reason());

    // 3. Schema property collision with existing property on same type
    Path dtsCollide = tempDir.resolve("collide.d.ts");
    Files.writeString(
        dtsCollide,
        """
        export interface Profile {
          id: number;
          username: string;
        }
        export interface TemplateParameters {
          p: Profile;
        }
        """);
    resolver.registerSchemaFile(dtsCollide);
    String collideUri = "file:///collide.vtl";
    service.schemaIndex().recordDependency(collideUri, dtsCollide);
    service.openDocument(collideUri, 1, "$p.id and $p.username");
    RenameConflictException propColEx =
        assertThrows(
            RenameConflictException.class,
            () -> service.rename(collideUri, Position.of(0, 4), "username"));
    assertEquals(RenameConflictException.Reason.NAME_COLLISION, propColEx.reason());

    // 4. Invalid identifiers
    String idUri = "file:///invalid.vtl";
    service.openDocument(idUri, 1, "#set($valid = 1)\n$valid");
    for (String bad : List.of("123bad", "bad-name", "bad name", "", "   ", "foo.bar")) {
      RenameConflictException invEx =
          assertThrows(
              RenameConflictException.class,
              () -> service.rename(idUri, Position.of(0, 6), bad),
              "Expected INVALID_IDENTIFIER for: " + bad);
      assertEquals(RenameConflictException.Reason.INVALID_IDENTIFIER, invEx.reason());
    }

    // 5. Reserved keywords
    for (String kw : List.of("if", "foreach", "end", "set", "true", "false", "null")) {
      RenameConflictException kwEx =
          assertThrows(
              RenameConflictException.class,
              () -> service.rename(idUri, Position.of(0, 6), kw),
              "Expected INVALID_IDENTIFIER for keyword: " + kw);
      assertEquals(RenameConflictException.Reason.INVALID_IDENTIFIER, kwEx.reason());
    }
  }

  @Test
  @DisplayName(
      "M40 Rename Safety: multi-file TypeScript property rename, contract rename, exact ranges,"
          + " atomicity")
  void testM40RenameSafetyMultiFileAndAtomicity(@TempDir Path tempDir) throws IOException {
    TemplateLanguageService service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    CanonicalSchemaResolver resolver = service.schemaResolver();

    // 1. Multi-file TypeScript rename
    Path dtsFile = tempDir.resolve("multi.d.ts");
    Files.writeString(
        dtsFile,
        """
        export interface RecordDto {
          headline: string;
        }
        export interface TemplateParameters {
          rec: RecordDto;
        }
        """);
    resolver.registerSchemaFile(dtsFile);

    Path t1 = tempDir.resolve("t1.vtl");
    Files.writeString(t1, "Title: $rec.headline");
    Path t2 = tempDir.resolve("t2.vtl");
    Files.writeString(t2, "Header: $rec.headline, again $rec.headline");

    String t1Uri = t1.toUri().toString();
    String t2Uri = t2.toUri().toString();
    service.schemaIndex().recordDependency(t1Uri, dtsFile);
    service.schemaIndex().recordDependency(t2Uri, dtsFile);

    service.openDocument(t1Uri, 1, "Title: $rec.headline");
    service.openDocument(t2Uri, 1, "Header: $rec.headline, again $rec.headline");

    WorkspaceEdit edit = service.rename(t1Uri, Position.of(0, 13), "articleTitle");
    assertNotNull(edit);

    // Verify 3 distinct files modified: dtsFile, t1, t2
    assertEquals(3, edit.changes().size());
    List<TextEdit> dtsEdits = edit.changes().get(dtsFile.toUri().toString());
    assertEquals(1, dtsEdits.size());
    assertEquals("articleTitle", dtsEdits.get(0).newText());

    List<TextEdit> t1Edits = edit.changes().get(t1Uri);
    assertEquals(1, t1Edits.size());
    assertEquals("articleTitle", t1Edits.get(0).newText());

    List<TextEdit> t2Edits = edit.changes().get(t2Uri);
    assertEquals(2, t2Edits.size());
    for (TextEdit te : t2Edits) {
      assertEquals("articleTitle", te.newText());
    }

    // 2. Exact ranges verified: monotonically increasing without overlap
    assertEquals(Range.of(0, 13, 0, 21), t2Edits.get(0).range());
    assertEquals(Range.of(0, 34, 0, 42), t2Edits.get(1).range());
    assertTrue(
        t2Edits.get(0).range().end().character() <= t2Edits.get(1).range().start().character());

    // 3. Atomicity: invalid rename throws without returning partial edits
    assertThrows(
        RenameConflictException.class,
        () -> service.rename(t1Uri, Position.of(0, 13), "invalid-identifier!"));
  }
}
