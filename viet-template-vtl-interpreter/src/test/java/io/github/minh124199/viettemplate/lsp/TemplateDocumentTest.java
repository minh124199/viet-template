package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import io.github.minh124199.viettemplate.api.SourceSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateDocumentTest {

  @Test
  @DisplayName("Position to offset and back on LF line endings")
  void testLfPositionOffsetMapping() {
    String text = "hello\nworld\nvietnam";
    TemplateDocument doc = new TemplateDocument("file:///test.vt", 1, text);

    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(5, doc.positionToOffset(Position.of(0, 5)));
    assertEquals(6, doc.positionToOffset(Position.of(1, 0)));
    assertEquals(11, doc.positionToOffset(Position.of(1, 5)));
    assertEquals(12, doc.positionToOffset(Position.of(2, 0)));
    assertEquals(19, doc.positionToOffset(Position.of(2, 7)));

    assertEquals(Position.of(0, 0), doc.offsetToPosition(0));
    assertEquals(Position.of(0, 5), doc.offsetToPosition(5));
    assertEquals(Position.of(1, 0), doc.offsetToPosition(6));
    assertEquals(Position.of(1, 5), doc.offsetToPosition(11));
    assertEquals(Position.of(2, 0), doc.offsetToPosition(12));
    assertEquals(Position.of(2, 7), doc.offsetToPosition(19));
  }

  @Test
  @DisplayName("Position to offset and back on CRLF line endings")
  void testCrlfPositionOffsetMapping() {
    String text = "hello\r\nworld\r\n";
    TemplateDocument doc = new TemplateDocument("file:///test-crlf.vt", 1, text);

    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(5, doc.positionToOffset(Position.of(0, 5)));
    // Line 1 starts after \r\n (offset 7)
    assertEquals(7, doc.positionToOffset(Position.of(1, 0)));
    assertEquals(12, doc.positionToOffset(Position.of(1, 5)));

    assertEquals(Position.of(0, 0), doc.offsetToPosition(0));
    assertEquals(Position.of(0, 5), doc.offsetToPosition(5));
    assertEquals(Position.of(1, 0), doc.offsetToPosition(7));
    assertEquals(Position.of(1, 5), doc.offsetToPosition(12));
  }

  @Test
  @DisplayName("Position to offset and back on CR line endings")
  void testCrPositionOffsetMapping() {
    String text = "line1\rline2";
    TemplateDocument doc = new TemplateDocument("file:///test-cr.vt", 1, text);

    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(6, doc.positionToOffset(Position.of(1, 0)));
    assertEquals(11, doc.positionToOffset(Position.of(1, 5)));

    assertEquals(Position.of(0, 0), doc.offsetToPosition(0));
    assertEquals(Position.of(1, 0), doc.offsetToPosition(6));
  }

  @Test
  @DisplayName("Empty document edge cases")
  void testEmptyDocument() {
    TemplateDocument doc = new TemplateDocument("file:///empty.vt", 1, "");

    assertEquals(0, doc.positionToOffset(Position.of(0, 0)));
    assertEquals(0, doc.positionToOffset(Position.of(5, 5))); // Clamped
    assertEquals(Position.of(0, 0), doc.offsetToPosition(0));
    assertEquals(Position.of(0, 0), doc.offsetToPosition(100)); // Clamped
  }

  @Test
  @DisplayName("Unicode surrogate pairs and Vietnamese multi-byte character mapping")
  void testUnicodeAndSurrogatePairs() {
    // "Xin chào 🇻🇳 🚀"
    // 'X','i','n',' ','c','h','à','o',' '
    // 🚀 is U+1F680 -> 2 UTF-16 code units (surrogate pair)
    String text = "Xin chào 🚀 thế giới\nChào bạn";
    TemplateDocument doc = new TemplateDocument("file:///unicode.vt", 1, text);

    // 🚀 starts at index 9. Length of surrogate is 2 chars.
    int rocketOffset = text.indexOf("🚀");
    assertEquals(Position.of(0, rocketOffset), doc.offsetToPosition(rocketOffset));
    assertEquals(Position.of(0, rocketOffset + 2), doc.offsetToPosition(rocketOffset + 2));

    // Next line offset
    int line2Offset = text.indexOf("Chào bạn");
    Position line2Pos = doc.offsetToPosition(line2Offset);
    assertEquals(1, line2Pos.line());
    assertEquals(0, line2Pos.character());
  }

  @Test
  @DisplayName("SourceSpan to Range conversion")
  void testSourceSpanToRange() {
    String text = "Hello\nWorld";
    TemplateDocument doc = new TemplateDocument("file:///span.vt", 1, text);

    // Span covering 'World' (offset 6 to 11, line 2, col 1 to 6 in 1-based)
    SourceSpan span = SourceSpan.of(6, 11, 2, 1, 2, 6);
    Range range = doc.spanToRange(span);

    assertEquals(Position.of(1, 0), range.start());
    assertEquals(Position.of(1, 5), range.end());
  }

  @Test
  @DisplayName("Document update creates new instance with updated version and text")
  void testDocumentUpdate() {
    TemplateDocument doc = new TemplateDocument("file:///doc.vt", 1, "v1 text");
    TemplateDocument updated = doc.withContent(2, "v2 text updated");

    assertEquals(1, doc.version());
    assertEquals("v1 text", doc.text());

    assertEquals(2, updated.version());
    assertEquals("v2 text updated", updated.text());
    assertEquals(doc.uri(), updated.uri());
  }

  @Test
  @DisplayName("Negative coordinate validation")
  void testNegativeCoordinates() {
    assertThrows(IllegalArgumentException.class, () -> Position.of(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> Position.of(0, -1));
  }
}
