import * as assert from 'assert';
import {
  parseTraceOption,
  parseVmArgs,
  readConfigFromGetter,
  getExtensionConfig,
} from '../../src/configuration';
import {
  CONFIG_JAVA_HOME,
  CONFIG_JAR_PATH,
  CONFIG_TRACE,
  CONFIG_VM_ARGS,
  DEFAULT_TRACE,
} from '../../src/constants';

suite('Configuration Reader & Fallback Tests', () => {
  test('parseTraceOption parses valid trace levels and applies fallback', () => {
    assert.strictEqual(parseTraceOption('off'), 'off');
    assert.strictEqual(parseTraceOption('messages'), 'messages');
    assert.strictEqual(parseTraceOption('verbose'), 'verbose');
    assert.strictEqual(parseTraceOption('invalid'), DEFAULT_TRACE);
    assert.strictEqual(parseTraceOption(null), DEFAULT_TRACE);
    assert.strictEqual(parseTraceOption(undefined), DEFAULT_TRACE);
    assert.strictEqual(parseTraceOption(123), DEFAULT_TRACE);
  });

  test('parseVmArgs cleans up arguments and returns default on non-array', () => {
    assert.deepStrictEqual(
      parseVmArgs([' -Xmx512m ', '-Dprop=true', '', '   ']),
      ['-Xmx512m', '-Dprop=true']
    );
    assert.deepStrictEqual(parseVmArgs([]), []);
    assert.deepStrictEqual(parseVmArgs(null), []);
    assert.deepStrictEqual(parseVmArgs(undefined), []);
    assert.deepStrictEqual(parseVmArgs('not-an-array'), []);
  });

  test('readConfigFromGetter correctly maps workspace settings to config object', () => {
    const store: Record<string, unknown> = {
      [CONFIG_JAVA_HOME]: '  /usr/lib/jvm/java-21  ',
      [CONFIG_JAR_PATH]: '  /custom/lsp.jar  ',
      [CONFIG_TRACE]: 'verbose',
      [CONFIG_VM_ARGS]: ['-Xmx1g'],
    };

    const config = readConfigFromGetter((key) => store[key]);
    assert.strictEqual(config.javaHome, '/usr/lib/jvm/java-21');
    assert.strictEqual(config.jarPath, '/custom/lsp.jar');
    assert.strictEqual(config.trace, 'verbose');
    assert.deepStrictEqual(config.vmArgs, ['-Xmx1g']);
  });

  test('readConfigFromGetter returns safe defaults when settings are empty or null', () => {
    const config = readConfigFromGetter(() => undefined);
    assert.strictEqual(config.javaHome, undefined);
    assert.strictEqual(config.jarPath, undefined);
    assert.strictEqual(config.trace, 'off');
    assert.deepStrictEqual(config.vmArgs, []);
  });

  test('getExtensionConfig returns safe fallback in non-VSCode runner environments', () => {
    const config = getExtensionConfig();
    assert.ok(config);
    assert.strictEqual(config.trace, 'off');
    assert.deepStrictEqual(config.vmArgs, []);
  });
});
