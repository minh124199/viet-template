import type * as vscode from 'vscode';
import {
  CONFIGURATION_SECTION,
  CONFIG_JAVA_HOME,
  CONFIG_JAR_PATH,
  CONFIG_TRACE,
  CONFIG_VM_ARGS,
  DEFAULT_TRACE,
  DEFAULT_VM_ARGS,
} from './constants';

export type TraceOption = 'off' | 'messages' | 'verbose';

export interface VietTemplateExtensionConfig {
  javaHome?: string;
  jarPath?: string;
  trace: TraceOption;
  vmArgs: string[];
}

export function parseTraceOption(value: unknown): TraceOption {
  if (value === 'messages' || value === 'verbose') {
    return value;
  }
  return DEFAULT_TRACE;
}

export function parseVmArgs(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => String(item).trim())
      .filter((item) => item.length > 0);
  }
  return [...DEFAULT_VM_ARGS];
}

export function readConfigFromGetter(
  getSetting: (key: string) => unknown
): VietTemplateExtensionConfig {
  const javaHomeRaw = getSetting(CONFIG_JAVA_HOME);
  const javaHome =
    typeof javaHomeRaw === 'string' && javaHomeRaw.trim().length > 0
      ? javaHomeRaw.trim()
      : undefined;

  const jarPathRaw = getSetting(CONFIG_JAR_PATH);
  const jarPath =
    typeof jarPathRaw === 'string' && jarPathRaw.trim().length > 0
      ? jarPathRaw.trim()
      : undefined;

  const trace = parseTraceOption(getSetting(CONFIG_TRACE));
  const vmArgs = parseVmArgs(getSetting(CONFIG_VM_ARGS));

  return {
    javaHome,
    jarPath,
    trace,
    vmArgs,
  };
}

export function getExtensionConfig(): VietTemplateExtensionConfig {
  try {
    // Dynamically require vscode so unit tests run in standard Node
    const vscode = require('vscode');
    const config = vscode.workspace.getConfiguration(CONFIGURATION_SECTION);
    return readConfigFromGetter((key: string) => config.get(key));
  } catch {
    return {
      javaHome: undefined,
      jarPath: undefined,
      trace: DEFAULT_TRACE,
      vmArgs: [...DEFAULT_VM_ARGS],
    };
  }
}
