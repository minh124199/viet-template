import * as assert from 'assert';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import {
  resolveServerLaunchConfig,
  buildServerOptions,
  resolveDevClasspath,
} from '../../src/server-launcher';
import { BUNDLED_JAR_NAME, MAIN_LSP_CLASS } from '../../src/constants';

suite('Server Launcher Resolution & Argument Construction Tests', () => {
  const fakeJava = '/usr/bin/java';

  test('resolves custom jar when configured and exists', () => {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'launcher-custom-'));
    try {
      const customJar = path.join(tmpDir, 'custom-server.jar');
      fs.writeFileSync(customJar, 'fake-jar-data');

      const config = resolveServerLaunchConfig(
        fakeJava,
        tmpDir,
        customJar,
        ['-Xmx256m']
      );

      assert.strictEqual(config.launchMode, 'custom-jar');
      assert.strictEqual(config.command, fakeJava);
      assert.deepStrictEqual(config.args, ['-Xmx256m', '-jar', path.resolve(customJar)]);
    } finally {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  });

  test('resolves bundled jar when present in extension server directory', () => {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'launcher-bundled-'));
    try {
      const serverDir = path.join(tmpDir, 'server');
      fs.mkdirSync(serverDir, { recursive: true });
      const bundledJar = path.join(serverDir, BUNDLED_JAR_NAME);
      fs.writeFileSync(bundledJar, 'fake-bundled-jar-data');

      const config = resolveServerLaunchConfig(fakeJava, tmpDir, undefined, [
        '-Xms128m',
      ]);

      assert.strictEqual(config.launchMode, 'bundled-jar');
      assert.strictEqual(config.command, fakeJava);
      assert.deepStrictEqual(config.args, ['-Xms128m', '-jar', bundledJar]);
    } finally {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  });

  test('resolves dev classpath when running inside repo without bundled jar', () => {
    // Current repo has target/classes for all modules
    const extensionDir = path.resolve(__dirname, '..', '..', '..'); // editors/vscode
    const classpath = resolveDevClasspath(extensionDir);
    assert.ok(classpath, 'Dev classpath should be discoverable in repo');
    assert.ok(
      classpath.includes('viet-template-vtl-interpreter'),
      'Classpath must contain interpreter module'
    );
    assert.ok(
      classpath.includes('viet-template-language-vtl'),
      'Classpath must contain language-vtl module'
    );
    assert.ok(
      classpath.includes('viet-template-runtime'),
      'Classpath must contain runtime module'
    );
    assert.ok(
      classpath.includes('viet-template-api'),
      'Classpath must contain api module'
    );

    // If we pass an extensionPath inside the repo without a bundled jar
    const tmpSubdirInRepo = path.join(extensionDir, 'scratch-dev-test');
    fs.mkdirSync(tmpSubdirInRepo, { recursive: true });
    try {
      const config = resolveServerLaunchConfig(
        fakeJava,
        tmpSubdirInRepo,
        undefined,
        ['-Dcustom.prop=true']
      );

      assert.strictEqual(config.launchMode, 'dev-classpath');
      assert.strictEqual(config.command, fakeJava);
      assert.strictEqual(config.args[0], '-Dcustom.prop=true');
      assert.strictEqual(config.args[1], '-cp');
      assert.strictEqual(config.args[3], MAIN_LSP_CLASS);
    } finally {
      fs.rmSync(tmpSubdirInRepo, { recursive: true, force: true });
    }
  });

  test('buildServerOptions maps launch config to language client options', () => {
    const launchConfig = {
      command: fakeJava,
      args: ['-jar', '/path/to/server.jar'],
      launchMode: 'custom-jar' as const,
      targetPath: '/path/to/server.jar',
    };

    const serverOptions: any = buildServerOptions(launchConfig);
    assert.strictEqual(serverOptions.command, fakeJava);
    assert.deepStrictEqual(serverOptions.args, ['-jar', '/path/to/server.jar']);
    assert.ok(serverOptions.options?.env);
  });

  test('throws error when no jar or dev classpath can be found', () => {
    const tmpEmpty = fs.mkdtempSync(path.join(os.tmpdir(), 'launcher-empty-'));
    try {
      assert.throws(() => {
        resolveServerLaunchConfig(fakeJava, tmpEmpty, '/non/existent/jar.jar');
      }, /Unable to locate Viet Template Language Server/);
    } finally {
      fs.rmSync(tmpEmpty, { recursive: true, force: true });
    }
  });
});
