package io.github.minh124199.viettemplate.lsp;

import io.github.minh124199.viettemplate.api.MemberAccessPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entrypoint and coordinator for the Viet Template Language Server Protocol (LSP) engine.
 *
 * <p>Supports standard stdio framing execution via {@link #main(String[])} and {@link
 * #run(InputStream, OutputStream)}, as well as embedded in-memory message processing via {@link
 * #handleMessage(String)}.
 */
public final class VietTemplateLanguageServer {

  private final TemplateLanguageService service;
  private final List<String> notifications = new CopyOnWriteArrayList<>();
  private final LspProtocolAdapter adapter;

  private VietTemplateLanguageServer(TemplateLanguageService service) {
    this.service = Objects.requireNonNull(service, "service must not be null");
    this.adapter = new LspProtocolAdapter(service, notifications::add);
  }

  /**
   * Creates a new language server instance with the standard security profile.
   *
   * @return a new server instance
   */
  public static VietTemplateLanguageServer create() {
    return create(MemberAccessPolicy.standard());
  }

  /**
   * Creates a new language server instance with the specified security policy.
   *
   * @param memberAccessPolicy the member access policy governing symbol visibility
   * @return a new server instance
   */
  public static VietTemplateLanguageServer create(MemberAccessPolicy memberAccessPolicy) {
    TemplateLanguageService svc = TemplateLanguageService.create(memberAccessPolicy);
    return new VietTemplateLanguageServer(svc);
  }

  static VietTemplateLanguageServer create(TemplateLanguageService service) {
    return new VietTemplateLanguageServer(service);
  }

  /**
   * Standard CLI entrypoint running the language server over {@link System#in} and {@link
   * System#out}.
   *
   * @param args command-line arguments (unused)
   * @throws IOException if an I/O error occurs during server execution
   */
  public static void main(String[] args) throws IOException {
    VietTemplateLanguageServer server = create();
    server.run(System.in, System.out);
  }

  /**
   * Runs the language server message loop over the given input and output streams.
   *
   * @param in the input stream receiving LSP framed requests and notifications
   * @param out the output stream sending LSP framed responses and notifications
   * @throws IOException if an I/O error occurs
   */
  public void run(InputStream in, OutputStream out) throws IOException {
    Objects.requireNonNull(in, "in must not be null");
    Objects.requireNonNull(out, "out must not be null");

    LspProtocolAdapter sessionAdapter =
        new LspProtocolAdapter(
            service,
            msg -> {
              try {
                LspStreamTransport.writeMessage(out, msg);
              } catch (IOException e) {
                throw new IllegalStateException(
                    "Failed to write notification to LSP output stream", e);
              }
            });

    while (sessionAdapter.state() != LspProtocolAdapter.ServerState.EXITED) {
      String message = LspStreamTransport.readMessage(in);
      if (message == null) {
        break;
      }
      String response = sessionAdapter.handleMessage(message);
      if (response != null) {
        LspStreamTransport.writeMessage(out, response);
      }
    }
  }

  /**
   * Handles a raw JSON-RPC 2.0 message string directly in-memory.
   *
   * @param jsonRpcMessage the JSON-RPC message
   * @return the JSON-RPC response string, or {@code null} if the message was a notification
   */
  public String handleMessage(String jsonRpcMessage) {
    return adapter.handleMessage(jsonRpcMessage);
  }

  /**
   * Drains and returns all queued notifications produced by direct {@link #handleMessage(String)}
   * calls.
   *
   * @return list of notification JSON strings
   */
  public List<String> drainNotifications() {
    List<String> drained = new ArrayList<>(notifications);
    notifications.clear();
    return drained;
  }

  /**
   * Registers a canonical contract schema JSON string.
   *
   * @param schemaId the template identifier or URI
   * @param schemaJson the raw schema JSON text
   */
  public void registerSchema(String schemaId, String schemaJson) {
    service.registerSchema(schemaId, schemaJson);
  }

  /**
   * Registers a canonical contract schema file from disk.
   *
   * @param schemaPath path to the {@code *.vt-schema.json} file
   * @throws IOException if the file cannot be read
   */
  public void registerSchemaFile(Path schemaPath) throws IOException {
    service.registerSchemaFile(schemaPath);
  }

  /**
   * Sets the directory scanned for fallback contract schemas.
   *
   * @param directory the directory path
   */
  public void setSchemaDirectory(Path directory) {
    service.setSchemaDirectory(directory);
  }

  /**
   * Returns the active member access policy.
   *
   * @return the active policy
   */
  public MemberAccessPolicy memberAccessPolicy() {
    return service.memberAccessPolicy();
  }

  TemplateLanguageService languageService() {
    return service;
  }

  LspProtocolAdapter adapter() {
    return adapter;
  }
}
