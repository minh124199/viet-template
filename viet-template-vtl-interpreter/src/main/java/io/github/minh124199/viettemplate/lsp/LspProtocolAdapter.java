package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.Diagnostic;
import io.github.minh124199.viettemplate.api.DiagnosticSeverity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Protocol adapter connecting JSON-RPC 2.0 messages to the {@link TemplateLanguageService}.
 *
 * <p>Implements the standard Language Server Protocol (LSP) lifecycle state machine: {@code
 * UNINITIALIZED -> INITIALIZED -> SHUTDOWN -> EXITED}.
 */
final class LspProtocolAdapter {

  enum ServerState {
    UNINITIALIZED,
    INITIALIZED,
    SHUTDOWN,
    EXITED
  }

  private final TemplateLanguageService service;
  private final Consumer<String> notificationSender;
  private volatile ServerState state = ServerState.UNINITIALIZED;

  LspProtocolAdapter(TemplateLanguageService service, Consumer<String> notificationSender) {
    this.service = Objects.requireNonNull(service, "service must not be null");
    this.notificationSender = notificationSender != null ? notificationSender : msg -> {};
    this.service.setDiagnosticListener(this::republishOpenDocumentDiagnostics);
  }

  public ServerState state() {
    return state;
  }

  public TemplateLanguageService service() {
    return service;
  }

  /**
   * Processes a single JSON-RPC message and returns the response JSON string, or {@code null} if
   * the message was a notification that requires no direct reply.
   */
  public String handleMessage(String messageText) {
    if (messageText == null || messageText.isBlank()) {
      return jsonRpcError(null, -32700, "Empty JSON-RPC message");
    }

    Object parsed;
    try {
      parsed = new JsonParser(messageText).parse();
    } catch (IllegalArgumentException e) {
      return jsonRpcError(null, -32700, "Parse error: " + e.getMessage());
    }

    if (!(parsed instanceof Map<?, ?> msg)) {
      return jsonRpcError(null, -32600, "Invalid Request: message must be a JSON object");
    }

    Object id = msg.get("id");
    Object methodObj = msg.get("method");
    String method = methodObj != null ? methodObj.toString() : "";
    Object paramsObj = msg.get("params");
    Map<?, ?> params = paramsObj instanceof Map<?, ?> m ? m : Map.of();

    // Notifications have no id
    boolean isNotification = (id == null);

    if (state == ServerState.SHUTDOWN && !"exit".equals(method)) {
      if (isNotification) return null;
      return jsonRpcError(id, -32600, "Invalid request: server is already shutdown");
    }

    if (state == ServerState.UNINITIALIZED
        && !"initialize".equals(method)
        && !"exit".equals(method)) {
      if (isNotification) return null;
      return jsonRpcError(id, -32002, "Server not initialized");
    }

    switch (method) {
      case "initialize" -> {
        state = ServerState.INITIALIZED;
        return handleInitialize(id, params);
      }
      case "initialized" -> {
        // Client acknowledgment notification
        return null;
      }
      case "shutdown" -> {
        state = ServerState.SHUTDOWN;
        return jsonRpcSuccess(id, "null");
      }
      case "exit" -> {
        state = ServerState.EXITED;
        return null;
      }
      case "textDocument/didOpen" -> {
        handleDidOpen(params);
        return null;
      }
      case "textDocument/didChange" -> {
        handleDidChange(params);
        return null;
      }
      case "textDocument/didClose" -> {
        handleDidClose(params);
        return null;
      }
      case "textDocument/completion" -> {
        return handleCompletion(id, params);
      }
      case "textDocument/hover" -> {
        return handleHover(id, params);
      }
      case "textDocument/definition" -> {
        return handleDefinition(id, params);
      }
      case "textDocument/references" -> {
        return handleReferences(id, params);
      }
      case "textDocument/prepareRename" -> {
        return handlePrepareRename(id, params);
      }
      case "textDocument/rename" -> {
        return handleRename(id, params);
      }
      case "workspace/didChangeWatchedFiles" -> {
        handleDidChangeWatchedFiles(params);
        return null;
      }
      case "workspace/symbol" -> {
        return handleWorkspaceSymbol(id, params);
      }
      case "$/cancelRequest" -> {
        return null;
      }
      default -> {
        if (isNotification) {
          return null; // Unknown notifications are ignored
        }
        return jsonRpcError(id, -32601, "Method not found: " + method);
      }
    }
  }

  private String handleInitialize(Object id, Map<?, ?> params) {
    if (params != null) {
      Object wsFolders = params.get("workspaceFolders");
      if (wsFolders instanceof List<?> list) {
        for (Object f : list) {
          if (f instanceof Map<?, ?> folderMap) {
            String fUri = getString(folderMap, "uri");
            if (fUri != null) {
              try {
                service.setWorkspaceRoot(java.nio.file.Path.of(java.net.URI.create(fUri)));
              } catch (IllegalArgumentException
                  | java.nio.file.FileSystemNotFoundException ignored) {
              }
            }
          }
        }
      }
      String rootUri = getString(params, "rootUri");
      if (rootUri != null && !rootUri.isBlank() && !"null".equalsIgnoreCase(rootUri)) {
        try {
          service.setWorkspaceRoot(java.nio.file.Path.of(java.net.URI.create(rootUri)));
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
        }
      }
      String rootPath = getString(params, "rootPath");
      if (rootPath != null && !rootPath.isBlank() && !"null".equalsIgnoreCase(rootPath)) {
        try {
          service.setWorkspaceRoot(java.nio.file.Path.of(rootPath));
        } catch (IllegalArgumentException ignored) {
        }
      }
    }

    String result =
        """
        {
          "capabilities": {
            "textDocumentSync": 1,
            "completionProvider": {
              "triggerCharacters": [".", "$", "{", "#"],
              "resolveProvider": false
            },
            "hoverProvider": true,
            "definitionProvider": true,
            "referencesProvider": true,
            "renameProvider": {
              "prepareProvider": true
            },
            "workspaceSymbolProvider": true
          },
          "serverInfo": {
            "name": "viet-template-lsp",
            "version": "1.1.0"
          }
        }\
        """;
    return jsonRpcSuccess(id, result);
  }

  private void handleDidOpen(Map<?, ?> params) {
    Object tdObj = params.get("textDocument");
    if (!(tdObj instanceof Map<?, ?> td)) return;
    String uri = getString(td, "uri");
    Number ver = getNumber(td, "version");
    int version = ver != null ? ver.intValue() : 1;
    String text = getString(td, "text");
    if (uri != null && text != null) {
      if (uri.startsWith("file:/")) {
        try {
          service.probeSourceRootsFor(java.nio.file.Path.of(java.net.URI.create(uri)));
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
        }
      }
      service.openDocument(uri, version, text);
      publishDiagnostics(uri, version);
    }
  }

  private void handleDidChange(Map<?, ?> params) {
    Object tdObj = params.get("textDocument");
    if (!(tdObj instanceof Map<?, ?> td)) return;
    String uri = getString(td, "uri");
    Number ver = getNumber(td, "version");
    int version = ver != null ? ver.intValue() : 1;

    Object changesObj = params.get("contentChanges");
    if (changesObj instanceof List<?> list && !list.isEmpty()) {
      Object first = list.get(0);
      if (first instanceof Map<?, ?> cMap) {
        String newText = getString(cMap, "text");
        if (uri != null && newText != null) {
          boolean updated = service.updateDocumentIfNewer(uri, version, newText);
          if (updated) {
            publishDiagnostics(uri, version);
          }
        }
      }
    }
  }

  private void handleDidClose(Map<?, ?> params) {
    Object tdObj = params.get("textDocument");
    if (!(tdObj instanceof Map<?, ?> td)) return;
    String uri = getString(td, "uri");
    if (uri != null) {
      service.closeDocument(uri);
      // Clear diagnostics on close
      String notification =
          String.format(
              "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/publishDiagnostics\",\"params\":{\"uri\":\"%s\",\"diagnostics\":[]}}",
              escapeJson(uri));
      notificationSender.accept(notification);
    }
  }

  private void handleDidChangeWatchedFiles(Map<?, ?> params) {
    Object changesObj = params.get("changes");
    if (changesObj instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> c) {
          String uri = getString(c, "uri");
          Number typeNum = getNumber(c, "type");
          int changeType = typeNum != null ? typeNum.intValue() : 2;
          if (uri != null) {
            service.onWatchedFileChanged(uri, changeType);
          }
        }
      }
    }
  }

  private void republishOpenDocumentDiagnostics(String uri) {
    Optional<TemplateDocument> doc = service.getDocument(uri);
    if (doc.isPresent()) {
      publishDiagnostics(uri, doc.get().version());
    }
  }

  public void publishDiagnostics(String uri, int version) {
    List<Diagnostic> diags = service.diagnostics(uri);
    Optional<TemplateDocument> doc = service.getDocument(uri);
    StringBuilder sb = new StringBuilder();
    sb.append("{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/publishDiagnostics\",\"params\":{");
    sb.append("\"uri\":\"").append(escapeJson(uri)).append("\",");
    sb.append("\"version\":").append(version).append(",");
    sb.append("\"diagnostics\":[");

    for (int i = 0; i < diags.size(); i++) {
      if (i > 0) sb.append(",");
      Diagnostic d = diags.get(i);
      Range r = doc.map(dDoc -> dDoc.spanToRange(d.primarySpan())).orElse(Range.of(0, 0, 0, 0));
      int severity = mapSeverity(d.severity());

      sb.append("{");
      sb.append("\"range\":{\"start\":{\"line\":")
          .append(r.start().line())
          .append(",\"character\":")
          .append(r.start().character())
          .append("},");
      sb.append("\"end\":{\"line\":")
          .append(r.end().line())
          .append(",\"character\":")
          .append(r.end().character())
          .append("}},");
      sb.append("\"severity\":").append(severity).append(",");
      sb.append("\"code\":\"").append(escapeJson(d.code().qualifiedCode())).append("\",");
      sb.append("\"source\":\"viet-template\",");
      sb.append("\"message\":\"").append(escapeJson(d.message())).append("\"");
      sb.append("}");
    }

    sb.append("]}}");
    notificationSender.accept(sb.toString());
  }

  private String handleCompletion(Object id, Map<?, ?> params) {
    String uri = getDocUri(params);
    Position pos = getPosition(params);
    if (uri == null || pos == null) {
      return jsonRpcSuccess(id, "{\"isIncomplete\":false,\"items\":[]}");
    }

    CompletionList compList = service.complete(uri, pos);
    StringBuilder sb = new StringBuilder();
    sb.append("{\"isIncomplete\":").append(compList.isIncomplete()).append(",\"items\":[");
    List<CompletionItem> items = compList.items();
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) sb.append(",");
      CompletionItem ci = items.get(i);
      sb.append("{");
      sb.append("\"label\":\"").append(escapeJson(ci.label())).append("\",");
      sb.append("\"kind\":").append(ci.kind().value()).append(",");
      sb.append("\"detail\":\"").append(escapeJson(ci.detail())).append("\",");
      sb.append("\"documentation\":\"").append(escapeJson(ci.documentation())).append("\",");
      sb.append("\"sortText\":\"").append(escapeJson(ci.sortText())).append("\"");
      sb.append("}");
    }
    sb.append("]}");
    return jsonRpcSuccess(id, sb.toString());
  }

  private String handleHover(Object id, Map<?, ?> params) {
    String uri = getDocUri(params);
    Position pos = getPosition(params);
    if (uri == null || pos == null) {
      return jsonRpcSuccess(id, "null");
    }

    Optional<HoverInfo> hover = service.hover(uri, pos);
    if (hover.isEmpty()) {
      return jsonRpcSuccess(id, "null");
    }

    HoverInfo hi = hover.get();
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"contents\":{\"kind\":\"markdown\",\"value\":\"")
        .append(escapeJson(hi.markdownValue()))
        .append("\"}");
    if (hi.range().isPresent()) {
      Range r = hi.range().get();
      sb.append(",\"range\":{\"start\":{\"line\":")
          .append(r.start().line())
          .append(",\"character\":")
          .append(r.start().character())
          .append("},\"end\":{\"line\":")
          .append(r.end().line())
          .append(",\"character\":")
          .append(r.end().character())
          .append("}}");
    }
    sb.append("}");
    return jsonRpcSuccess(id, sb.toString());
  }

  private String handleDefinition(Object id, Map<?, ?> params) {
    String uri = getDocUri(params);
    Position pos = getPosition(params);
    if (uri == null || pos == null) {
      return jsonRpcSuccess(id, "[]");
    }

    if (uri.startsWith("file:/")) {
      try {
        service.probeSourceRootsFor(java.nio.file.Path.of(java.net.URI.create(uri)));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }

    List<LocationInfo> locs = service.definition(uri, pos);
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < locs.size(); i++) {
      if (i > 0) sb.append(",");
      LocationInfo loc = locs.get(i);
      Range r = loc.range();
      sb.append("{");
      sb.append("\"uri\":\"").append(escapeJson(loc.uri())).append("\",");
      sb.append("\"range\":{\"start\":{\"line\":")
          .append(r.start().line())
          .append(",\"character\":")
          .append(r.start().character())
          .append("},\"end\":{\"line\":")
          .append(r.end().line())
          .append(",\"character\":")
          .append(r.end().character())
          .append("}}");
      sb.append("}");
    }
    sb.append("]");
    return jsonRpcSuccess(id, sb.toString());
  }

  private String handleReferences(Object id, Map<?, ?> params) {
    String uri = getDocUri(params);
    Position pos = getPosition(params);
    if (uri == null || pos == null) {
      return jsonRpcSuccess(id, "[]");
    }

    boolean includeDeclaration = false;
    Object contextObj = params.get("context");
    if (contextObj instanceof Map<?, ?> context) {
      Object incDecl = context.get("includeDeclaration");
      if (Boolean.TRUE.equals(incDecl) || "true".equalsIgnoreCase(String.valueOf(incDecl))) {
        includeDeclaration = true;
      }
    }

    if (uri.startsWith("file:/")) {
      try {
        service.probeSourceRootsFor(java.nio.file.Path.of(java.net.URI.create(uri)));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }

    List<LocationInfo> locs = service.references(uri, pos, includeDeclaration);
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < locs.size(); i++) {
      if (i > 0) sb.append(",");
      LocationInfo loc = locs.get(i);
      Range r = loc.range();
      sb.append("{");
      sb.append("\"uri\":\"").append(escapeJson(loc.uri())).append("\",");
      sb.append("\"range\":{\"start\":{\"line\":")
          .append(r.start().line())
          .append(",\"character\":")
          .append(r.start().character())
          .append("},\"end\":{\"line\":")
          .append(r.end().line())
          .append(",\"character\":")
          .append(r.end().character())
          .append("}}");
      sb.append("}");
    }
    sb.append("]");
    return jsonRpcSuccess(id, sb.toString());
  }

  private String handlePrepareRename(Object id, Map<?, ?> params) {
    String uri = getDocUri(params);
    Position pos = getPosition(params);
    if (uri == null || pos == null) {
      return jsonRpcSuccess(id, "null");
    }

    if (uri.startsWith("file:/")) {
      try {
        service.probeSourceRootsFor(java.nio.file.Path.of(java.net.URI.create(uri)));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }

    Optional<PrepareRenameResult> result = service.prepareRename(uri, pos);
    if (result.isEmpty()) {
      return jsonRpcSuccess(id, "null");
    }

    PrepareRenameResult pr = result.get();
    Range r = pr.range();
    String resultJson =
        String.format(
            "{\"range\":{\"start\":{\"line\":%d,\"character\":%d},\"end\":{\"line\":%d,\"character\":%d}},\"placeholder\":\"%s\"}",
            r.start().line(),
            r.start().character(),
            r.end().line(),
            r.end().character(),
            escapeJson(pr.placeholder()));
    return jsonRpcSuccess(id, resultJson);
  }

  private String handleRename(Object id, Map<?, ?> params) {
    String uri = getDocUri(params);
    Position pos = getPosition(params);
    String newName = getString(params, "newName");
    if (uri == null || pos == null || newName == null) {
      return jsonRpcError(id, -32602, "Invalid params for rename");
    }

    if (uri.startsWith("file:/")) {
      try {
        service.probeSourceRootsFor(java.nio.file.Path.of(java.net.URI.create(uri)));
      } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ignored) {
      }
    }

    try {
      WorkspaceEdit edit = service.rename(uri, pos, newName);
      return jsonRpcSuccess(id, serializeWorkspaceEdit(edit));
    } catch (RenameConflictException e) {
      return jsonRpcError(id, -32600, e.getMessage());
    } catch (IllegalArgumentException e) {
      return jsonRpcError(id, -32600, e.getMessage());
    }
  }

  private String serializeWorkspaceEdit(WorkspaceEdit edit) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"changes\":{");
    int uriIdx = 0;
    for (Map.Entry<String, List<TextEdit>> entry : edit.changes().entrySet()) {
      if (uriIdx > 0) sb.append(",");
      sb.append("\"").append(escapeJson(entry.getKey())).append("\":[");
      List<TextEdit> edits = entry.getValue();
      for (int i = 0; i < edits.size(); i++) {
        if (i > 0) sb.append(",");
        TextEdit te = edits.get(i);
        Range r = te.range();
        sb.append("{\"range\":{\"start\":{\"line\":")
            .append(r.start().line())
            .append(",\"character\":")
            .append(r.start().character())
            .append("},\"end\":{\"line\":")
            .append(r.end().line())
            .append(",\"character\":")
            .append(r.end().character())
            .append("}},\"newText\":\"")
            .append(escapeJson(te.newText()))
            .append("\"}");
      }
      sb.append("]");
      uriIdx++;
    }
    sb.append("}}");
    return sb.toString();
  }

  private String handleWorkspaceSymbol(Object id, Map<?, ?> params) {
    String query = getString(params, "query");
    if (query == null) {
      query = "";
    }
    List<SymbolInformation> symbols = service.workspaceSymbols(query);
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < symbols.size(); i++) {
      if (i > 0) sb.append(",");
      SymbolInformation s = symbols.get(i);
      Range r = s.location().range();
      sb.append("{");
      sb.append("\"name\":\"").append(escapeJson(s.name())).append("\",");
      sb.append("\"kind\":").append(s.kind().value()).append(",");
      sb.append("\"location\":{");
      sb.append("\"uri\":\"").append(escapeJson(s.location().uri())).append("\",");
      sb.append("\"range\":{\"start\":{\"line\":")
          .append(r.start().line())
          .append(",\"character\":")
          .append(r.start().character())
          .append("},\"end\":{\"line\":")
          .append(r.end().line())
          .append(",\"character\":")
          .append(r.end().character())
          .append("}}");
      sb.append("}");
      if (s.containerName() != null && !s.containerName().isBlank()) {
        sb.append(",\"containerName\":\"").append(escapeJson(s.containerName())).append("\"");
      }
      sb.append("}");
    }
    sb.append("]");
    return jsonRpcSuccess(id, sb.toString());
  }

  // --- Helper Methods ---

  private static int mapSeverity(DiagnosticSeverity severity) {
    return switch (severity) {
      case ERROR -> 1;
      case WARNING -> 2;
      case INFO -> 3;
    };
  }

  private static String getDocUri(Map<?, ?> params) {
    Object tdObj = params.get("textDocument");
    if (tdObj instanceof Map<?, ?> td) {
      return getString(td, "uri");
    }
    return null;
  }

  private static Position getPosition(Map<?, ?> params) {
    Object posObj = params.get("position");
    if (posObj instanceof Map<?, ?> p) {
      Number line = getNumber(p, "line");
      Number character = getNumber(p, "character");
      if (line != null && character != null) {
        return Position.of(line.intValue(), character.intValue());
      }
    }
    return null;
  }

  private static String jsonRpcSuccess(Object id, String resultJson) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"jsonrpc\":\"2.0\",");
    sb.append("\"id\":").append(formatId(id)).append(",");
    sb.append("\"result\":").append(resultJson);
    sb.append("}");
    return sb.toString();
  }

  private static String jsonRpcError(Object id, int code, String message) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"jsonrpc\":\"2.0\",");
    sb.append("\"id\":").append(formatId(id)).append(",");
    sb.append("\"error\":{\"code\":")
        .append(code)
        .append(",\"message\":\"")
        .append(escapeJson(message))
        .append("\"}");
    sb.append("}");
    return sb.toString();
  }

  private static String formatId(Object id) {
    if (id == null) return "null";
    if (id instanceof Number) return id.toString();
    return "\"" + escapeJson(id.toString()) + "\"";
  }

  private static String getString(Map<?, ?> map, String key) {
    Object val = map.get(key);
    return val != null ? val.toString() : null;
  }

  private static Number getNumber(Map<?, ?> map, String key) {
    Object val = map.get(key);
    return val instanceof Number n ? n : null;
  }

  static String escapeJson(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  // --- Internal JSON Parser ---

  private static final class JsonParser {
    private final String src;
    private int pos = 0;

    JsonParser(String src) {
      this.src = src != null ? src : "";
    }

    Object parse() {
      skipWhitespace();
      if (pos >= src.length()) return null;
      Object res = parseValue();
      skipWhitespace();
      return res;
    }

    private Object parseValue() {
      skipWhitespace();
      if (pos >= src.length()) throw error("Unexpected end of input");
      char c = src.charAt(pos);
      if (c == '{') return parseObject();
      if (c == '[') return parseArray();
      if (c == '"') return parseString();
      if (c == 't' || c == 'f') return parseBoolean();
      if (c == 'n') return parseNull();
      if (c == '-' || Character.isDigit(c)) return parseNumber();
      throw error("Unexpected character: '" + c + "'");
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
          throw error("Expected ',' or '}' in object");
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
          throw error("Expected ',' or ']' in array");
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
          if (pos >= src.length()) throw error("Unterminated escape sequence");
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
              if (pos + 4 > src.length()) throw error("Invalid unicode escape");
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
      throw error("Unterminated string");
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
      throw error("Invalid boolean literal");
    }

    private Object parseNull() {
      if (src.startsWith("null", pos)) {
        pos += 4;
        return null;
      }
      throw error("Invalid null literal");
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
        throw error("Expected '" + expected + "'");
      }
      pos++;
    }

    private void skipWhitespace() {
      while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
        pos++;
      }
    }

    private IllegalArgumentException error(String msg) {
      return new IllegalArgumentException(msg + " at character " + pos);
    }
  }
}
