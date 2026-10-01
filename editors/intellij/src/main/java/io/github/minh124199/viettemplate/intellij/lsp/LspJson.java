package io.github.minh124199.viettemplate.intellij.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, zero-dependency JSON parser and serializer tailored for LSP JSON-RPC messages.
 */
public final class LspJson {

  private LspJson() {}

  public static Object parse(String jsonText) {
    if (jsonText == null || jsonText.isBlank()) return null;
    return new Parser(jsonText).parseValue();
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> parseObject(String jsonText) {
    Object result = parse(jsonText);
    return (result instanceof Map<?, ?> map) ? (Map<String, Object>) map : Map.of();
  }

  public static String escapeJson(String raw) {
    if (raw == null) return "";
    StringBuilder sb = new StringBuilder(raw.length() + 16);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < ' ') {
            String hex = Integer.toHexString(c);
            sb.append("\\u");
            sb.append("0".repeat(4 - hex.length()));
            sb.append(hex);
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  public static String getString(Map<?, ?> map, String key) {
    if (map == null) return null;
    Object val = map.get(key);
    return (val instanceof String s) ? s : (val != null ? val.toString() : null);
  }

  public static Integer getInt(Map<?, ?> map, String key) {
    if (map == null) return null;
    Object val = map.get(key);
    if (val instanceof Number n) return n.intValue();
    if (val instanceof String s) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException ignored) {}
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getMap(Map<?, ?> map, String key) {
    if (map == null) return null;
    Object val = map.get(key);
    return (val instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> getList(Map<?, ?> map, String key) {
    if (map == null) return List.of();
    Object val = map.get(key);
    return (val instanceof List<?> l) ? (List<Object>) l : List.of();
  }

  private static final class Parser {
    private final String src;
    private int pos = 0;

    Parser(String src) {
      this.src = src;
    }

    Object parseValue() {
      skipWhitespace();
      if (pos >= src.length()) return null;
      char c = src.charAt(pos);
      if (c == '{') return parseObject();
      if (c == '[') return parseArray();
      if (c == '"') return parseString();
      if (c == 't' || c == 'f') return parseBoolean();
      if (c == 'n') return parseNull();
      if (c == '-' || Character.isDigit(c)) return parseNumber();
      throw new IllegalArgumentException("Unexpected JSON character '" + c + "' at position " + pos);
    }

    private Map<String, Object> parseObject() {
      consume('{');
      Map<String, Object> map = new LinkedHashMap<>();
      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == '}') {
        pos++;
        return map;
      }
      while (pos < src.length()) {
        skipWhitespace();
        String key = parseString();
        skipWhitespace();
        consume(':');
        Object value = parseValue();
        map.put(key, value);
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
        } else if (pos < src.length() && src.charAt(pos) == '}') {
          pos++;
          break;
        } else {
          throw new IllegalArgumentException("Expected ',' or '}' in object at position " + pos);
        }
      }
      return map;
    }

    private List<Object> parseArray() {
      consume('[');
      List<Object> list = new ArrayList<>();
      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == ']') {
        pos++;
        return list;
      }
      while (pos < src.length()) {
        Object item = parseValue();
        list.add(item);
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
        } else if (pos < src.length() && src.charAt(pos) == ']') {
          pos++;
          break;
        } else {
          throw new IllegalArgumentException("Expected ',' or ']' in array at position " + pos);
        }
      }
      return list;
    }

    private String parseString() {
      consume('"');
      StringBuilder sb = new StringBuilder();
      while (pos < src.length()) {
        char c = src.charAt(pos++);
        if (c == '"') return sb.toString();
        if (c == '\\') {
          if (pos >= src.length()) throw new IllegalArgumentException("Unterminated escape sequence");
          char esc = src.charAt(pos++);
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
              if (pos + 4 > src.length()) throw new IllegalArgumentException("Invalid unicode escape");
              String hex = src.substring(pos, pos + 4);
              pos += 4;
              sb.append((char) Integer.parseInt(hex, 16));
            }
            default -> sb.append(esc);
          }
        } else {
          sb.append(c);
        }
      }
      throw new IllegalArgumentException("Unterminated string literal");
    }

    private Boolean parseBoolean() {
      if (src.startsWith("true", pos)) {
        pos += 4;
        return Boolean.TRUE;
      }
      if (src.startsWith("false", pos)) {
        pos += 5;
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
    }

    private Object parseNull() {
      if (src.startsWith("null", pos)) {
        pos += 4;
        return null;
      }
      throw new IllegalArgumentException("Invalid null literal at position " + pos);
    }

    private Number parseNumber() {
      int start = pos;
      if (src.charAt(pos) == '-') pos++;
      while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      boolean isFloat = false;
      if (pos < src.length() && src.charAt(pos) == '.') {
        isFloat = true;
        pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      }
      if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
        isFloat = true;
        pos++;
        if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
      }
      String numStr = src.substring(start, pos);
      if (isFloat) return Double.parseDouble(numStr);
      try {
        return Long.parseLong(numStr);
      } catch (NumberFormatException ignored) {
        return Double.parseDouble(numStr);
      }
    }

    private void consume(char expected) {
      skipWhitespace();
      if (pos >= src.length() || src.charAt(pos) != expected) {
        throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
      }
      pos++;
    }

    private void skipWhitespace() {
      while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
        pos++;
      }
    }
  }
}
