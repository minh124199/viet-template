package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.*;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaResolver;
import io.github.minh124199.viettemplate.schema.SchemaFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossSourceLspParityTest {

  public record JavaUser(String name, String email, int age) {}

  @TempDir Path tempDir;

  private CanonicalSchemaResolver resolver;
  private TemplateLanguageService service;

  private String jsonUri;
  private String tsUri;
  private String contractUri;
  private String javaUri;

  @BeforeEach
  void setUp() throws IOException {
    resolver = new CanonicalSchemaResolver(getClass().getClassLoader());
    service = new TemplateLanguageService(resolver, MemberAccessPolicy.standard());

    // 1. JSON Schema
    Path jsonSchemaFile = tempDir.resolve("user-json.schema.json");
    String jsonContent =
        """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "title": "user-json",
          "type": "object",
          "properties": {
            "user": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "email": { "type": "string" },
                "age": { "type": "integer" }
              },
              "required": ["name", "email", "age"]
            }
          }
        }
        """;
    Files.writeString(jsonSchemaFile, jsonContent);
    resolver.registerSchemaFile(jsonSchemaFile);
    jsonUri = "file:///workspace/user-json.vtl";
    service.schemaIndex().recordDependency(jsonUri, jsonSchemaFile);

    // 2. TypeScript .d.ts
    Path tsFile = tempDir.resolve("user-ts.d.ts");
    String tsContent =
        """
        export interface UserDto {
          name: string;
          email: string;
          age: number;
        }

        export interface TemplateParameters {
          user: UserDto;
        }
        """;
    Files.writeString(tsFile, tsContent);
    resolver.registerSchemaFile(tsFile);
    tsUri = "file:///workspace/user-ts.vtl";
    service.schemaIndex().recordDependency(tsUri, tsFile);

    // 3. Contract (.contract)
    Path contractFile = tempDir.resolve("user-contract.contract");
    String contractContent = "user=" + JavaUser.class.getName() + "\n";
    Files.writeString(contractFile, contractContent);
    resolver.registerSchemaFile(contractFile);
    contractUri = "file:///workspace/user-contract.vtl";
    service.schemaIndex().recordDependency(contractUri, contractFile);

    // 4. Java Model (pure in-memory reflection shape)
    javaUri = "file:///workspace/user-java.vtl";
    CanonicalSchema javaSchema =
        new CanonicalSchema(
            "user-java",
            SchemaFormat.JAVA,
            "fp-java",
            Map.of(
                "user",
                new ParameterDef("user", new ClassTypeRef(JavaUser.class.getName()), false)),
            Map.of(
                JavaUser.class.getName(),
                new TypeDef(
                    JavaUser.class.getName(),
                    "record",
                    Map.of(
                        "name",
                        new PropertyDef("name", new ClassTypeRef("java.lang.String"), false),
                        "email",
                        new PropertyDef("email", new ClassTypeRef("java.lang.String"), false),
                        "age",
                        new PropertyDef("age", new PrimitiveTypeRef("int"), false)))),
            "");
    resolver.registerSchema(javaUri, javaSchema);
  }

  @Test
  @DisplayName("Completion parity across JSON Schema, TypeScript, Contract, and Java Model")
  void testCompletionParity() {
    List<String> uris = List.of(jsonUri, tsUri, contractUri, javaUri);

    for (String uri : uris) {
      service.openDocument(uri, 1, "User: $user.");
      CompletionList comp = service.completion(uri, Position.of(0, 12));
      List<String> labels = comp.items().stream().map(CompletionItem::label).sorted().toList();

      assertTrue(
          labels.containsAll(List.of("age", "email", "name")),
          "Failed on " + uri + ", got: " + labels);
    }
  }

  @Test
  @DisplayName("Hover parity across JSON Schema, TypeScript, Contract, and Java Model")
  void testHoverParity() {
    List<String> uris = List.of(jsonUri, tsUri, contractUri, javaUri);

    for (String uri : uris) {
      service.openDocument(uri, 1, "Name: $user.name");
      // Offset of 'name': index 11
      Optional<HoverInfo> hover = service.hover(uri, Position.of(0, 12));
      assertTrue(hover.isPresent(), "Hover missing on " + uri);
      String md = hover.get().markdown();

      assertTrue(md.contains("**name**"), "Markdown on " + uri + " missing property name: " + md);
      assertTrue(
          md.contains("String") || md.contains("string"),
          "Markdown on " + uri + " missing string type: " + md);
    }
  }

  @Test
  @DisplayName("VTLS:2104 typo diagnostic parity across all four schema sources")
  void testTypoDiagnosticParity() {
    List<String> uris = List.of(jsonUri, tsUri, contractUri, javaUri);

    for (String uri : uris) {
      service.openDocument(uri, 1, "Hello $user.nmae");
      List<Diagnostic> diags = service.diagnostics(uri);

      assertFalse(diags.isEmpty(), "Expected diagnostic on " + uri);
      Diagnostic d = diags.get(0);
      assertEquals("VTLS:2104", d.code().qualifiedCode(), "Mismatch code on " + uri);
      assertTrue(
          d.message().contains("Did you mean 'name'?"),
          "Expected typo suggestion on " + uri + ", got: " + d.message());
    }
  }

  @Test
  @DisplayName(
      "Definition navigation parity: files navigate to source, Java reflection returns empty")
  void testDefinitionNavigationParity() {
    service.openDocument(jsonUri, 1, "User: $user.name");
    List<LocationInfo> jsonDefs = service.definition(jsonUri, Position.of(0, 12));
    assertFalse(jsonDefs.isEmpty());
    assertTrue(jsonDefs.get(0).uri().endsWith(".schema.json"));

    service.openDocument(tsUri, 1, "User: $user.name");
    List<LocationInfo> tsDefs = service.definition(tsUri, Position.of(0, 12));
    assertFalse(tsDefs.isEmpty());
    assertTrue(tsDefs.get(0).uri().endsWith(".d.ts"));

    service.openDocument(contractUri, 1, "User: $user.name");
    List<LocationInfo> contractDefs = service.definition(contractUri, Position.of(0, 12));
    assertFalse(contractDefs.isEmpty());
    assertTrue(contractDefs.get(0).uri().endsWith(".contract"));

    service.openDocument(javaUri, 1, "User: $user.name");
    List<LocationInfo> javaDefs = service.definition(javaUri, Position.of(0, 12));
    assertTrue(
        javaDefs.isEmpty(),
        "Java reflection without source must return empty list, got: " + javaDefs);
  }
}
