package io.github.minh124199.viettemplate.intellij.lsp;

import io.github.minh124199.viettemplate.intellij.runtime.JavaRuntimeResolver;
import io.github.minh124199.viettemplate.intellij.runtime.LspServerLauncher;
import io.github.minh124199.viettemplate.intellij.settings.VietTemplateSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VietTemplateLspClientRealServerIntegrationTest {

  @TempDir
  Path tempDir;

  private VietTemplateLspClient client;
  private Process spawnedProcess;

  @BeforeEach
  void setUp() throws Exception {
    VietTemplateSettings settings = new VietTemplateSettings();
    JavaRuntimeResolver resolver = new JavaRuntimeResolver(settings);
    Path javaExec = resolver.resolveAndValidateJavaExecutable();

    LspServerLauncher launcher = new LspServerLauncher(settings);
    Path serverJar = launcher.locateServerJar();

    ProcessBuilder pb = launcher.createProcessBuilder(javaExec, serverJar, List.of(), tempDir.toFile());
    client = new VietTemplateLspClient(pb);
    client.start(tempDir.toUri().toString());
    spawnedProcess = client.getProcess();
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.stop();
    }
    if (spawnedProcess != null) {
      assertThat(spawnedProcess.isAlive()).isFalse();
    }
  }

  @Test
  void shouldPerformHandshakeAndKeepProcessAlive() {
    assertThat(client.isRunning()).isTrue();
    assertThat(spawnedProcess).isNotNull();
    assertThat(spawnedProcess.isAlive()).isTrue();
  }

  @Test
  void shouldPublishDiagnosticsForSyntaxErrorsAndRecoverOnFix() throws Exception {
    String syntaxErrorText = loadFixture("syntax-error.vtl");
    String uri = tempDir.resolve("syntax-error.vtl").toUri().toString();

    // v1: open invalid content -> error diagnostic
    client.didOpen(uri, 1, syntaxErrorText);
    List<LspDiagnostic> diags = pollUntilNotEmpty(() -> client.getDiagnostics(uri), 3000);
    assertThat(diags).isNotEmpty();
    assertThat(diags.get(0).severity()).isEqualTo(LspDiagnostic.SEVERITY_ERROR);
    assertThat(diags.get(0).code()).contains("SYNTAX:PARSE_ERROR");

    // v2: didChange repaired content -> empty diagnostics
    client.didChange(uri, 2, "## Valid content\n#set($x = 10)\n$x\n");
    boolean clearedV2 = pollUntil(() -> client.getDiagnostics(uri).isEmpty(), 3000);
    assertThat(clearedV2).isTrue();

    // v3: didChange re-introducing error -> error diagnostic
    client.didChange(uri, 3, syntaxErrorText);
    List<LspDiagnostic> diagsV3 = pollUntilNotEmpty(() -> client.getDiagnostics(uri), 3000);
    assertThat(diagsV3).isNotEmpty();
    assertThat(diagsV3.get(0).severity()).isEqualTo(LspDiagnostic.SEVERITY_ERROR);
    assertThat(diagsV3.get(0).code()).contains("SYNTAX:PARSE_ERROR");

    // v4: didChange repaired content again -> empty diagnostics
    client.didChange(uri, 4, "## Valid content again\n#set($y = 20)\n$y\n");
    boolean clearedV4 = pollUntil(() -> client.getDiagnostics(uri).isEmpty(), 3000);
    assertThat(clearedV4).isTrue();
  }

  @Test
  void shouldProvideCompletionsForDirectivesAndVariables() throws Exception {
    String uri = tempDir.resolve("completion.vtl").toUri().toString();
    client.didOpen(uri, 1, "#set($customerName = \"Viet\")\n$");

    List<LspCompletionItem> items = client.completion(uri, 1, 1).get(5, TimeUnit.SECONDS);
    assertThat(items).isNotEmpty();
    assertThat(items).extracting(LspCompletionItem::label).anyMatch(label -> label.contains("customerName"));
  }

  @Test
  void shouldProvideHoverDocumentation() throws Exception {
    String uri = tempDir.resolve("hover.vtl").toUri().toString();
    client.didOpen(uri, 1, "#set($user = \"Alice\")\n$user\n");

    // Hover on variable $user (line 1, col 2)
    LspHoverResult varHover = client.hover(uri, 1, 2).get(5, TimeUnit.SECONDS);
    assertThat(varHover).isNotNull();
    assertThat(varHover.contents()).isNotBlank();
    assertThat(varHover.contents()).contains("$user");
    assertThat(varHover.contents()).containsIgnoringCase("Object");

    // Hover on directive #set (line 0, col 1)
    LspHoverResult directiveHover = client.hover(uri, 0, 1).get(5, TimeUnit.SECONDS);
    assertThat(directiveHover).isNotNull();
    assertThat(directiveHover.contents()).isNotBlank();
    assertThat(directiveHover.contents()).contains("#set");
  }

  @Test
  void shouldProvideDefinitionNavigationToContractSchema() throws Exception {
    Path vtlFile = tempDir.resolve("definition.vtl");
    Path schemaFile = tempDir.resolve("definition.vt-schema.json");

    Files.writeString(vtlFile, loadFixture("definition.vtl"), StandardCharsets.UTF_8);
    Files.writeString(schemaFile, loadFixture("definition.vt-schema.json"), StandardCharsets.UTF_8);

    String uri = vtlFile.toUri().toString();
    client.didOpen(uri, 1, Files.readString(vtlFile));

    // Wait for file indexing
    Thread.sleep(150);

    List<LspLocation> locations = client.definition(uri, 0, 8).get(5, TimeUnit.SECONDS);
    assertThat(locations).isNotEmpty();
    LspLocation location = locations.get(0);
    assertThat(location.uri()).endsWith("definition.vt-schema.json");
    assertThat(location.range()).isNotNull();
    assertThat(location.range().start().line()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void shouldProvideInTemplateDefinitionNavigationForLocalVariables() throws Exception {
    String uri = tempDir.resolve("in-template-def.vtl").toUri().toString();
    String text = "#set($local = 42)\nValue: $local\n";
    client.didOpen(uri, 1, text);

    List<LspLocation> locations = client.definition(uri, 1, 8).get(5, TimeUnit.SECONDS);
    assertThat(locations).isNotEmpty();
    LspLocation location = locations.get(0);
    assertThat(location.uri()).isEqualTo(uri);
    assertThat(location.range()).isNotNull();
    assertThat(location.range().start().line()).isEqualTo(0);
  }

  @Test
  void shouldProvideReferencesForLocalVariables() throws Exception {
    String uri = tempDir.resolve("in-template-refs.vtl").toUri().toString();
    String text = "#set($local = 42)\nValue: $local\nAgain: $local\n";
    client.didOpen(uri, 1, text);

    // Wait for file indexing
    Thread.sleep(150);

    List<LspLocation> locations = client.references(uri, 1, 8, true).get(5, TimeUnit.SECONDS);
    assertThat(locations).isNotEmpty();
    assertThat(locations).hasSize(3); // line 0 (#set declaration), line 1 (usage), line 2 (usage)
    for (LspLocation loc : locations) {
      assertThat(loc.uri()).isEqualTo(uri);
      assertThat(loc.range()).isNotNull();
    }
  }

  @Test
  void shouldGracefullyShutdownWithZeroProcessLeaks() throws InterruptedException {
    assertThat(client.isRunning()).isTrue();
    assertThat(spawnedProcess.isAlive()).isTrue();

    client.stop();

    assertThat(client.isRunning()).isFalse();
    boolean exited = spawnedProcess.waitFor(2, TimeUnit.SECONDS);
    assertThat(exited).isTrue();
    assertThat(spawnedProcess.isAlive()).isFalse();
  }

  private String loadFixture(String name) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
      if (in == null) {
        throw new IOException("Fixture resource not found: /fixtures/" + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private <T> List<T> pollUntilNotEmpty(java.util.function.Supplier<List<T>> supplier, long maxWaitMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + maxWaitMs;
    while (System.currentTimeMillis() < deadline) {
      List<T> list = supplier.get();
      if (list != null && !list.isEmpty()) {
        return list;
      }
      Thread.sleep(50);
    }
    return supplier.get();
  }

  private boolean pollUntil(java.util.function.BooleanSupplier condition, long maxWaitMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + maxWaitMs;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      Thread.sleep(50);
    }
    return condition.getAsBoolean();
  }
}
