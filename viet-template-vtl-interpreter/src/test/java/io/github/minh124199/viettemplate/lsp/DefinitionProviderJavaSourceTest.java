package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.lsp.models.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefinitionProviderJavaSourceTest {

  private final JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
  private TemplateLanguageService service;
  private CanonicalSchemaResolver resolver;

  @BeforeEach
  void setUp() {
    service = TemplateLanguageService.create(MemberAccessPolicy.standard());
    resolver = service.schemaResolver();
  }

  @Test
  @DisplayName("Navigate from root reference $user to record class declaration in Java source")
  void testNavigateToRootModelType(@TempDir Path workspaceDir) throws IOException {
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

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("user.vtl");
    Files.writeString(templateFile, "Hello $user");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Hello $user");

    // Position on $user: line 0, col 8
    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 8));
    assertFalse(defs.isEmpty(), "Expected definition location for $user");
    LocationInfo loc = defs.get(0);
    assertEquals(javaFile.toUri().toString(), loc.uri());
    assertEquals(2, loc.range().start().line()); // line 2: public record NavUserRecord(
    assertEquals("NavUserRecord", extractSnippet(javaSource, loc.range()));
  }

  @Test
  @DisplayName("Navigate from property step $user.name to record component in record header")
  void testNavigateToRecordComponentInHeader(@TempDir Path workspaceDir) throws IOException {
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

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("user.vtl");
    Files.writeString(templateFile, "Hello $user.name");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Hello $user.name");

    // Position on .name: line 0, col 13
    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs.isEmpty(), "Expected definition location for $user.name");
    LocationInfo loc = defs.get(0);
    assertEquals(javaFile.toUri().toString(), loc.uri());
    assertEquals(3, loc.range().start().line()); // line 3: String name,
    assertEquals("name", extractSnippet(javaSource, loc.range()));
  }

  @Test
  @DisplayName(
      "Navigate to explicit accessor method when record defines an explicit accessor instead of"
          + " component")
  void testNavigateToExplicitRecordAccessorMethod(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavExplicitAccessorRecord.java");
    String javaSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavExplicitAccessorRecord(String name) {
          @Override
          public String name() {
            return this.name != null ? this.name.trim() : "";
          }
        }
        """;
    Files.writeString(javaFile, javaSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("explicit.vtl");
    Files.writeString(templateFile, "Hello $user.name");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavExplicitAccessorRecord.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Hello $user.name");

    // Position on .name: line 0, col 13
    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs.isEmpty(), "Expected definition location for explicit accessor name");
    LocationInfo loc = defs.get(0);
    assertEquals(javaFile.toUri().toString(), loc.uri());
    assertEquals(4, loc.range().start().line()); // line 4: public String name() {
    assertEquals("name", extractSnippet(javaSource, loc.range()));
  }

  @Test
  @DisplayName("Navigate from JavaBean getter property to getName() method")
  void testNavigateToJavaBeanGetter(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavBeanUser.java");
    String javaSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavBeanUser {
          private String name;
          private boolean active;

          public String getName() {
            return name;
          }

          public boolean isActive() {
            return active;
          }
        }
        """;
    Files.writeString(javaFile, javaSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("bean.vtl");
    Files.writeString(templateFile, "Name: $user.name");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Name: $user.name");

    // Position on .name: line 0, col 13
    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs.isEmpty(), "Expected definition location for $user.name");
    LocationInfo loc = defs.get(0);
    assertEquals(javaFile.toUri().toString(), loc.uri());
    assertEquals(6, loc.range().start().line()); // line 6: public String getName() {
    assertEquals("getName", extractSnippet(javaSource, loc.range()));
  }

  @Test
  @DisplayName("Navigate from boolean property to isActive() boolean getter")
  void testNavigateToBooleanGetter(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavBeanUser.java");
    String javaSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavBeanUser {
          private String name;
          private boolean active;

          public String getName() {
            return name;
          }

          public boolean isActive() {
            return active;
          }
        }
        """;
    Files.writeString(javaFile, javaSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("boolean.vtl");
    Files.writeString(templateFile, "Active: $user.active");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Active: $user.active");

    // Position on .active: line 0, col 16
    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 16));
    assertFalse(defs.isEmpty(), "Expected definition location for $user.active");
    LocationInfo loc = defs.get(0);
    assertEquals(javaFile.toUri().toString(), loc.uri());
    assertEquals(10, loc.range().start().line()); // line 10: public boolean isActive() {
    assertEquals("isActive", extractSnippet(javaSource, loc.range()));
  }

  @Test
  @DisplayName("Navigate from public field property to field declaration")
  void testNavigateToPublicField(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavFieldUser.java");
    String javaSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavFieldUser {
          public String role;
        }
        """;
    Files.writeString(javaFile, javaSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("field.vtl");
    Files.writeString(templateFile, "Role: $user.role");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavFieldUser.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Role: $user.role");

    // Position on .role: line 0, col 13
    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs.isEmpty(), "Expected definition location for $user.role");
    LocationInfo loc = defs.get(0);
    assertEquals(javaFile.toUri().toString(), loc.uri());
    assertEquals(3, loc.range().start().line()); // line 3: public String role;
    assertEquals("role", extractSnippet(javaSource, loc.range()));
  }

  @Test
  @DisplayName("Navigate from inherited property to superclass file declaring the member")
  void testNavigateToInheritedMemberOnDeclaringClass(@TempDir Path workspaceDir)
      throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);

    Path baseFile = srcDir.resolve("NavBaseUser.java");
    String baseSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavBaseUser {
          private String email;

          public String getEmail() {
            return email;
          }
        }
        """;
    Files.writeString(baseFile, baseSource);

    Path adminFile = srcDir.resolve("NavAdminUser.java");
    String adminSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavAdminUser extends NavBaseUser {
          private String role;

          public String getRole() {
            return role;
          }
        }
        """;
    Files.writeString(adminFile, adminSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("admin.vtl");
    Files.writeString(templateFile, "Email: $admin.email, Role: $admin.role");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavAdminUser.class, "admin", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Email: $admin.email, Role: $admin.role");

    // 1. $admin.email: inherited getter -> must navigate to NavBaseUser.java
    List<LocationInfo> emailDefs = service.definition(docUri, Position.of(0, 15));
    assertFalse(emailDefs.isEmpty(), "Expected definition for inherited member $admin.email");
    LocationInfo emailLoc = emailDefs.get(0);
    assertEquals(baseFile.toUri().toString(), emailLoc.uri());
    assertEquals(
        5, emailLoc.range().start().line()); // line 5 in NavBaseUser: public String getEmail()
    assertEquals("getEmail", extractSnippet(baseSource, emailLoc.range()));

    // 2. $admin.role: subclass getter -> must navigate to NavAdminUser.java
    List<LocationInfo> roleDefs = service.definition(docUri, Position.of(0, 35));
    assertFalse(roleDefs.isEmpty(), "Expected definition for subclass member $admin.role");
    LocationInfo roleLoc = roleDefs.get(0);
    assertEquals(adminFile.toUri().toString(), roleLoc.uri());
    assertEquals(
        5, roleLoc.range().start().line()); // line 5 in NavAdminUser: public String getRole()
    assertEquals("getRole", extractSnippet(adminSource, roleLoc.range()));
  }

  @Test
  @DisplayName("Navigate to member of nested class model")
  void testNavigateToNestedClassMember(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);

    Path outerFile = srcDir.resolve("NavOuter.java");
    String outerSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavOuter {
          public static class Inner {
            private String label;

            public String getLabel() {
              return label;
            }
          }

          private Inner inner;

          public Inner getInner() {
            return inner;
          }
        }
        """;
    Files.writeString(outerFile, outerSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("nested.vtl");
    Files.writeString(templateFile, "Label: $outer.inner.label");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavOuter.class, "outer", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Label: $outer.inner.label");

    // 1. .inner: line 0, col 16
    List<LocationInfo> innerDefs = service.definition(docUri, Position.of(0, 16));
    assertFalse(innerDefs.isEmpty(), "Expected definition for $outer.inner");
    assertEquals(outerFile.toUri().toString(), innerDefs.get(0).uri());
    assertEquals(13, innerDefs.get(0).range().start().line()); // public Inner getInner()
    assertEquals("getInner", extractSnippet(outerSource, innerDefs.get(0).range()));

    // 2. .label: line 0, col 22
    List<LocationInfo> labelDefs = service.definition(docUri, Position.of(0, 22));
    assertFalse(labelDefs.isEmpty(), "Expected definition for $outer.inner.label");
    assertEquals(outerFile.toUri().toString(), labelDefs.get(0).uri());
    assertEquals(6, labelDefs.get(0).range().start().line()); // public String getLabel()
    assertEquals("getLabel", extractSnippet(outerSource, labelDefs.get(0).range()));
  }

  @Test
  @DisplayName("Pure Java model without source returns empty list (definition unavailable)")
  void testMissingSourceReturnsEmptyList(@TempDir Path workspaceDir) throws IOException {
    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("missing.vtl");
    Files.writeString(templateFile, "Name: $user.name");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Name: $user.name");

    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 13));
    assertTrue(defs.isEmpty(), "Pure Java model without source must return empty list");
  }

  @Test
  @DisplayName("Missing Java source falls back to companion .contract file when present")
  void testMissingSourceFallsBackToCompanionContract(@TempDir Path workspaceDir)
      throws IOException {
    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("profile.vtl");
    Files.writeString(templateFile, "Name: $user.name");
    String docUri = templateFile.toUri().toString();

    Path contractFile = workspaceDir.resolve("profile.vtl.contract");
    String contractContent = "user=" + NavUserRecord.class.getName() + "\n";
    Files.writeString(contractFile, contractContent);

    resolver.registerSchemaFile(contractFile);
    service.schemaIndex().recordDependency(docUri, contractFile);

    service.openDocument(docUri, 1, "Name: $user.name");

    List<LocationInfo> defs = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs.isEmpty(), "Should fall back to .contract when source is missing");
    assertEquals(contractFile.toUri().toString(), defs.get(0).uri());
  }

  @Test
  @DisplayName("Non-JVM schemas (.d.ts, .schema.json, .vt-schema.json) are unaffected")
  void testNonJvmSchemasUnaffected(@TempDir Path workspaceDir) throws IOException {
    // 1. TypeScript .d.ts
    Path tsFile = workspaceDir.resolve("ts-model.d.ts");
    String tsContent =
        """
        export interface TsUser {
          name: string;
        }

        export interface TemplateParameters {
          user: TsUser;
        }
        """;
    Files.writeString(tsFile, tsContent);
    resolver.registerSchemaFile(tsFile);

    String tsDocUri = "file:///workspace/ts.vtl";
    service.schemaIndex().recordDependency(tsDocUri, tsFile);
    service.openDocument(tsDocUri, 1, "Hello $user.name");

    List<LocationInfo> tsDefs = service.definition(tsDocUri, Position.of(0, 13));
    assertFalse(tsDefs.isEmpty(), "Expected definition in .d.ts file");
    assertTrue(tsDefs.get(0).uri().endsWith(".d.ts"));

    // 2. JSON Schema .schema.json
    Path jsonFile = workspaceDir.resolve("json-model.schema.json");
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
                "name": { "type": "string" }
              }
            }
          }
        }
        """;
    Files.writeString(jsonFile, jsonContent);
    resolver.registerSchemaFile(jsonFile);

    String jsonDocUri = "file:///workspace/json.vtl";
    service.schemaIndex().recordDependency(jsonDocUri, jsonFile);
    service.openDocument(jsonDocUri, 1, "Hello $user.name");

    List<LocationInfo> jsonDefs = service.definition(jsonDocUri, Position.of(0, 13));
    assertFalse(jsonDefs.isEmpty(), "Expected definition in .schema.json file");
    assertTrue(jsonDefs.get(0).uri().endsWith(".schema.json"));
  }

  @Test
  @DisplayName("In-template #set and #foreach variables remain unaffected")
  void testInTemplateVariablesUnaffected() {
    String text =
        """
        #set($greeting = "Hello")
        #foreach($item in $items)
          $greeting $item
        #end
        """;
    String docUri = "file:///workspace/local.vtl";
    service.openDocument(docUri, 1, text);

    // Line 2: "  $greeting $item"
    // $greeting on line 2, col 4
    List<LocationInfo> setDefs = service.definition(docUri, Position.of(2, 4));
    assertFalse(setDefs.isEmpty());
    assertEquals(docUri, setDefs.get(0).uri());
    assertEquals(0, setDefs.get(0).range().start().line()); // Defined on line 0 (#set)

    // $item on line 2, col 14
    List<LocationInfo> loopDefs = service.definition(docUri, Position.of(2, 14));
    assertFalse(loopDefs.isEmpty());
    assertEquals(docUri, loopDefs.get(0).uri());
    assertEquals(1, loopDefs.get(0).range().start().line()); // Defined on line 1 (#foreach)
  }

  @Test
  @DisplayName("Cache invalidation: edits to Java source file update definition coordinates")
  void testCacheInvalidationOnSourceEdit(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavBeanUser.java");
    String initialSource =
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavBeanUser {
          public String getName() {
            return "initial";
          }
        }
        """;
    Files.writeString(javaFile, initialSource);

    service.setWorkspaceRoot(workspaceDir);

    Path templateFile = workspaceDir.resolve("dyn.vtl");
    Files.writeString(templateFile, "Name: $user.name");
    String docUri = templateFile.toUri().toString();

    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", docUri);
    resolver.registerSchema(docUri, res.schemas().get(docUri));

    service.openDocument(docUri, 1, "Name: $user.name");

    List<LocationInfo> defs1 = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs1.isEmpty());
    assertEquals(3, defs1.get(0).range().start().line());

    // Modify source file: add 5 comment lines at top
    String modifiedSource =
        """
        // Line 1
        // Line 2
        // Line 3
        // Line 4
        // Line 5
        package io.github.minh124199.viettemplate.lsp.models;

        public class NavBeanUser {
          public String getName() {
            return "modified";
          }
        }
        """;
    // Ensure modified time differs
    try {
      Thread.sleep(50);
    } catch (InterruptedException ignored) {
    }
    Files.writeString(javaFile, modifiedSource);

    // Notify service of file change
    service.onWatchedFileChanged(javaFile.toString(), 2);

    List<LocationInfo> defs2 = service.definition(docUri, Position.of(0, 13));
    assertFalse(defs2.isEmpty());
    assertEquals(8, defs2.get(0).range().start().line()); // shifted by 5 lines: 3 + 5 = 8
    assertEquals("getName", extractSnippet(modifiedSource, defs2.get(0).range()));
  }

  private static String extractSnippet(String source, Range range) {
    String[] lines = source.split("\\R", -1);
    int startLine = range.start().line();
    int endLine = range.end().line();
    int startCol = range.start().character();
    int endCol = range.end().character();

    if (startLine == endLine) {
      return lines[startLine].substring(startCol, endCol);
    }
    StringBuilder sb = new StringBuilder();
    for (int l = startLine; l <= endLine; l++) {
      if (l == startLine) {
        sb.append(lines[l].substring(startCol)).append("\n");
      } else if (l == endLine) {
        sb.append(lines[l], 0, endCol);
      } else {
        sb.append(lines[l]).append("\n");
      }
    }
    return sb.toString();
  }
}
