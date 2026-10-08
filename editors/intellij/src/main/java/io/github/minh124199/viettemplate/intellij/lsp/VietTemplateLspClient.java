package io.github.minh124199.viettemplate.intellij.lsp;

import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Robust JSON-RPC 2.0 stdio LSP client managing the Viet Template Language Server child process.
 */
public class VietTemplateLspClient implements AutoCloseable {

  private static final Logger LOG = Logger.getInstance(VietTemplateLspClient.class);

  private final ProcessBuilder processBuilder;
  private final Object outputLock = new Object();
  private final AtomicInteger nextRequestId = new AtomicInteger(1);
  private final Map<Integer, CompletableFuture<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();
  private final Map<String, List<LspDiagnostic>> diagnosticsMap = new ConcurrentHashMap<>();
  private final List<Consumer<String>> diagnosticsListeners = new CopyOnWriteArrayList<>();

  private volatile Process process;
  private volatile OutputStream outputStream;
  private volatile Thread readerThread;
  private volatile Thread stderrThread;
  private final AtomicBoolean isRunning = new AtomicBoolean(false);

  public VietTemplateLspClient(ProcessBuilder processBuilder) {
    this.processBuilder = Objects.requireNonNull(processBuilder, "processBuilder must not be null");
  }

  /**
   * Starts the language server process and performs the LSP initialize -> initialized handshake.
   */
  public synchronized void start(String rootUri) throws IOException {
    if (isRunning.get()) {
      return;
    }

    LOG.info("Starting Viet Template Language Server process: " + String.join(" ", processBuilder.command()));
    process = processBuilder.start();
    outputStream = process.getOutputStream();
    isRunning.set(true);

    InputStream in = process.getInputStream();
    readerThread = new Thread(() -> readerLoop(in), "VietTemplateLspClient-Reader");
    readerThread.setDaemon(true);
    readerThread.start();

    InputStream err = process.getErrorStream();
    stderrThread = new Thread(() -> stderrLoop(err), "VietTemplateLspClient-Stderr");
    stderrThread.setDaemon(true);
    stderrThread.start();

    // Handshake: initialize
    int id = nextRequestId.getAndIncrement();
    String safeUri = (rootUri != null) ? rootUri : "";
    long pid = ProcessHandle.current().pid();
    String initRequest = String.format(
        "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"initialize\",\"params\":{\"processId\":%d,\"rootUri\":\"%s\",\"capabilities\":{}}}",
        id, pid, LspJson.escapeJson(safeUri)
    );

    CompletableFuture<Map<String, Object>> initFuture = new CompletableFuture<>();
    pendingRequests.put(id, initFuture);
    sendRawMessage(initRequest);

    try {
      initFuture.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      LOG.warn("Failed or timed out waiting for LSP initialize response", e);
      stop();
      throw new IOException("Failed to initialize Viet Template Language Server: " + e.getMessage(), e);
    }

    // Handshake: initialized notification
    sendNotification("initialized", "{}");
    LOG.info("Viet Template Language Server successfully initialized");
  }

  public boolean isRunning() {
    return isRunning.get() && process != null && process.isAlive();
  }

  public Process getProcess() {
    return process;
  }

  public void addDiagnosticsListener(Consumer<String> listener) {
    if (listener != null) {
      diagnosticsListeners.add(listener);
    }
  }

  public void removeDiagnosticsListener(Consumer<String> listener) {
    if (listener != null) {
      diagnosticsListeners.remove(listener);
    }
  }

  public List<LspDiagnostic> getDiagnostics(String uri) {
    if (uri == null) return Collections.emptyList();
    List<LspDiagnostic> diags = diagnosticsMap.get(uri);
    return diags != null ? Collections.unmodifiableList(diags) : Collections.emptyList();
  }

  // =========================================================================
  // DOCUMENT SYNC
  // =========================================================================

  public void didOpen(String uri, int version, String text) {
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\",\"languageId\":\"viet-template\",\"version\":%d,\"text\":\"%s\"}}",
        LspJson.escapeJson(uri), version, LspJson.escapeJson(text)
    );
    sendNotification("textDocument/didOpen", params);
  }

  public void didChange(String uri, int version, String text) {
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\",\"version\":%d},\"contentChanges\":[{\"text\":\"%s\"}]}",
        LspJson.escapeJson(uri), version, LspJson.escapeJson(text)
    );
    sendNotification("textDocument/didChange", params);
  }

  public void didClose(String uri) {
    diagnosticsMap.remove(uri);
    String params = String.format("{\"textDocument\":{\"uri\":\"%s\"}}", LspJson.escapeJson(uri));
    sendNotification("textDocument/didClose", params);
  }

  // =========================================================================
  // QUERIES
  // =========================================================================

  public CompletableFuture<List<LspCompletionItem>> completion(String uri, int line, int character) {
    int id = nextRequestId.getAndIncrement();
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":%d,\"character\":%d}}",
        LspJson.escapeJson(uri), line, character
    );

    return sendRequest(id, "textDocument/completion", params).thenApply(res -> {
      Object resultObj = res.get("result");
      List<Object> itemsRaw = List.of();
      if (resultObj instanceof Map<?, ?> rMap) {
        itemsRaw = LspJson.getList(rMap, "items");
      } else if (resultObj instanceof List<?> rList) {
        itemsRaw = (List<Object>) rList;
      }

      List<LspCompletionItem> items = new ArrayList<>();
      for (Object raw : itemsRaw) {
        if (raw instanceof Map<?, ?> m) {
          String label = LspJson.getString(m, "label");
          Integer kind = LspJson.getInt(m, "kind");
          String detail = LspJson.getString(m, "detail");
          String doc = LspJson.getString(m, "documentation");
          String sortText = LspJson.getString(m, "sortText");
          if (label != null) {
            items.add(new LspCompletionItem(label, kind != null ? kind : 1, detail, doc, sortText));
          }
        }
      }
      return items;
    });
  }

  public CompletableFuture<LspHoverResult> hover(String uri, int line, int character) {
    int id = nextRequestId.getAndIncrement();
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":%d,\"character\":%d}}",
        LspJson.escapeJson(uri), line, character
    );

    return sendRequest(id, "textDocument/hover", params).thenApply(res -> {
      Object resultObj = res.get("result");
      if (!(resultObj instanceof Map<?, ?> rMap)) {
        return new LspHoverResult("", null);
      }

      String contentStr = "";
      Object contentsObj = rMap.get("contents");
      if (contentsObj instanceof Map<?, ?> cMap) {
        contentStr = LspJson.getString(cMap, "value");
      } else if (contentsObj instanceof String s) {
        contentStr = s;
      }

      LspRange range = parseRange(LspJson.getMap(rMap, "range"));
      return new LspHoverResult(contentStr != null ? contentStr : "", range);
    });
  }

  public CompletableFuture<List<LspLocation>> definition(String uri, int line, int character) {
    int id = nextRequestId.getAndIncrement();
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":%d,\"character\":%d}}",
        LspJson.escapeJson(uri), line, character
    );

    return sendRequest(id, "textDocument/definition", params).thenApply(res -> {
      Object resultObj = res.get("result");
      List<Object> locsRaw = List.of();
      if (resultObj instanceof List<?> l) {
        locsRaw = (List<Object>) l;
      } else if (resultObj instanceof Map<?, ?> m) {
        locsRaw = List.of(m);
      }

      List<LspLocation> locations = new ArrayList<>();
      for (Object raw : locsRaw) {
        if (raw instanceof Map<?, ?> m) {
          String targetUri = LspJson.getString(m, "uri");
          LspRange range = parseRange(LspJson.getMap(m, "range"));
          if (targetUri != null && range != null) {
            locations.add(new LspLocation(targetUri, range));
          }
        }
      }
      return locations;
    });
  }

  public CompletableFuture<List<LspLocation>> references(
      String uri, int line, int character, boolean includeDeclaration) {
    int id = nextRequestId.getAndIncrement();
    String params =
        String.format(
            "{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":%d,\"character\":%d},\"context\":{\"includeDeclaration\":%b}}",
            LspJson.escapeJson(uri), line, character, includeDeclaration);

    return sendRequest(id, "textDocument/references", params)
        .thenApply(
            res -> {
              Object resultObj = res.get("result");
              List<Object> locsRaw = List.of();
              if (resultObj instanceof List<?> l) {
                locsRaw = (List<Object>) l;
              } else if (resultObj instanceof Map<?, ?> m) {
                locsRaw = List.of(m);
              }

              List<LspLocation> locations = new ArrayList<>();
              for (Object raw : locsRaw) {
                if (raw instanceof Map<?, ?> m) {
                  String targetUri = LspJson.getString(m, "uri");
                  LspRange range = parseRange(LspJson.getMap(m, "range"));
                  if (targetUri != null && range != null) {
                    locations.add(new LspLocation(targetUri, range));
                  }
                }
              }
              return locations;
            });
  }

  public CompletableFuture<LspPrepareRenameResult> prepareRename(String uri, int line, int character) {
    int id = nextRequestId.getAndIncrement();
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":%d,\"character\":%d}}",
        LspJson.escapeJson(uri), line, character
    );

    return sendRequest(id, "textDocument/prepareRename", params).thenApply(res -> {
      Object resultObj = res.get("result");
      if (!(resultObj instanceof Map<?, ?> rMap)) {
        return null;
      }
      LspRange range = parseRange(LspJson.getMap(rMap, "range"));
      String placeholder = LspJson.getString(rMap, "placeholder");
      if (range == null || placeholder == null) {
        return null;
      }
      return new LspPrepareRenameResult(range, placeholder);
    });
  }

  public CompletableFuture<LspWorkspaceEdit> rename(String uri, int line, int character, String newName) {
    int id = nextRequestId.getAndIncrement();
    String params = String.format(
        "{\"textDocument\":{\"uri\":\"%s\"},\"position\":{\"line\":%d,\"character\":%d},\"newName\":\"%s\"}",
        LspJson.escapeJson(uri), line, character, LspJson.escapeJson(newName)
    );

    return sendRequest(id, "textDocument/rename", params).thenApply(res -> {
      Object errorObj = res.get("error");
      if (errorObj instanceof Map<?, ?> errMap) {
        String msg = LspJson.getString(errMap, "message");
        throw new RuntimeException("LSP rename error: " + (msg != null ? msg : "unknown"));
      }
      Object resultObj = res.get("result");
      if (!(resultObj instanceof Map<?, ?> rMap)) {
        return LspWorkspaceEdit.empty();
      }
      Map<String, Object> changesMap = LspJson.getMap(rMap, "changes");
      if (changesMap == null) {
        return LspWorkspaceEdit.empty();
      }
      Map<String, List<LspTextEdit>> changes = new java.util.LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : changesMap.entrySet()) {
        if (entry.getValue() instanceof List<?> list) {
          List<LspTextEdit> edits = new ArrayList<>();
          for (Object item : list) {
            if (item instanceof Map<?, ?> eMap) {
              LspRange range = parseRange(LspJson.getMap(eMap, "range"));
              String text = LspJson.getString(eMap, "newText");
              if (range != null && text != null) {
                edits.add(new LspTextEdit(range, text));
              }
            }
          }
          changes.put(entry.getKey(), edits);
        }
      }
      return new LspWorkspaceEdit(changes);
    });
  }

  private LspRange parseRange(Map<String, Object> rangeMap) {
    if (rangeMap == null) return null;
    Map<String, Object> startMap = LspJson.getMap(rangeMap, "start");
    Map<String, Object> endMap = LspJson.getMap(rangeMap, "end");
    int sLine = startMap != null && LspJson.getInt(startMap, "line") != null ? LspJson.getInt(startMap, "line") : 0;
    int sChar = startMap != null && LspJson.getInt(startMap, "character") != null ? LspJson.getInt(startMap, "character") : 0;
    int eLine = endMap != null && LspJson.getInt(endMap, "line") != null ? LspJson.getInt(endMap, "line") : 0;
    int eChar = endMap != null && LspJson.getInt(endMap, "character") != null ? LspJson.getInt(endMap, "character") : 0;
    return LspRange.of(sLine, sChar, eLine, eChar);
  }

  // =========================================================================
  // MESSAGE TRANSPORT & DISPATCH
  // =========================================================================

  public CompletableFuture<Map<String, Object>> sendRequest(int id, String method, String paramsJson) {
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    pendingRequests.put(id, future);

    String req = String.format("{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}", id, method, paramsJson);
    try {
      sendRawMessage(req);
    } catch (IOException e) {
      pendingRequests.remove(id);
      future.completeExceptionally(e);
    }
    return future;
  }

  public void sendNotification(String method, String paramsJson) {
    String notif = String.format("{\"jsonrpc\":\"2.0\",\"method\":\"%s\",\"params\":%s}", method, paramsJson);
    try {
      sendRawMessage(notif);
    } catch (IOException e) {
      LOG.warn("Failed to send LSP notification: " + method, e);
    }
  }

  public void sendRawMessage(String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    String header = "Content-Length: " + body.length + "\r\n\r\n";
    byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);

    synchronized (outputLock) {
      if (outputStream == null) {
        throw new IOException("LSP Client is not running");
      }
      outputStream.write(headerBytes);
      outputStream.write(body);
      outputStream.flush();
    }
  }

  private void readerLoop(InputStream in) {
    try {
      while (isRunning.get()) {
        String msg = readFramedMessage(in);
        if (msg == null) {
          break; // EOF
        }
        dispatchIncomingMessage(msg);
      }
    } catch (Exception e) {
      if (isRunning.get()) {
        LOG.warn("Error reading from LSP server stream", e);
      }
    }
  }

  private void stderrLoop(InputStream err) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        LOG.warn("[VietTemplate LSP stderr] " + line);
      }
    } catch (IOException ignored) {}
  }

  private void dispatchIncomingMessage(String json) {
    Map<String, Object> msg = LspJson.parseObject(json);
    if (msg == null) return;

    Object idObj = msg.get("id");
    if (idObj instanceof Number n) {
      int id = n.intValue();
      CompletableFuture<Map<String, Object>> future = pendingRequests.remove(id);
      if (future != null) {
        future.complete(msg);
      }
      return;
    }

    String method = LspJson.getString(msg, "method");
    if ("textDocument/publishDiagnostics".equals(method)) {
      Map<String, Object> params = LspJson.getMap(msg, "params");
      if (params != null) {
        String uri = LspJson.getString(params, "uri");
        List<Object> diagsRaw = LspJson.getList(params, "diagnostics");
        List<LspDiagnostic> parsedDiags = new ArrayList<>();
        for (Object d : diagsRaw) {
          if (d instanceof Map<?, ?> dMap) {
            LspRange range = parseRange(LspJson.getMap(dMap, "range"));
            Integer severity = LspJson.getInt(dMap, "severity");
            String code = LspJson.getString(dMap, "code");
            String source = LspJson.getString(dMap, "source");
            String message = LspJson.getString(dMap, "message");
            if (range != null) {
              parsedDiags.add(new LspDiagnostic(
                  range,
                  severity != null ? severity : 1,
                  code != null ? code : "",
                  source != null ? source : "viet-template",
                  message != null ? message : ""
              ));
            }
          }
        }
        if (uri != null) {
          diagnosticsMap.put(uri, parsedDiags);
          for (Consumer<String> listener : diagnosticsListeners) {
            try {
              listener.accept(uri);
            } catch (Exception e) {
              LOG.warn("Error notifying diagnostics listener", e);
            }
          }
        }
      }
    }
  }

  private String readFramedMessage(InputStream in) throws IOException {
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
          inHeader = false;
        } else {
          String lower = line.toLowerCase(Locale.ROOT);
          if (lower.startsWith("content-length:")) {
            String valStr = line.substring("content-length:".length()).trim();
            contentLength = Integer.parseInt(valStr);
          }
        }
      } else if (b != '\r') {
        headerLine.append((char) b);
      }
    }

    if (contentLength < 0) {
      throw new IOException("Missing Content-Length in LSP message");
    }

    byte[] body = in.readNBytes(contentLength);
    if (body.length < contentLength) {
      throw new EOFException("Truncated LSP message: expected " + contentLength + " bytes, got " + body.length);
    }

    return new String(body, StandardCharsets.UTF_8);
  }

  // =========================================================================
  // SHUTDOWN & CLEANUP (ZERO LEAK GUARANTEE)
  // =========================================================================

  @Override
  public synchronized void close() {
    stop();
  }

  public synchronized void stop() {
    if (!isRunning.compareAndSet(true, false)) {
      return;
    }

    LOG.info("Stopping Viet Template Language Server client");

    // Fail any outstanding requests
    for (CompletableFuture<Map<String, Object>> future : pendingRequests.values()) {
      future.cancel(true);
    }
    pendingRequests.clear();

    // 1. Send shutdown request (graceful)
    try {
      int id = nextRequestId.getAndIncrement();
      String shutdownReq = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"shutdown\",\"params\":null}";
      sendRawMessage(shutdownReq);
    } catch (Exception ignored) {}

    // 2. Send exit notification
    try {
      String exitNotif = "{\"jsonrpc\":\"2.0\",\"method\":\"exit\",\"params\":null}";
      sendRawMessage(exitNotif);
    } catch (Exception ignored) {}

    // 3. Close output stream
    synchronized (outputLock) {
      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (Exception ignored) {}
        outputStream = null;
      }
    }

    // 4. Wait for process exit, fallback to destroy, then destroyForcibly
    if (process != null) {
      try {
        boolean exited = process.waitFor(2, TimeUnit.SECONDS);
        if (!exited) {
          LOG.warn("Process did not exit within 2s, destroying...");
          process.destroy();
          exited = process.waitFor(1, TimeUnit.SECONDS);
          if (!exited) {
            LOG.warn("Process did not exit after destroy(), force killing...");
            process.destroyForcibly();
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
      process = null;
    }

    // 5. Interrupt worker threads
    if (readerThread != null) {
      readerThread.interrupt();
      readerThread = null;
    }
    if (stderrThread != null) {
      stderrThread.interrupt();
      stderrThread = null;
    }
  }
}
