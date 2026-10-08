import * as assert from 'assert';
import * as path from 'path';
import * as fs from 'fs';
import * as child_process from 'child_process';
import { pathToFileURL } from 'url';
import { findJavaExecutable } from '../../src/java-runtime';
import { resolveServerLaunchConfig } from '../../src/server-launcher';

interface JsonRpcMessage {
  jsonrpc: '2.0';
  id?: number | string;
  method?: string;
  params?: any;
  result?: any;
  error?: any;
}

class LspStdioClient {
  private proc: child_process.ChildProcessWithoutNullStreams;
  private seq = 0;
  private pendingRequests = new Map<
    number,
    { resolve: (val: any) => void; reject: (err: any) => void }
  >();
  private notificationListeners = new Map<string, Array<(params: any) => void>>();
  private buffer = Buffer.alloc(0);

  constructor(command: string, args: string[]) {
    this.proc = child_process.spawn(command, args);

    this.proc.stdout.on('data', (chunk: Buffer) => {
      this.buffer = Buffer.concat([this.buffer, chunk]);
      this.processBuffer();
    });
  }

  private processBuffer(): void {
    while (true) {
      const headerEnd = this.buffer.indexOf('\r\n\r\n');
      if (headerEnd === -1) {
        break;
      }

      const headerText = this.buffer.subarray(0, headerEnd).toString('utf8');
      const match = /Content-Length:\s*(\d+)/i.exec(headerText);
      if (!match) {
        throw new Error(`Malformed LSP header: ${headerText}`);
      }

      const contentLength = parseInt(match[1], 10);
      const totalMessageLength = headerEnd + 4 + contentLength;
      if (this.buffer.length < totalMessageLength) {
        break;
      }

      const bodyBytes = this.buffer.subarray(headerEnd + 4, totalMessageLength);
      this.buffer = this.buffer.subarray(totalMessageLength);

      const messageStr = bodyBytes.toString('utf8');
      const message: JsonRpcMessage = JSON.parse(messageStr);
      this.handleMessage(message);
    }
  }

  private handleMessage(msg: JsonRpcMessage): void {
    if (msg.id !== undefined && (msg.result !== undefined || msg.error !== undefined)) {
      const idNum = typeof msg.id === 'string' ? parseInt(msg.id, 10) : msg.id;
      const handler = this.pendingRequests.get(idNum);
      if (handler) {
        this.pendingRequests.delete(idNum);
        if (msg.error) {
          handler.reject(new Error(`LSP Error [${msg.error.code}]: ${msg.error.message}`));
        } else {
          handler.resolve(msg.result);
        }
      }
    } else if (msg.method) {
      const listeners = this.notificationListeners.get(msg.method) || [];
      for (const listener of listeners) {
        listener(msg.params);
      }
    }
  }

  public sendRequest(method: string, params: any): Promise<any> {
    const id = ++this.seq;
    return new Promise((resolve, reject) => {
      this.pendingRequests.set(id, { resolve, reject });
      this.writePayload({ jsonrpc: '2.0', id, method, params });
    });
  }

  public sendNotification(method: string, params: any): void {
    this.writePayload({ jsonrpc: '2.0', method, params });
  }

  public onNotification(method: string, listener: (params: any) => void): void {
    const list = this.notificationListeners.get(method) || [];
    list.push(listener);
    this.notificationListeners.set(method, list);
  }

  public waitForNotification(
    method: string,
    predicate: (params: any) => boolean = () => true,
    timeoutMs = 10000
  ): Promise<any> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        reject(new Error(`Timeout (${timeoutMs}ms) waiting for notification '${method}'`));
      }, timeoutMs);

      const listener = (params: any) => {
        if (predicate(params)) {
          clearTimeout(timer);
          const list = this.notificationListeners.get(method) || [];
          const idx = list.indexOf(listener);
          if (idx !== -1) {
            list.splice(idx, 1);
          }
          resolve(params);
        }
      };

      this.onNotification(method, listener);
    });
  }

  private writePayload(payload: JsonRpcMessage): void {
    const json = JSON.stringify(payload);
    const bodyBuf = Buffer.from(json, 'utf8');
    const header = `Content-Length: ${bodyBuf.length}\r\n\r\n`;
    this.proc.stdin.write(header);
    this.proc.stdin.write(bodyBuf);
  }

  public async terminate(): Promise<number | null> {
    return new Promise((resolve) => {
      if (this.proc.exitCode !== null) {
        resolve(this.proc.exitCode);
        return;
      }
      this.proc.once('close', (code) => resolve(code));
      this.proc.kill('SIGTERM');
      setTimeout(() => {
        if (this.proc.exitCode === null) {
          this.proc.kill('SIGKILL');
        }
      }, 2000);
    });
  }

  public get process(): child_process.ChildProcessWithoutNullStreams {
    return this.proc;
  }
}

suite('E2E LSP Server Smoke & Protocol Lifecycle Tests', () => {
  let client: LspStdioClient;
  const extensionDir = path.resolve(__dirname, '..', '..', '..');
  const fixturesDir = path.join(extensionDir, 'test', 'fixtures');

  function fixtureUri(filename: string): string {
    return pathToFileURL(path.join(fixturesDir, filename)).href;
  }

  function fixtureText(filename: string): string {
    return fs.readFileSync(path.join(fixturesDir, filename), 'utf8');
  }

  suiteSetup(async () => {
    const javaPath = findJavaExecutable();
    assert.ok(javaPath, 'Host Java executable must be discoverable');

    const launchConfig = resolveServerLaunchConfig(javaPath, extensionDir);
    client = new LspStdioClient(launchConfig.command, launchConfig.args);
  });

  suiteTeardown(async () => {
    if (client) {
      await client.terminate();
    }
  });

  test('Step 1: LSP Initialize handshake returns required server capabilities', async () => {
    const initParams = {
      processId: process.pid,
      rootUri: pathToFileURL(fixturesDir).href,
      capabilities: {},
      workspaceFolders: [
        {
          uri: pathToFileURL(fixturesDir).href,
          name: 'fixtures',
        },
      ],
    };

    const initResult = await client.sendRequest('initialize', initParams);
    assert.ok(initResult, 'initialize response must not be null');
    assert.ok(initResult.capabilities, 'capabilities must be present');

    // textDocumentSync = 1 (Full)
    assert.strictEqual(
      initResult.capabilities.textDocumentSync,
      1,
      'textDocumentSync should be 1 (Full)'
    );

    // completionProvider
    assert.ok(
      initResult.capabilities.completionProvider,
      'completionProvider must be enabled'
    );
    assert.ok(
      Array.isArray(initResult.capabilities.completionProvider.triggerCharacters),
      'completion trigger characters must be defined'
    );

    // hoverProvider & definitionProvider
    assert.strictEqual(
      initResult.capabilities.hoverProvider,
      true,
      'hoverProvider must be true'
    );
    assert.strictEqual(
      initResult.capabilities.definitionProvider,
      true,
      'definitionProvider must be true'
    );
    assert.strictEqual(
      initResult.capabilities.referencesProvider,
      true,
      'referencesProvider must be true'
    );
    assert.deepStrictEqual(
      initResult.capabilities.renameProvider,
      { prepareProvider: true },
      'renameProvider must be advertised'
    );

    // Send initialized notification
    client.sendNotification('initialized', {});
  });

  test('Step 2: Opens valid.vtl and receives clean empty diagnostics', async () => {
    const uri = fixtureUri('valid.vtl');
    const text = fixtureText('valid.vtl');

    const diagPromise = client.waitForNotification(
      'textDocument/publishDiagnostics',
      (p) => p.uri === uri
    );

    client.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri,
        languageId: 'viet-template',
        version: 1,
        text,
      },
    });

    const diags = await diagPromise;
    assert.strictEqual(diags.uri, uri);
    assert.deepStrictEqual(
      diags.diagnostics,
      [],
      'valid.vtl should produce 0 diagnostics'
    );
  });

  test('Step 3: Opens syntax-error.vtl and receives syntax error diagnostic', async () => {
    const uri = fixtureUri('syntax-error.vtl');
    const text = fixtureText('syntax-error.vtl');

    const diagPromise = client.waitForNotification(
      'textDocument/publishDiagnostics',
      (p) => p.uri === uri
    );

    client.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri,
        languageId: 'viet-template',
        version: 1,
        text,
      },
    });

    const diags = await diagPromise;
    assert.strictEqual(diags.uri, uri);
    assert.ok(
      diags.diagnostics.length > 0,
      'syntax-error.vtl must produce at least one diagnostic'
    );
    assert.strictEqual(diags.diagnostics[0].severity, 1, 'Severity must be Error (1)');
  });

  test('Step 4: Queries completion on completion.vtl and receives variable suggestions', async () => {
    const uri = fixtureUri('completion.vtl');
    const text = fixtureText('completion.vtl');

    client.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri,
        languageId: 'viet-template',
        version: 1,
        text,
      },
    });

    // In completion.vtl, line 1 is "$var"
    const compResult = await client.sendRequest('textDocument/completion', {
      textDocument: { uri },
      position: { line: 1, character: 4 },
    });

    assert.ok(compResult, 'completion result must not be null');
    const items = Array.isArray(compResult) ? compResult : compResult.items;
    assert.ok(Array.isArray(items), 'completion items must be an array');
    const varItem = items.find((i: any) => i.label === 'var');
    assert.ok(varItem, 'completion items must contain $var defined in template');
  });

  test('Step 5: Queries hover on hover.vtl and receives documentation', async () => {
    const uri = fixtureUri('hover.vtl');
    const text = fixtureText('hover.vtl');

    client.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri,
        languageId: 'viet-template',
        version: 1,
        text,
      },
    });

    // In hover.vtl, line 1 is "#if($user)" - hover on "#if"
    const hoverResult = await client.sendRequest('textDocument/hover', {
      textDocument: { uri },
      position: { line: 1, character: 1 },
    });

    assert.ok(hoverResult, 'hover result must not be null');
    assert.ok(hoverResult.contents, 'hover contents must be present');
    const md =
      typeof hoverResult.contents === 'string'
        ? hoverResult.contents
        : hoverResult.contents.value;
    assert.ok(md.includes('#if'), 'Hover markdown must document #if directive');
  });

  test('Step 6: Queries definition on definition.vtl and receives navigation location', async () => {
    const uri = fixtureUri('definition.vtl');
    const text = fixtureText('definition.vtl');

    client.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri,
        languageId: 'viet-template',
        version: 1,
        text,
      },
    });

    // Line 1 is "$user" (character 2) - should navigate to line 0 (#set($user = 'Alice'))
    const defResult = await client.sendRequest('textDocument/definition', {
      textDocument: { uri },
      position: { line: 1, character: 2 },
    });

    assert.ok(defResult, 'definition result must not be null');
    const loc = Array.isArray(defResult) ? defResult[0] : defResult;
    assert.ok(loc, 'At least one location must be returned');
    assert.strictEqual(loc.uri, uri);
    assert.strictEqual(
      loc.range.start.line,
      0,
      'Definition of $user should point to line 0 (#set)'
    );

    // Line 2 is "$account.email" - character 10 (email property)
    // Should navigate to definition.vt-schema.json
    const schemaDefResult = await client.sendRequest('textDocument/definition', {
      textDocument: { uri },
      position: { line: 2, character: 10 },
    });

    assert.ok(schemaDefResult, 'schema definition result must not be null');
    const schemaLoc = Array.isArray(schemaDefResult)
      ? schemaDefResult[0]
      : schemaDefResult;
    assert.ok(schemaLoc, 'Schema property location must be returned');
    assert.ok(
      schemaLoc.uri.endsWith('definition.vt-schema.json'),
      `Definition should point to definition.vt-schema.json, got: ${schemaLoc.uri}`
    );
  });

  test('Step 7: Queries references on definition.vtl and receives template usages', async () => {
    const uri = fixtureUri('definition.vtl');

    // Query references for $user on line 1, character 2 with includeDeclaration = true
    const refResult = await client.sendRequest('textDocument/references', {
      textDocument: { uri },
      position: { line: 1, character: 2 },
      context: { includeDeclaration: true },
    });

    assert.ok(Array.isArray(refResult), 'references result must be an array');
    assert.ok(
      refResult.length >= 1,
      `At least 1 reference for $user expected, got ${refResult.length}`
    );
    assert.ok(
      refResult.some((r: any) => r.uri === uri),
      'references must contain definition.vtl uri'
    );
  });

  test('Step 8: Queries prepareRename and rename on definition.vtl', async () => {
    const uri = fixtureUri('definition.vtl');

    // Query prepareRename for $user on line 1, character 2
    const prepResult = await client.sendRequest('textDocument/prepareRename', {
      textDocument: { uri },
      position: { line: 1, character: 2 },
    });

    assert.ok(prepResult, 'prepareRename result must not be null');
    assert.strictEqual(prepResult.placeholder, 'user');
    assert.strictEqual(prepResult.range.start.line, 1);

    // Query rename for $user to 'admin'
    const renameResult = await client.sendRequest('textDocument/rename', {
      textDocument: { uri },
      position: { line: 1, character: 2 },
      newName: 'admin',
    });

    assert.ok(renameResult, 'rename result must not be null');
    assert.ok(renameResult.changes, 'rename result must contain changes');
    assert.ok(renameResult.changes[uri], 'rename changes must contain definition.vtl');
    const edits = renameResult.changes[uri];
    assert.ok(edits.length >= 2, 'Must contain declaration and reference edits');
    assert.ok(edits.every((e: any) => e.newText === 'admin'));
  });

  test('Step 9: Clean shutdown and exit terminates process with code 0', async () => {
    const shutdownResult = await client.sendRequest('shutdown', null);
    assert.strictEqual(
      shutdownResult,
      null,
      'shutdown result must be null per LSP spec'
    );

    const exitPromise = new Promise<number>((resolve) => {
      client.process.once('close', (code) => resolve(code ?? 0));
    });

    client.sendNotification('exit', null);
    const exitCode = await exitPromise;
    assert.strictEqual(exitCode, 0, 'Server process should exit with code 0');
  });
});
