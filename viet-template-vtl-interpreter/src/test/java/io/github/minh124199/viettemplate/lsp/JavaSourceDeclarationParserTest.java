package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceDeclarationParserTest {

  @Test
  @DisplayName("Parse regular class with JavaBean getter, boolean getter, and public field")
  void testParseClassWithGettersAndFields(@TempDir Path tempDir) throws IOException {
    String code =
        """
        package com.example.model;

        public class Account {
          private String id;
          public String role;

          public String getId() {
            return id;
          }

          public boolean isActive() {
            return true;
          }
        }
        """;
    Path file = tempDir.resolve("Account.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    assertEquals(file, parsed.path());
    assertTrue(parsed.fileSize() > 0);

    JavaSourceDeclaration typeDecl = parsed.typeDeclarations().get("com.example.model.Account");
    assertNotNull(typeDecl);
    assertEquals("Account", typeDecl.name());
    assertEquals(JavaSourceSymbolKey.Kind.TYPE, typeDecl.kind());
    assertEquals(2, typeDecl.identifierRange().start().line());

    List<JavaSourceDeclaration> members =
        parsed.memberDeclarations().get("com.example.model.Account");
    assertNotNull(members);

    // Public field: role
    JavaSourceDeclaration roleField =
        members.stream().filter(m -> m.name().equals("role")).findFirst().orElseThrow();
    assertEquals(JavaSourceSymbolKey.Kind.FIELD, roleField.kind());
    assertEquals(4, roleField.identifierRange().start().line());

    // Getter: getId
    JavaSourceDeclaration getIdMethod =
        members.stream().filter(m -> m.name().equals("getId")).findFirst().orElseThrow();
    assertEquals(JavaSourceSymbolKey.Kind.METHOD, getIdMethod.kind());
    assertEquals(0, getIdMethod.parameterCount());
    assertEquals(6, getIdMethod.identifierRange().start().line());

    // Boolean getter: isActive
    JavaSourceDeclaration isActiveMethod =
        members.stream().filter(m -> m.name().equals("isActive")).findFirst().orElseThrow();
    assertEquals(JavaSourceSymbolKey.Kind.METHOD, isActiveMethod.kind());
    assertEquals(0, isActiveMethod.parameterCount());
    assertEquals(10, isActiveMethod.identifierRange().start().line());
  }

  @Test
  @DisplayName("Parse record with record components and explicit accessor method")
  void testParseRecordWithComponentsAndExplicitAccessor(@TempDir Path tempDir) throws IOException {
    String code =
        """
        package com.example.model;

        public record Person(
            String name,
            int age
        ) {
          public String name() {
            return this.name;
          }
        }
        """;
    Path file = tempDir.resolve("Person.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    JavaSourceDeclaration typeDecl = parsed.typeDeclarations().get("com.example.model.Person");
    assertNotNull(typeDecl);
    assertEquals("Person", typeDecl.name());

    List<JavaSourceDeclaration> members =
        parsed.memberDeclarations().get("com.example.model.Person");
    assertNotNull(members);

    // Record components in header
    List<JavaSourceDeclaration> nameDecls =
        members.stream().filter(m -> m.name().equals("name")).toList();
    assertEquals(2, nameDecls.size(), "Should contain both record component and explicit method");

    JavaSourceDeclaration component =
        nameDecls.stream()
            .filter(m -> m.kind() == JavaSourceSymbolKey.Kind.RECORD_COMPONENT)
            .findFirst()
            .orElseThrow();
    assertEquals(3, component.identifierRange().start().line());

    JavaSourceDeclaration explicitAccessor =
        nameDecls.stream()
            .filter(m -> m.kind() == JavaSourceSymbolKey.Kind.METHOD)
            .findFirst()
            .orElseThrow();
    assertEquals(6, explicitAccessor.identifierRange().start().line());

    JavaSourceDeclaration ageComp =
        members.stream().filter(m -> m.name().equals("age")).findFirst().orElseThrow();
    assertEquals(JavaSourceSymbolKey.Kind.RECORD_COMPONENT, ageComp.kind());
    assertEquals(4, ageComp.identifierRange().start().line());
  }

  @Test
  @DisplayName("Parse interface with methods and enum with constants")
  void testParseInterfaceAndEnum(@TempDir Path tempDir) throws IOException {
    String code =
        """
        package com.example.model;

        public interface StatusHolder {
          String getStatus();
          default boolean isReady() { return true; }
        }

        enum OrderStatus {
          PENDING,
          SHIPPED,
          DELIVERED
        }
        """;
    Path file = tempDir.resolve("StatusHolder.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    assertNotNull(parsed.typeDeclarations().get("com.example.model.StatusHolder"));
    assertNotNull(parsed.typeDeclarations().get("com.example.model.OrderStatus"));

    List<JavaSourceDeclaration> ifaceMembers =
        parsed.memberDeclarations().get("com.example.model.StatusHolder");
    assertTrue(ifaceMembers.stream().anyMatch(m -> m.name().equals("getStatus")));
    assertTrue(ifaceMembers.stream().anyMatch(m -> m.name().equals("isReady")));

    List<JavaSourceDeclaration> enumMembers =
        parsed.memberDeclarations().get("com.example.model.OrderStatus");
    assertTrue(
        enumMembers.stream()
            .anyMatch(
                m -> m.name().equals("PENDING") && m.kind() == JavaSourceSymbolKey.Kind.FIELD));
    assertTrue(
        enumMembers.stream()
            .anyMatch(
                m -> m.name().equals("SHIPPED") && m.kind() == JavaSourceSymbolKey.Kind.FIELD));
  }

  @Test
  @DisplayName("Parse nested classes and records with binary and canonical name resolution")
  void testNestedClassesAndRecords(@TempDir Path tempDir) throws IOException {
    String code =
        """
        package com.example;

        public class Outer {
          public static class InnerClass {
            public String innerField;
          }

          public record InnerRecord(String item) {}
        }
        """;
    Path file = tempDir.resolve("Outer.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    // Both binary and canonical forms
    assertNotNull(parsed.typeDeclarations().get("com.example.Outer$InnerClass"));
    assertNotNull(parsed.typeDeclarations().get("com.example.Outer.InnerClass"));
    assertNotNull(parsed.typeDeclarations().get("com.example.Outer$InnerRecord"));
    assertNotNull(parsed.typeDeclarations().get("com.example.Outer.InnerRecord"));

    List<JavaSourceDeclaration> innerClassMembers =
        parsed.memberDeclarations().get("com.example.Outer$InnerClass");
    assertNotNull(innerClassMembers);
    assertTrue(innerClassMembers.stream().anyMatch(m -> m.name().equals("innerField")));

    List<JavaSourceDeclaration> innerRecordMembers =
        parsed.memberDeclarations().get("com.example.Outer$InnerRecord");
    assertNotNull(innerRecordMembers);
    assertTrue(
        innerRecordMembers.stream()
            .anyMatch(
                m ->
                    m.name().equals("item")
                        && m.kind() == JavaSourceSymbolKey.Kind.RECORD_COMPONENT));
  }

  @Test
  @DisplayName("Parse declarations with annotations containing matching strings and comments")
  void testMultilineAndAnnotations(@TempDir Path tempDir) throws IOException {
    String code =
        """
        package com.example;

        public record Product(
            @SuppressWarnings("product")
            @Deprecated
            String
                product,
            double price
        ) {
          @SuppressWarnings("product")
          public String /* product comment */ product() {
            return "product";
          }
        }
        """;
    Path file = tempDir.resolve("Product.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    List<JavaSourceDeclaration> members = parsed.memberDeclarations().get("com.example.Product");
    assertNotNull(members);

    JavaSourceDeclaration comp =
        members.stream()
            .filter(
                m ->
                    m.name().equals("product")
                        && m.kind() == JavaSourceSymbolKey.Kind.RECORD_COMPONENT)
            .findFirst()
            .orElseThrow();
    // Identifier must be on line 6 ("product,"), NOT line 3 ("@SuppressWarnings(\"product\")")
    assertEquals(6, comp.identifierRange().start().line());

    JavaSourceDeclaration method =
        members.stream()
            .filter(m -> m.name().equals("product") && m.kind() == JavaSourceSymbolKey.Kind.METHOD)
            .findFirst()
            .orElseThrow();
    // Method identifier must be on line 10 ("public String /* product comment */ product()"), NOT
    // line 9
    assertEquals(10, method.identifierRange().start().line());
  }

  @Test
  @DisplayName(
      "Parse with Unicode surrogate pairs and emoji comments preserving UTF-16 coordinates")
  void testCommentsWithEmojisAndSurrogatePairs(@TempDir Path tempDir) throws IOException {
    String code =
        """
        package com.example;

        /**
         * Documentation with emojis: 🚀 🌟 🎉 and surrogate pairs 𝄞
         */
        public class Emojis {
          // Line comment with 🦄
          public String title;
        }
        """;
    Path file = tempDir.resolve("Emojis.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    JavaSourceDeclaration title =
        parsed.memberDeclarations().get("com.example.Emojis").stream()
            .filter(m -> m.name().equals("title"))
            .findFirst()
            .orElseThrow();
    assertEquals(7, title.identifierRange().start().line());
    assertEquals("title", title.name());
  }

  @Test
  @DisplayName("Accurate coordinate resolution on Windows CRLF line endings")
  void testCrlfPositionAccuracy(@TempDir Path tempDir) throws IOException {
    String code =
        "package com.example;\r\n"
            + "\r\n"
            + "public class WindowsStyle {\r\n"
            + "  public String data;\r\n"
            + "}\r\n";
    Path file = tempDir.resolve("WindowsStyle.java");
    Files.writeString(file, code, StandardCharsets.UTF_8);

    ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
    JavaSourceDeclaration type = parsed.typeDeclarations().get("com.example.WindowsStyle");
    assertNotNull(type);
    assertEquals(2, type.identifierRange().start().line());

    JavaSourceDeclaration data =
        parsed.memberDeclarations().get("com.example.WindowsStyle").stream()
            .filter(m -> m.name().equals("data"))
            .findFirst()
            .orElseThrow();
    assertEquals(3, data.identifierRange().start().line());
  }

  @Test
  @DisplayName("Graceful degradation on invalid or unparseable source code")
  void testGracefulFallbackOnSyntaxError(@TempDir Path tempDir) throws IOException {
    String invalidCode = "package com.example; class { missing everything !@#$%^&*()";
    Path file = tempDir.resolve("Broken.java");
    Files.writeString(file, invalidCode, StandardCharsets.UTF_8);

    assertDoesNotThrow(
        () -> {
          ParsedJavaSourceFile parsed = JavaSourceDeclarationParser.parse(file);
          assertNotNull(parsed);
        });
  }
}
