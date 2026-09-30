package io.github.minh124199.viettemplate.lsp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LspStreamTransportTest {

  @Test
  @DisplayName("Read single framed LSP message")
  void testReadSingleMessage() throws IOException {
    String payload = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"shutdown\"}";
    byte[] body = payload.getBytes(StandardCharsets.UTF_8);
    String frame = "Content-Length: " + body.length + "\r\n\r\n" + payload;

    ByteArrayInputStream in = new ByteArrayInputStream(frame.getBytes(StandardCharsets.UTF_8));
    String message = LspStreamTransport.readMessage(in);

    assertEquals(payload, message);
  }

  @Test
  @DisplayName("Read consecutive framed LSP messages")
  void testReadConsecutiveMessages() throws IOException {
    String payload1 = "{\"id\":1}";
    String payload2 = "{\"id\":2}";

    String stream =
        "Content-Length: "
            + payload1.getBytes(StandardCharsets.UTF_8).length
            + "\r\n\r\n"
            + payload1
            + "Content-Length: "
            + payload2.getBytes(StandardCharsets.UTF_8).length
            + "\r\n\r\n"
            + payload2;

    ByteArrayInputStream in = new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8));
    assertEquals(payload1, LspStreamTransport.readMessage(in));
    assertEquals(payload2, LspStreamTransport.readMessage(in));
    assertNull(LspStreamTransport.readMessage(in)); // Clean EOF
  }

  @Test
  @DisplayName("Handle additional headers like Content-Type")
  void testAdditionalHeaders() throws IOException {
    String payload = "{\"id\":1}";
    byte[] body = payload.getBytes(StandardCharsets.UTF_8);
    String frame =
        "Content-Length: "
            + body.length
            + "\r\nContent-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n"
            + payload;

    ByteArrayInputStream in = new ByteArrayInputStream(frame.getBytes(StandardCharsets.UTF_8));
    assertEquals(payload, LspStreamTransport.readMessage(in));
  }

  @Test
  @DisplayName("Write framed message and verify header length and formatting")
  void testWriteMessage() throws IOException {
    String payload = "{\"result\":\"ok\"}";
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    LspStreamTransport.writeMessage(out, payload);

    String expected =
        "Content-Length: " + payload.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + payload;
    assertEquals(expected, out.toString(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Unexpected EOF in payload throws EOFException")
  void testUnexpectedEofInPayload() {
    String truncated = "Content-Length: 50\r\n\r\nshort";
    ByteArrayInputStream in = new ByteArrayInputStream(truncated.getBytes(StandardCharsets.UTF_8));

    assertThrows(EOFException.class, () -> LspStreamTransport.readMessage(in));
  }

  @Test
  @DisplayName("Missing Content-Length throws IOException")
  void testMissingContentLength() {
    String noHeader = "Custom-Header: value\r\n\r\nbody";
    ByteArrayInputStream in = new ByteArrayInputStream(noHeader.getBytes(StandardCharsets.UTF_8));

    assertThrows(IOException.class, () -> LspStreamTransport.readMessage(in));
  }
}
