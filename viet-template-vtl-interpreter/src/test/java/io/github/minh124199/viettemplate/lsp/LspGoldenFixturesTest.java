package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspGoldenFixturesTest {

  private TemplateLanguageService service;
  private CanonicalSchemaResolver schemaResolver;

  @BeforeEach
  void setUp() {
    schemaResolver = new CanonicalSchemaResolver();
    service = new TemplateLanguageService(schemaResolver, MemberAccessPolicy.standard());
  }

  private String loadResource(String path) {
    try (InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource: " + path, e);
    }
  }

  @Test
  @DisplayName("Golden Fixture: simple-user.vt")
  void testSimpleUserFixture() {
    String uri = "file:///fixtures/simple-user.vt";
    String templateText = loadResource("/golden-lsp/simple-user.vt");
    String schemaText = loadResource("/golden-lsp/simple-user.vt-schema.json");

    service.registerSchema(uri, schemaText);
    service.openDocument(uri, 1, templateText);

    // 1. Diagnostics: clean template has 0 errors
    List<Diagnostic> diags = service.diagnostics(uri);
    assertTrue(diags.isEmpty(), "Expected 0 diagnostics for simple-user.vt, got: " + diags);

    // 2. Completion: at '$' (offset 7)
    TemplateDocument doc = service.getDocument(uri).orElseThrow();
    int dollarOffset = templateText.indexOf("${username}") + 1; // position of '$'
    Position pos = doc.offsetToPosition(dollarOffset);
    CompletionList completions = service.completion(uri, pos);

    List<CompletionItem> items = completions.items();
    assertTrue(items.stream().anyMatch(i -> i.label().equals("active")));
    assertTrue(items.stream().anyMatch(i -> i.label().equals("id")));
    assertTrue(items.stream().anyMatch(i -> i.label().equals("username")));

    // 3. Hover: on 'username'
    int usernameOffset = templateText.indexOf("username");
    Optional<HoverInfo> hover = service.hover(uri, doc.offsetToPosition(usernameOffset));
    assertTrue(hover.isPresent());
    assertTrue(hover.get().markdown().contains("**$username**: `String`"));

    // 4. Definition: on 'username' resolves to schema
    List<LocationInfo> defs = service.definition(uri, doc.offsetToPosition(usernameOffset));
    assertFalse(defs.isEmpty());
  }

  @Test
  @DisplayName("Golden Fixture: nested-member.vt")
  void testNestedMemberFixture() {
    String uri = "file:///fixtures/nested-member.vt";
    String templateText = loadResource("/golden-lsp/nested-member.vt");
    String schemaText = loadResource("/golden-lsp/nested-member.vt-schema.json");

    service.registerSchema(uri, schemaText);
    service.openDocument(uri, 1, templateText);

    // Clean template
    List<Diagnostic> diags = service.diagnostics(uri);
    assertTrue(diags.isEmpty(), "Expected 0 diagnostics for nested-member.vt, got: " + diags);

    // Member completion on customer.
    TemplateDocument doc = service.getDocument(uri).orElseThrow();
    int dotOffset = templateText.indexOf("customer.") + "customer.".length();
    CompletionList compCustomer = service.completion(uri, doc.offsetToPosition(dotOffset));
    assertTrue(compCustomer.items().stream().anyMatch(i -> i.label().equals("address")));
    assertTrue(compCustomer.items().stream().anyMatch(i -> i.label().equals("profile")));

    // Deep member completion on customer.profile.
    int profileDotOffset = templateText.indexOf("customer.profile.") + "customer.profile.".length();
    CompletionList compProfile = service.completion(uri, doc.offsetToPosition(profileDotOffset));
    assertTrue(compProfile.items().stream().anyMatch(i -> i.label().equals("bio")));
    assertTrue(compProfile.items().stream().anyMatch(i -> i.label().equals("displayName")));

    // Hover on displayName
    int nameOffset = templateText.indexOf("displayName");
    Optional<HoverInfo> hoverName = service.hover(uri, doc.offsetToPosition(nameOffset));
    assertTrue(hoverName.isPresent());
    assertTrue(hoverName.get().markdown().contains("**displayName**: `String`"));
  }

  @Test
  @DisplayName("Golden Fixture: unicode-template.vt")
  void testUnicodeTemplateFixture() {
    String uri = "file:///fixtures/unicode-template.vt";
    String templateText = loadResource("/golden-lsp/unicode-template.vt");
    String schemaText = loadResource("/golden-lsp/unicode-template.vt-schema.json");

    service.registerSchema(uri, schemaText);
    service.openDocument(uri, 1, templateText);

    List<Diagnostic> diags = service.diagnostics(uri);
    assertTrue(diags.isEmpty(), "Expected 0 diagnostics for unicode-template.vt, got: " + diags);

    TemplateDocument doc = service.getDocument(uri).orElseThrow();

    // Verify Vietnamese multi-byte offsets
    int hoTenOffset = templateText.indexOf("hoTen");
    Position hoTenPos = doc.offsetToPosition(hoTenOffset);
    assertEquals(0, hoTenPos.line());

    Optional<HoverInfo> hover = service.hover(uri, hoTenPos);
    assertTrue(hover.isPresent());
    assertTrue(hover.get().markdown().contains("**hoTen**: `String`"));

    // Verify rocket emoji line position mapping
    int rocketOffset = templateText.indexOf("🚀");
    Position rocketPos = doc.offsetToPosition(rocketOffset);
    assertEquals(1, rocketPos.line());
    assertEquals(rocketOffset, doc.positionToOffset(rocketPos));
  }

  @Test
  @DisplayName("Golden Fixture: diagnostic-template.vt")
  void testDiagnosticTemplateFixture() {
    String uri = "file:///fixtures/diagnostic-template.vt";
    String templateText = loadResource("/golden-lsp/diagnostic-template.vt");
    String schemaText = loadResource("/golden-lsp/diagnostic-template.vt-schema.json");

    service.registerSchema(uri, schemaText);
    service.openDocument(uri, 1, templateText);

    List<Diagnostic> diags = service.diagnostics(uri);
    assertEquals(4, diags.size(), "Expected exactly 4 diagnostics: " + diags);

    // 1. Line 1: $undeclaredRoot -> VTLS:2101
    Diagnostic d1 = diags.get(0);
    assertEquals("VTLS:2101", d1.code().qualifiedCode());
    assertEquals(DiagnosticSeverity.ERROR, d1.severity());
    assertEquals(2, d1.primarySpan().startLine());

    // 2. Line 2: ${user.nonexistent} -> VTLS:2104
    Diagnostic d2 = diags.get(1);
    assertEquals("VTLS:2104", d2.code().qualifiedCode());
    assertEquals(DiagnosticSeverity.ERROR, d2.severity());
    assertEquals(3, d2.primarySpan().startLine());

    // 3. Line 3: ${user.class} -> VTLSEC:2401
    Diagnostic d3 = diags.get(2);
    assertEquals("VTLSEC:2401", d3.code().qualifiedCode());
    assertEquals(DiagnosticSeverity.ERROR, d3.severity());
    assertEquals(4, d3.primarySpan().startLine());

    // 4. Line 4: ${nullableUser.name} -> VTLS:2107
    Diagnostic d4 = diags.get(3);
    assertEquals("VTLS:2107", d4.code().qualifiedCode());
    assertEquals(DiagnosticSeverity.WARNING, d4.severity());
    assertEquals(5, d4.primarySpan().startLine());
  }

  @Test
  @DisplayName("Golden Fixture: recursive-model.vt")
  void testRecursiveModelFixture() {
    String uri = "file:///fixtures/recursive-model.vt";
    String templateText = loadResource("/golden-lsp/recursive-model.vt");
    String schemaText = loadResource("/golden-lsp/recursive-model.vt-schema.json");

    service.registerSchema(uri, schemaText);
    service.openDocument(uri, 1, templateText);

    List<Diagnostic> diags = service.diagnostics(uri);
    assertTrue(diags.isEmpty(), "Expected 0 diagnostics for recursive-model.vt, got: " + diags);

    TemplateDocument doc = service.getDocument(uri).orElseThrow();

    // Verify chained recursive completion without stack overflow
    int deepOffset = templateText.indexOf("root.next.next.") + "root.next.next.".length();
    CompletionList comp = service.completion(uri, doc.offsetToPosition(deepOffset));
    assertTrue(comp.items().stream().anyMatch(i -> i.label().equals("next")));
    assertTrue(comp.items().stream().anyMatch(i -> i.label().equals("value")));

    // Hover on value
    int valOffset = templateText.indexOf("value");
    Optional<HoverInfo> hover = service.hover(uri, doc.offsetToPosition(valOffset));
    assertTrue(hover.isPresent());
    assertTrue(hover.get().markdown().contains("**value**: `String`"));
  }
}
