package io.github.minh124199.viettemplate.lsp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Standard stream transport implementing the Language Server Protocol (LSP) base protocol framing.
 *
 * <p>Messages are framed with headers terminated by {@code \r\n\r\n}, with {@code Content-Length:
 * <n>} specifying the exact byte length of the following UTF-8 payload.
 */
final class LspStreamTransport {

  private static final String CONTENT_LENGTH_PREFIX = "content-length:";
  private static final byte[] HEADER_TERMINATOR = new byte[] {'\r', '\n', '\r', '\n'};

  private LspStreamTransport() {}

  /**
   * Reads a single framed JSON-RPC message from the input stream.
   *
   * @param in the input stream to read from
   * @return the message payload as string, or {@code null} if the stream reached EOF before a new
   *     message
   * @throws IOException if an I/O error or framing violation occurs
   */
  static String readMessage(InputStream in) throws IOException {
    Objects.requireNonNull(in, "in must not be null");

    int contentLength = -1;
    StringBuilder headerLine = new StringBuilder();
    boolean inHeader = true;

    while (inHeader) {
      int b = in.read();
      if (b == -1) {
        if (headerLine.isEmpty() && contentLength == -1) {
          return null; // Clean EOF
        }
        throw new EOFException("Unexpected EOF while reading LSP message headers");
      }

      if (b == '\n') {
        String line = headerLine.toString().trim();
        headerLine.setLength(0);

        if (line.isEmpty()) {
          // Empty line indicates end of headers
          inHeader = false;
        } else {
          String lower = line.toLowerCase(Locale.ROOT);
          if (lower.startsWith(CONTENT_LENGTH_PREFIX)) {
            String valueStr = line.substring(CONTENT_LENGTH_PREFIX.length()).trim();
            try {
              contentLength = Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
              throw new IOException("Invalid Content-Length value: " + valueStr, e);
            }
          }
        }
      } else if (b != '\r') {
        headerLine.append((char) b);
      }
    }

    if (contentLength < 0) {
      throw new IOException("Missing or invalid Content-Length header in LSP message");
    }

    byte[] body = in.readNBytes(contentLength);
    if (body.length < contentLength) {
      throw new EOFException(
          "Expected " + contentLength + " bytes, but only received " + body.length);
    }

    return new String(body, StandardCharsets.UTF_8);
  }

  /**
   * Writes a single framed JSON-RPC message to the output stream.
   *
   * @param out the output stream to write to
   * @param json the JSON-RPC payload string
   * @throws IOException if an I/O error occurs
   */
  static void writeMessage(OutputStream out, String json) throws IOException {
    Objects.requireNonNull(out, "out must not be null");
    Objects.requireNonNull(json, "json must not be null");

    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    String header = "Content-Length: " + body.length + "\r\n\r\n";
    byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);

    synchronized (out) {
      out.write(headerBytes);
      out.write(body);
      out.flush();
    }
  }
}
