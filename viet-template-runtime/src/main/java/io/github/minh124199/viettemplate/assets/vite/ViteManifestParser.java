package io.github.minh124199.viettemplate.assets.vite;

import io.github.minh124199.viettemplate.assets.AssetDiagnosticCode;
import io.github.minh124199.viettemplate.assets.AssetException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Robust, zero-dependency parser for Vite production {@code manifest.json} files.
 *
 * <p>Parses manifest entries into {@link ViteRawEntry} structures while tolerating unknown or
 * forward-compatible JSON fields.
 */
final class ViteManifestParser {

  private final String json;
  private int pos;
  private final int length;

  private ViteManifestParser(String json) {
    this.json = json != null ? json : "";
    this.length = this.json.length();
    this.pos = 0;
  }

  public static Map<String, ViteRawEntry> parse(InputStream in) throws IOException {
    if (in == null) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_001, "Manifest input stream must not be null.");
    }
    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    return parse(content);
  }

  public static Map<String, ViteRawEntry> parse(String json) {
    if (json == null || json.isBlank()) {
      throw new AssetException(
          AssetDiagnosticCode.VT_ASSET_002, "Manifest content is empty or blank.");
    }
    return new ViteManifestParser(json).parseRoot();
  }

  private Map<String, ViteRawEntry> parseRoot() {
    skipWhitespace();
    if (pos >= length || json.charAt(pos) != '{') {
      throw error("Manifest root must be a JSON object starting with '{'");
    }
    pos++; // skip '{'

    Map<String, ViteRawEntry> entries = new LinkedHashMap<>();
    skipWhitespace();
    if (pos < length && json.charAt(pos) == '}') {
      pos++;
      return entries;
    }

    while (pos < length) {
      skipWhitespace();
      String key = parseString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      ViteRawEntry entry = parseEntry(key);
      entries.put(key, entry);

      skipWhitespace();
      if (pos < length && json.charAt(pos) == ',') {
        pos++;
      } else if (pos < length && json.charAt(pos) == '}') {
        pos++;
        break;
      } else {
        throw error("Expected ',' or '}' in manifest root object");
      }
    }

    skipWhitespace();
    if (pos < length) {
      throw error("Unexpected trailing content after manifest JSON object");
    }

    return Collections.unmodifiableMap(entries);
  }

  private ViteRawEntry parseEntry(String entryKey) {
    expect('{');

    String file = null;
    String src = null;
    String name = null;
    boolean isEntry = false;
    boolean isDynamicEntry = false;
    List<String> imports = new ArrayList<>();
    List<String> dynamicImports = new ArrayList<>();
    List<String> css = new ArrayList<>();
    List<String> assets = new ArrayList<>();

    skipWhitespace();
    if (pos < length && json.charAt(pos) == '}') {
      pos++;
      throw error(
          "Manifest entry '" + entryKey + "' is empty and missing required 'file' property");
    }

    while (pos < length) {
      skipWhitespace();
      String field = parseString();
      skipWhitespace();
      expect(':');
      skipWhitespace();

      switch (field) {
        case "file" -> file = parseString();
        case "src" -> src = parseString();
        case "name" -> name = parseString();
        case "isEntry" -> isEntry = parseBoolean();
        case "isDynamicEntry" -> isDynamicEntry = parseBoolean();
        case "imports" -> imports = parseStringList();
        case "dynamicImports" -> dynamicImports = parseStringList();
        case "css" -> css = parseStringList();
        case "assets" -> assets = parseStringList();
        default -> skipValue(); // Ignore unknown properties for forward compatibility
      }

      skipWhitespace();
      if (pos < length && json.charAt(pos) == ',') {
        pos++;
      } else if (pos < length && json.charAt(pos) == '}') {
        pos++;
        break;
      } else {
        throw error("Expected ',' or '}' in manifest entry object for '" + entryKey + "'");
      }
    }

    if (file == null || file.isBlank()) {
      throw invariantError(
          "Manifest entry '" + entryKey + "' is missing required 'file' property", entryKey);
    }

    return new ViteRawEntry(
        entryKey,
        file,
        src,
        name,
        isEntry,
        isDynamicEntry,
        List.copyOf(imports),
        List.copyOf(dynamicImports),
        List.copyOf(css),
        List.copyOf(assets));
  }

  private String parseString() {
    skipWhitespace();
    if (pos >= length || json.charAt(pos) != '"') {
      throw error("Expected string beginning with '\"'");
    }
    pos++; // skip opening '"'

    StringBuilder sb = new StringBuilder();
    while (pos < length) {
      char c = json.charAt(pos++);
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\') {
        if (pos >= length) {
          throw error("Unterminated escape sequence in string");
        }
        char esc = json.charAt(pos++);
        switch (esc) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > length) {
              throw error("Invalid unicode escape in string");
            }
            String hex = json.substring(pos, pos + 4);
            pos += 4;
            try {
              sb.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException e) {
              throw error("Invalid hex in unicode escape: \\u" + hex);
            }
          }
          default -> {
            if (esc <= 0x1F) {
              throw error(
                  "Unescaped control character in escape sequence (code point " + (int) esc + ")");
            }
            sb.append(esc);
          }
        }
      } else {
        if (c <= 0x1F) {
          throw error("Unescaped control character in JSON string (code point " + (int) c + ")");
        }
        sb.append(c);
      }
    }
    throw error("Unterminated string");
  }

  private boolean parseBoolean() {
    skipWhitespace();
    if (match("true")) {
      return true;
    }
    if (match("false")) {
      return false;
    }
    throw error("Expected boolean literal ('true' or 'false')");
  }

  private List<String> parseStringList() {
    skipWhitespace();
    if (match("null")) {
      return Collections.emptyList();
    }
    if (pos >= length || json.charAt(pos) != '[') {
      throw error("Expected array beginning with '['");
    }
    pos++;

    List<String> list = new ArrayList<>();
    skipWhitespace();
    if (pos < length && json.charAt(pos) == ']') {
      pos++;
      return list;
    }

    while (pos < length) {
      skipWhitespace();
      list.add(parseString());
      skipWhitespace();
      if (pos < length && json.charAt(pos) == ',') {
        pos++;
      } else if (pos < length && json.charAt(pos) == ']') {
        pos++;
        break;
      } else {
        throw error("Expected ',' or ']' in array");
      }
    }
    return list;
  }

  private void skipValue() {
    skipWhitespace();
    if (pos >= length) {
      return;
    }
    char c = json.charAt(pos);
    if (c == '"') {
      parseString();
    } else if (c == '{') {
      pos++;
      int depth = 1;
      while (pos < length && depth > 0) {
        char ch = json.charAt(pos++);
        if (ch == '"') {
          pos--;
          parseString();
        } else if (ch == '{') {
          depth++;
        } else if (ch == '}') {
          depth--;
        }
      }
    } else if (c == '[') {
      pos++;
      int depth = 1;
      while (pos < length && depth > 0) {
        char ch = json.charAt(pos++);
        if (ch == '"') {
          pos--;
          parseString();
        } else if (ch == '[') {
          depth++;
        } else if (ch == ']') {
          depth--;
        }
      }
    } else {
      // primitive number, boolean, null
      while (pos < length && ",}] \t\r\n".indexOf(json.charAt(pos)) == -1) {
        pos++;
      }
    }
  }

  private void expect(char expected) {
    skipWhitespace();
    if (pos >= length || json.charAt(pos) != expected) {
      throw error("Expected '" + expected + "'");
    }
    pos++;
  }

  private boolean match(String token) {
    if (json.startsWith(token, pos)) {
      pos += token.length();
      return true;
    }
    return false;
  }

  private void skipWhitespace() {
    while (pos < length) {
      char c = json.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        pos++;
      } else {
        break;
      }
    }
  }

  private AssetException error(String message) {
    int line = 1;
    int col = 1;
    for (int i = 0; i < pos && i < length; i++) {
      if (json.charAt(i) == '\n') {
        line++;
        col = 1;
      } else {
        col++;
      }
    }
    return new AssetException(
        AssetDiagnosticCode.VT_ASSET_002, message + " at line " + line + ", column " + col);
  }

  private AssetException invariantError(String message, String target) {
    return new AssetException(AssetDiagnosticCode.VT_ASSET_009, target, message);
  }
}
