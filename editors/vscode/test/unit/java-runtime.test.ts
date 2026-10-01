import * as assert from 'assert';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';
import {
  findJavaExecutable,
  parseJavaMajorVersion,
  resolveJavaRuntime,
  isExecutable,
  getJavaExecutableName,
} from '../../src/java-runtime';
import { MINIMUM_JAVA_VERSION } from '../../src/constants';

suite('Java Runtime Discovery & Validation Tests', () => {
  test('parseJavaMajorVersion correctly extracts major version across JDK formats', () => {
    assert.strictEqual(
      parseJavaMajorVersion('openjdk version "21.0.12.1" 2026-08-18'),
      21
    );
    assert.strictEqual(parseJavaMajorVersion('java version "21.0.2"'), 21);
    assert.strictEqual(parseJavaMajorVersion('openjdk version "25-ea"'), 25);
    assert.strictEqual(parseJavaMajorVersion('java version "17.0.9"'), 17);
    assert.strictEqual(parseJavaMajorVersion('java version "1.8.0_312"'), 8);
    assert.strictEqual(parseJavaMajorVersion(''), undefined);
    assert.strictEqual(parseJavaMajorVersion('unknown output'), undefined);
  });

  test('findJavaExecutable prioritizes configured home over env and path', () => {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'java-test-'));
    try {
      const binDir = path.join(tmpDir, 'bin');
      fs.mkdirSync(binDir, { recursive: true });
      const fakeJava = path.join(binDir, getJavaExecutableName());
      fs.writeFileSync(fakeJava, '#!/bin/sh\nexit 0\n');
      if (process.platform !== 'win32') {
        fs.chmodSync(fakeJava, 0o755);
      }

      // 1. Configured home
      const resolvedFromConfig = findJavaExecutable(tmpDir, undefined, undefined);
      assert.strictEqual(
        resolvedFromConfig,
        path.resolve(fakeJava),
        'Should resolve from configured Java home'
      );

      // 2. Direct binary path
      const resolvedDirect = findJavaExecutable(fakeJava, undefined, undefined);
      assert.strictEqual(
        resolvedDirect,
        path.resolve(fakeJava),
        'Should resolve direct binary path'
      );

      // 3. JAVA_HOME env
      const resolvedFromEnv = findJavaExecutable(undefined, tmpDir, undefined);
      assert.strictEqual(
        resolvedFromEnv,
        path.resolve(fakeJava),
        'Should resolve from JAVA_HOME'
      );

      // 4. Non-existent path returns undefined
      const notFound = findJavaExecutable('/non/existent/path/for/sure', undefined, '');
      assert.strictEqual(notFound, undefined);
    } finally {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  });

  test('resolveJavaRuntime successfully discovers host Java >= 21', async () => {
    const runtime = await resolveJavaRuntime();
    assert.ok(runtime.javaPath, 'javaPath must be resolved');
    assert.ok(fs.existsSync(runtime.javaPath), 'resolved javaPath must exist on disk');
    assert.ok(
      runtime.version >= MINIMUM_JAVA_VERSION,
      `Resolved Java version (${runtime.version}) must be >= ${MINIMUM_JAVA_VERSION}`
    );
  });

  test('resolveJavaRuntime throws friendly error when Java home does not exist', async () => {
    await assert.rejects(
      async () => {
        await resolveJavaRuntime('/non/existent/jdk/home/12345');
      },
      (err: Error) => {
        return (
          err.message.includes('Java executable could not be found') ||
          err.message.includes('Java 21 or higher is required')
        );
      }
    );
  });
});
