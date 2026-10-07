package io.github.minh124199.viettemplate.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.CanonicalSchema;
import io.github.minh124199.viettemplate.schema.CanonicalSchemaModel.ParameterDef;
import io.github.minh124199.viettemplate.schema.JsonSchemaImporter.JsonArrayNode;
import io.github.minh124199.viettemplate.schema.JsonSchemaImporter.JsonNode;
import io.github.minh124199.viettemplate.schema.JsonSchemaImporter.JsonNumberNode;
import io.github.minh124199.viettemplate.schema.JsonSchemaImporter.JsonObjectNode;
import io.github.minh124199.viettemplate.schema.JsonSchemaImporter.JsonParseException;
import io.github.minh124199.viettemplate.schema.JsonSchemaImporter.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonSchemaParserHardeningTest {

  private final JsonSchemaImporter importer = new JsonSchemaImporter();

  // 1. Escaped quotation marks
  @Test
  @DisplayName("1. Escaped quotation marks are correctly unescaped")
  void test01_EscapedQuotationMarks() throws Exception {
    String json = "{\"text\": \"hello \\\"world\\\"\"}";
    JsonNode node = new JsonParser(json).parse();
    assertThat(node).isInstanceOf(JsonObjectNode.class);
    JsonObjectNode obj = (JsonObjectNode) node;
    assertThat(obj.getString("text")).isEqualTo("hello \"world\"");
  }

  // 2. Escaped backslashes
  @Test
  @DisplayName("2. Escaped backslashes are correctly unescaped")
  void test02_EscapedBackslashes() throws Exception {
    String json = "{\"path\": \"C:\\\\Program Files\\\\App\"}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(obj.getString("path")).isEqualTo("C:\\Program Files\\App");
  }

  // 3. \n, \r, \t
  @Test
  @DisplayName("3. Standard control escapes (\\n, \\r, \\t, \\b, \\f, \\/) are parsed")
  void test03_ControlEscapes() throws Exception {
    String json = "{\"escapes\": \"line1\\nline2\\rline3\\ttab\\bback\\fform\\/slash\"}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(obj.getString("escapes")).isEqualTo("line1\nline2\rline3\ttab\bback\fform/slash");
  }

  // 4. Unicode escapes
  @Test
  @DisplayName("4. 4-digit hexadecimal Unicode escape sequences are correctly converted")
  void test04_UnicodeEscapes() throws Exception {
    String json = "{\"greeting\": \"\\u0048\\u0065\\u006c\\u006c\\u006f\\u0020\\u00e9\"}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(obj.getString("greeting")).isEqualTo("Hello é");
  }

  // 5. Surrogate pairs
  @Test
  @DisplayName("5. Unicode surrogate pairs representing astral plane code points decode accurately")
  void test05_SurrogatePairs() throws Exception {
    String json = "{\"emoji\": \"\\uD83D\\uDE00\"}"; // 😀 U+1F600
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    String emoji = obj.getString("emoji");
    assertThat(emoji).isEqualTo("\uD83D\uDE00");
    assertThat(emoji.codePointAt(0)).isEqualTo(0x1F600);
  }

  // 6. Empty object
  @Test
  @DisplayName("6. Empty object {} parses cleanly")
  void test06_EmptyObject() throws Exception {
    String json = "{}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(obj.properties()).isEmpty();
  }

  // 7. Empty array
  @Test
  @DisplayName("7. Empty array [] parses cleanly")
  void test07_EmptyArray() throws Exception {
    String json = "[]";
    JsonArrayNode arr = (JsonArrayNode) new JsonParser(json).parse();
    assertThat(arr.elements()).isEmpty();
  }

  // 8. Deeply nested objects (within bound)
  @Test
  @DisplayName("8. Deeply nested objects within bound (depth 200 <= 256) parse without error")
  void test08_DeeplyNestedObjectsWithinBound() throws Exception {
    int depth = 200;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append("{\"child\":");
    }
    sb.append("\"leaf\"");
    for (int i = 0; i < depth; i++) {
      sb.append("}");
    }
    JsonNode root = new JsonParser(sb.toString()).parse();
    assertThat(root).isInstanceOf(JsonObjectNode.class);
  }

  // 9. Deeply nested arrays (within bound)
  @Test
  @DisplayName("9. Deeply nested arrays within bound (depth 200 <= 256) parse without error")
  void test09_DeeplyNestedArraysWithinBound() throws Exception {
    int depth = 200;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append("[");
    }
    sb.append("42");
    for (int i = 0; i < depth; i++) {
      sb.append("]");
    }
    JsonNode root = new JsonParser(sb.toString()).parse();
    assertThat(root).isInstanceOf(JsonArrayNode.class);
  }

  // 10. Integer numbers
  @Test
  @DisplayName("10. Integer numbers of various magnitudes parse correctly")
  void test10_IntegerNumbers() throws Exception {
    String json = "{\"zero\": 0, \"positive\": 42, \"large\": 9223372036854775807}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(((JsonNumberNode) obj.get("zero")).value().longValue()).isEqualTo(0L);
    assertThat(((JsonNumberNode) obj.get("positive")).value().longValue()).isEqualTo(42L);
    assertThat(((JsonNumberNode) obj.get("large")).value().longValue()).isEqualTo(Long.MAX_VALUE);
  }

  // 11. Decimal numbers
  @Test
  @DisplayName("11. Decimal numbers parse as floating-point values")
  void test11_DecimalNumbers() throws Exception {
    String json = "{\"pi\": 3.14159, \"fraction\": 0.001, \"negative\": -0.5}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(((JsonNumberNode) obj.get("pi")).value().doubleValue()).isEqualTo(3.14159);
    assertThat(((JsonNumberNode) obj.get("fraction")).value().doubleValue()).isEqualTo(0.001);
    assertThat(((JsonNumberNode) obj.get("negative")).value().doubleValue()).isEqualTo(-0.5);
  }

  // 12. Exponent numbers
  @Test
  @DisplayName("12. Scientific exponent numbers parse accurately")
  void test12_ExponentNumbers() throws Exception {
    String json = "{\"exp1\": 1e5, \"exp2\": 2.5E-3, \"exp3\": -1.2e+4, \"zeroExp\": 0e0}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(((JsonNumberNode) obj.get("exp1")).value().doubleValue()).isEqualTo(100000.0);
    assertThat(((JsonNumberNode) obj.get("exp2")).value().doubleValue()).isEqualTo(0.0025);
    assertThat(((JsonNumberNode) obj.get("exp3")).value().doubleValue()).isEqualTo(-12000.0);
    assertThat(((JsonNumberNode) obj.get("zeroExp")).value().doubleValue()).isEqualTo(0.0);
  }

  // 13. Negative numbers
  @Test
  @DisplayName("13. Negative integers, decimals, and negative zero parse accurately")
  void test13_NegativeNumbers() throws Exception {
    String json = "{\"negInt\": -42, \"negZero\": -0, \"negFloat\": -3.14}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(((JsonNumberNode) obj.get("negInt")).value().longValue()).isEqualTo(-42L);
    assertThat(((JsonNumberNode) obj.get("negZero")).value().longValue()).isEqualTo(0L);
    assertThat(((JsonNumberNode) obj.get("negFloat")).value().doubleValue()).isEqualTo(-3.14);
  }

  // 14. Malformed numbers (leading zeros, unclosed decimal, bare minus, etc.)
  @Test
  @DisplayName("14. Malformed numbers strictly throw JsonParseException per RFC 8259")
  void test14_MalformedNumbers() {
    // Leading zeros forbidden
    assertThatThrownBy(() -> new JsonParser("{\"a\": 01}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Leading zeros");

    assertThatThrownBy(() -> new JsonParser("{\"a\": -01}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Leading zeros");

    assertThatThrownBy(() -> new JsonParser("{\"a\": 00}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Leading zeros");

    // Unclosed decimal point
    assertThatThrownBy(() -> new JsonParser("{\"a\": 1.}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Decimal point must be followed by digits");

    // Bare minus
    assertThatThrownBy(() -> new JsonParser("{\"a\": -}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid number format");

    // Incomplete exponent
    assertThatThrownBy(() -> new JsonParser("{\"a\": 1e}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Exponent must be followed by digits");

    assertThatThrownBy(() -> new JsonParser("{\"a\": 1e+}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Exponent must be followed by digits");
  }

  // 15. Trailing commas
  @Test
  @DisplayName("15. Trailing commas in object and array throw JsonParseException")
  void test15_TrailingCommas() {
    assertThatThrownBy(() -> new JsonParser("{\"a\": 1,}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Trailing comma in object");

    assertThatThrownBy(() -> new JsonParser("[1, 2,]").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Trailing comma in array");
  }

  // 16. Missing commas
  @Test
  @DisplayName("16. Missing commas in object and array throw JsonParseException")
  void test16_MissingCommas() {
    assertThatThrownBy(() -> new JsonParser("{\"a\": 1 \"b\": 2}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Expected ',' or '}'");

    assertThatThrownBy(() -> new JsonParser("[1 2]").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Expected ',' or ']'");
  }

  // 17. Missing colons
  @Test
  @DisplayName("17. Missing colons in object throw JsonParseException")
  void test17_MissingColons() {
    assertThatThrownBy(() -> new JsonParser("{\"a\" 1}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Expected ':'");
  }

  // 18. Unterminated strings
  @Test
  @DisplayName("18. Unterminated strings throw JsonParseException")
  void test18_UnterminatedStrings() {
    assertThatThrownBy(() -> new JsonParser("{\"key\": \"unclosed}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Unterminated string literal");
  }

  // 19. Invalid escape sequences
  @Test
  @DisplayName("19. Invalid escape sequences throw JsonParseException")
  void test19_InvalidEscapeSequences() {
    assertThatThrownBy(() -> new JsonParser("{\"a\": \"\\x\"}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid escape character: \\x");

    assertThatThrownBy(() -> new JsonParser("{\"a\": \"\\u12").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid unicode escape sequence");

    assertThatThrownBy(() -> new JsonParser("{\"a\": \"\\u12G4\"}").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid hex in unicode escape");

    assertThatThrownBy(() -> new JsonParser("{\"a\": \"test\\").parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Unterminated escape sequence");
  }

  // 20. Duplicate keys behavior (last-one-wins deterministic)
  @Test
  @DisplayName("20. Duplicate keys in objects exhibit deterministic last-one-wins behavior")
  void test20_DuplicateKeysDeterministic() throws Exception {
    String json = "{\"val\": 1, \"val\": 2, \"val\": 3}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(json).parse();
    assertThat(((JsonNumberNode) obj.get("val")).value().longValue()).isEqualTo(3L);

    String schemaJson =
        """
        {
          "type": "object",
          "properties": {
            "name": { "type": "string", "description": "first" },
            "name": { "type": "string", "description": "second" }
          }
        }
        """;
    SchemaImportResult res = importer.importString(schemaJson, "dup-test");
    assertThat(res.hasErrors()).isFalse();
    CanonicalSchema schema = res.schemas().get("dup-test");
    ParameterDef param = schema.parameters().get("name");
    assertThat(param.documentation()).isEqualTo("second");
  }

  // 21. BOM handling
  @Test
  @DisplayName("21. UTF-8 BOM (\\uFEFF) at document start is skipped without skewing location")
  void test21_BomHandling() throws Exception {
    String jsonWithBom = "\uFEFF{\"title\": \"WithBOM\", \"type\": \"object\"}";
    JsonNode node = new JsonParser(jsonWithBom).parse();
    assertThat(node).isInstanceOf(JsonObjectNode.class);
    JsonObjectNode obj = (JsonObjectNode) node;
    assertThat(obj.getString("title")).isEqualTo("WithBOM");
    // Position of root object should be line 1, column 1
    assertThat(obj.location().line()).isEqualTo(1);
    assertThat(obj.location().column()).isEqualTo(1);

    SchemaImportResult res = importer.importString(jsonWithBom, "bom-schema");
    assertThat(res.hasErrors()).isFalse();
    assertThat(res.schemas()).containsKey("bom-schema");
  }

  // 22. CRLF line tracking
  @Test
  @DisplayName("22. CRLF line breaks are correctly tracked for line numbers")
  void test22_CrlfLineTracking() throws Exception {
    String crlfJson = "{\r\n  \"key1\": 10,\r\n  \"key2\": 20\r\n}";
    JsonObjectNode obj = (JsonObjectNode) new JsonParser(crlfJson).parse();
    assertThat(obj.getPropertyLocation("key1").line()).isEqualTo(2);
    assertThat(obj.getPropertyLocation("key2").line()).isEqualTo(3);
  }

  // 23. Line/column accuracy
  @Test
  @DisplayName("23. Syntax errors report precise line and column coordinates")
  void test23_LineColumnAccuracy() {
    String json = "{\n  \"first\": 1,\n  \"bad\": 01\n}";
    assertThatThrownBy(() -> new JsonParser(json).parse())
        .isInstanceOfSatisfying(
            JsonParseException.class,
            ex -> {
              assertThat(ex.location.line()).isEqualTo(3);
              assertThat(ex.location.column()).isEqualTo(10);
            });
  }

  // 24. Oversized/deep input behavior (exceeding MAX_NESTING_DEPTH emits structured error, no
  // StackOverflowError)
  @Test
  @DisplayName(
      "24. Nesting depth exceeding 256 emits structured JsonParseException and no"
          + " StackOverflowError")
  void test24_OversizedDeepInputBehavior() {
    int depth = 257;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      sb.append("[");
    }
    sb.append("1");
    for (int i = 0; i < depth; i++) {
      sb.append("]");
    }
    String deepJson = sb.toString();

    // Direct parser check: throws JsonParseException, NOT StackOverflowError
    assertThatThrownBy(() -> new JsonParser(deepJson).parse())
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Exceeded maximum nesting depth of 256");

    // Importer integration check: reports structured error diagnostic
    SchemaImportResult result = importer.importString(deepJson, "deep-test");
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.diagnostics())
        .anyMatch(
            d ->
                d.code().equals(JsonSchemaImporter.CODE_SYNTAX_ERROR)
                    && d.severity() == DiagnosticSeverity.ERROR
                    && d.message().contains("Exceeded maximum nesting depth"));
  }

  // 25. Deterministic diagnostics
  @Test
  @DisplayName("25. Identical malformed input produces deterministic diagnostics")
  void test25_DeterministicDiagnostics() {
    String malformed = "{\n  \"prop\": 099\n}";
    SchemaImportResult res1 = importer.importString(malformed, "det-diag-1");
    SchemaImportResult res2 = importer.importString(malformed, "det-diag-2");

    assertThat(res1.diagnostics()).hasSize(1);
    assertThat(res2.diagnostics()).hasSize(1);

    var d1 = res1.diagnostics().get(0);
    var d2 = res2.diagnostics().get(0);

    assertThat(d1.code()).isEqualTo(d2.code());
    assertThat(d1.message()).isEqualTo(d2.message());
    assertThat(d1.line()).isEqualTo(d2.line());
    assertThat(d1.column()).isEqualTo(d2.column());
    assertThat(d1.severity()).isEqualTo(d2.severity());
  }
}
