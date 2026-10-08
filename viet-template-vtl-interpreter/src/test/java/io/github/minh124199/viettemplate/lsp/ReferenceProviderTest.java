package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.lsp.models.NavBeanUser;
import io.github.minh124199.viettemplate.lsp.models.NavUserRecord;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.JavaModelSchemaImporter;
import io.github.minh124199.viettemplate.schema.SchemaImportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceProviderTest {

  @Test
  @DisplayName("Finds all property references in a template and respects includeDeclaration")
  void testPropertyReferencesWithDeclaration(@TempDir Path workspaceDir) throws IOException {
    Path srcDir =
        workspaceDir.resolve("src/main/java/io/github/minh124199/viettemplate/lsp/models");
    Files.createDirectories(srcDir);
    Path javaFile = srcDir.resolve("NavUserRecord.java");
    Files.writeString(
        javaFile,
        """
        package io.github.minh124199.viettemplate.lsp.models;

        public record NavUserRecord(String name, int age) {}
        """);

    Path templateFile = workspaceDir.resolve("welcome.vtl");
    String content = "Hello $user.name! And once again $user.name!";
    Files.writeString(templateFile, content);

    String uri = templateFile.toUri().toString();
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavUserRecord.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    schemaIndex.javaSourceLocator().setWorkspaceRoot(workspaceDir);

    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on first 'name' (col 12)
    Position pos = Position.of(0, 12);

    // 1. includeDeclaration = false: only template usages
    List<LocationInfo> refsWithoutDecl =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(2, refsWithoutDecl.size());
    for (LocationInfo loc : refsWithoutDecl) {
      assertEquals(uri, loc.uri());
      assertEquals(0, loc.range().start().line());
    }
    // Verify exact ranges exclude leading dot
    assertEquals(Range.of(0, 12, 0, 16), refsWithoutDecl.get(0).range());
    assertEquals(Range.of(0, 39, 0, 43), refsWithoutDecl.get(1).range());

    // 2. includeDeclaration = true: template usages + Java source declaration
    List<LocationInfo> refsWithDecl =
        ReferenceProvider.references(
            doc, pos, true, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(3, refsWithDecl.size());
    assertTrue(refsWithDecl.stream().anyMatch(l -> l.uri().endsWith("NavUserRecord.java")));
  }

  @Test
  @DisplayName("Finds in-template local variable references and respects includeDeclaration")
  void testLocalVariableReferences() {
    String uri = "file:///local.vtl";
    String content = "#set($val = 42)\nValue: $val and again $val.";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $val in line 1, col 8
    Position pos = Position.of(1, 8);

    // includeDeclaration = false -> 2 usages only
    List<LocationInfo> usages =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(2, usages.size());
    assertEquals(1, usages.get(0).range().start().line());
    assertEquals(1, usages.get(1).range().start().line());

    // includeDeclaration = true -> #set on line 0 + 2 usages
    List<LocationInfo> all =
        ReferenceProvider.references(
            doc, pos, true, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(3, all.size());
    assertTrue(all.stream().anyMatch(l -> l.range().start().line() == 0));
  }

  @Test
  @DisplayName("Finds foreach loop variable references in template body")
  void testForeachVariableReferences() {
    String uri = "file:///loop.vtl";
    String content = "#foreach($item in $items)\nItem: $item\nAgain: $item\n#end";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on $item at line 1, col 7
    Position pos = Position.of(1, 7);

    List<LocationInfo> usages =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(2, usages.size());

    List<LocationInfo> all =
        ReferenceProvider.references(
            doc, pos, true, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(3, all.size());
    assertTrue(all.stream().anyMatch(l -> l.range().start().line() == 0));
  }

  @Test
  @DisplayName("Finds cross-template references for shared models")
  void testCrossTemplateReferences() {
    String uri1 = "file:///template1.vtl";
    String uri2 = "file:///template2.vtl";
    String content1 = "First: $user.name";
    String content2 = "Second: $user.name and $user.name";

    TemplateDocument doc1 = new TemplateDocument(uri1, 1, content1);
    TemplateDocument doc2 = new TemplateDocument(uri2, 1, content2);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res1 = importer.importModel(NavUserRecord.class, "user", uri1);
    SchemaImportResult res2 = importer.importModel(NavUserRecord.class, "user", uri2);
    resolver.registerSchema(uri1, res1.schemas().get(uri1));
    resolver.registerSchema(uri2, res2.schemas().get(uri2));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc1, resolver, MemberAccessPolicy.standard());
    refIndex.indexTemplate(doc2, resolver, MemberAccessPolicy.standard());

    // Query on name in template1
    Position pos = Position.of(0, 14);
    List<LocationInfo> refs =
        ReferenceProvider.references(
            doc1, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());

    assertEquals(3, refs.size());
    long countUri1 = refs.stream().filter(l -> l.uri().equals(uri1)).count();
    long countUri2 = refs.stream().filter(l -> l.uri().equals(uri2)).count();
    assertEquals(1, countUri1);
    assertEquals(2, countUri2);
  }

  @Test
  @DisplayName("Finds JavaBean method call references")
  void testMethodCallReferences() {
    String uri = "file:///method.vtl";
    String content = "Call: $user.getName() and $user.getName()";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on 'getName' (col 14)
    Position pos = Position.of(0, 14);
    List<LocationInfo> refs =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());

    assertEquals(2, refs.size());
    assertEquals(Range.of(0, 12, 0, 19), refs.get(0).range());
    assertEquals(Range.of(0, 32, 0, 39), refs.get(1).range());
  }

  @Test
  @DisplayName("Finds boolean getter references (isActive -> active)")
  void testBooleanGetterReferences() {
    String uri = "file:///bool.vtl";
    String content = "User is $user.active and again $user.active";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on first 'active' (col 16)
    Position pos = Position.of(0, 16);
    List<LocationInfo> refs =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());

    assertEquals(2, refs.size());
    assertEquals(Range.of(0, 14, 0, 20), refs.get(0).range());
    assertEquals(Range.of(0, 37, 0, 43), refs.get(1).range());
  }

  @Test
  @DisplayName("Finds public field references")
  void testPublicFieldReferences() {
    String uri = "file:///field.vtl";
    String content = "Role: $user.role and $user.role";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    SchemaImportResult res =
        importer.importModel(
            io.github.minh124199.viettemplate.lsp.models.NavFieldUser.class, "user", uri);
    resolver.registerSchema(uri, res.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Position on first 'role' (col 13)
    Position pos = Position.of(0, 13);
    List<LocationInfo> refs =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());

    assertEquals(2, refs.size());
    assertEquals(Range.of(0, 12, 0, 16), refs.get(0).range());
    assertEquals(Range.of(0, 27, 0, 31), refs.get(1).range());
  }

  @Test
  @DisplayName(
      "Multi-module workspace: templates in web-a and web-b share references to model in model/")
  void testMultiModuleWorkspaceReferences(@TempDir Path workspaceRoot) throws IOException {
    Path webA = workspaceRoot.resolve("web-a/src/main/resources/templates");
    Path webB = workspaceRoot.resolve("web-b/src/main/resources/templates");
    Files.createDirectories(webA);
    Files.createDirectories(webB);

    Path tplA = webA.resolve("view-a.vtl");
    Path tplB = webB.resolve("view-b.vtl");
    Files.writeString(tplA, "Module A: $admin.email");
    Files.writeString(tplB, "Module B: $base.email");

    String uriA = tplA.toUri().toString();
    String uriB = tplB.toUri().toString();

    TemplateDocument docA = new TemplateDocument(uriA, 1, "Module A: $admin.email");
    TemplateDocument docB = new TemplateDocument(uriB, 1, "Module B: $base.email");

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();
    // admin is NavAdminUser, base is NavBaseUser. Both resolve to NavBaseUser#getEmail()
    SchemaImportResult resA =
        importer.importModel(
            io.github.minh124199.viettemplate.lsp.models.NavAdminUser.class, "admin", uriA);
    SchemaImportResult resB =
        importer.importModel(
            io.github.minh124199.viettemplate.lsp.models.NavBaseUser.class, "base", uriB);
    resolver.registerSchema(uriA, resA.schemas().get(uriA));
    resolver.registerSchema(uriB, resB.schemas().get(uriB));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(docA, resolver, MemberAccessPolicy.standard());
    refIndex.indexTemplate(docB, resolver, MemberAccessPolicy.standard());

    // Query on email in module A (col 18)
    Position pos = Position.of(0, 18);
    List<LocationInfo> refs =
        ReferenceProvider.references(
            docA, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());

    assertEquals(2, refs.size());
    assertTrue(refs.stream().anyMatch(l -> l.uri().equals(uriA)));
    assertTrue(refs.stream().anyMatch(l -> l.uri().equals(uriB)));
  }

  @Test
  @DisplayName(
      "Schema rebinding migrates symbol key from old to new model without leaving stale entries")
  void testSchemaRebindingMigratesReferences() {
    String uri = "file:///rebind.vtl";
    String content = "User: $user.name";
    TemplateDocument doc = new TemplateDocument(uri, 1, content);

    CanonicalSchemaResolver resolver = new CanonicalSchemaResolver();
    JavaModelSchemaImporter importer = new JavaModelSchemaImporter();

    // 1. Initial schema: $user -> NavUserRecord (record component 'name')
    SchemaImportResult resRecord = importer.importModel(NavUserRecord.class, "user", uri);
    resolver.registerSchema(uri, resRecord.schemas().get(uri));

    WorkspaceSchemaIndex schemaIndex = new WorkspaceSchemaIndex(resolver);
    WorkspaceReferenceIndex refIndex = new WorkspaceReferenceIndex();
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Query on name -> resolves to NavUserRecord#name
    Position pos = Position.of(0, 13);
    List<LocationInfo> initialRefs =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(1, initialRefs.size());

    // Verify key in index is JvmMemberSymbolKey for RECORD_COMPONENT
    WorkspaceSymbolKey recordKey =
        WorkspaceSymbolKey.jvmMember(
            NavUserRecord.class.getName(), JvmMemberSymbolKey.Kind.RECORD_COMPONENT, "name");
    assertEquals(1, refIndex.findReferences(recordKey, false).size());

    // 2. Schema changes: $user -> NavBeanUser (JavaBean getter 'getName')
    SchemaImportResult resBean = importer.importModel(NavBeanUser.class, "user", uri);
    resolver.registerSchema(uri, resBean.schemas().get(uri));

    // Re-index template under updated schema
    refIndex.indexTemplate(doc, resolver, MemberAccessPolicy.standard());

    // Record component key must now have ZERO references (cleanly evicted)
    assertEquals(0, refIndex.findReferences(recordKey, false).size());

    // Getter key must now have 1 reference
    WorkspaceSymbolKey getterKey =
        WorkspaceSymbolKey.jvmMember(
            NavBeanUser.class.getName(), JvmMemberSymbolKey.Kind.GETTER, "getName");
    assertEquals(1, refIndex.findReferences(getterKey, false).size());

    // Query via ReferenceProvider reflects new binding
    List<LocationInfo> updatedRefs =
        ReferenceProvider.references(
            doc, pos, false, resolver, schemaIndex, refIndex, MemberAccessPolicy.standard());
    assertEquals(1, updatedRefs.size());
  }
}
