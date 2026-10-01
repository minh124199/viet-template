import * as fs from 'fs';
import * as path from 'path';
import * as child_process from 'child_process';
import { MINIMUM_JAVA_VERSION } from './constants';

export interface JavaRuntimeInfo {
  javaPath: string;
  version: number;
  rawOutput: string;
}

export function isExecutable(filePath: string): boolean {
  try {
    const stat = fs.statSync(filePath);
    if (!stat.isFile()) {
      return false;
    }
    if (process.platform === 'win32') {
      const ext = path.extname(filePath).toLowerCase();
      return ext === '.exe' || ext === '.cmd' || ext === '.bat';
    }
    // Check execute bit on POSIX
    fs.accessSync(filePath, fs.constants.X_OK);
    return true;
  } catch {
    return false;
  }
}

export function getJavaExecutableName(): string {
  return process.platform === 'win32' ? 'java.exe' : 'java';
}

export function findJavaExecutable(
  configuredJavaHome?: string,
  envJavaHome: string | undefined = process.env.JAVA_HOME,
  pathEnv: string | undefined = process.env.PATH
): string | undefined {
  const exeName = getJavaExecutableName();

  // 1. Configured vietTemplate.java.home
  if (configuredJavaHome && configuredJavaHome.trim().length > 0) {
    const trimmed = configuredJavaHome.trim();
    // Check if directly an executable file
    if (isExecutable(trimmed)) {
      return path.resolve(trimmed);
    }
    const candidate = path.join(trimmed, 'bin', exeName);
    if (isExecutable(candidate)) {
      return path.resolve(candidate);
    }
    // Explicitly configured java home was not found
    return undefined;
  }

  // 2. JAVA_HOME environment variable
  if (envJavaHome && envJavaHome.trim().length > 0) {
    const trimmed = envJavaHome.trim();
    if (isExecutable(trimmed)) {
      return path.resolve(trimmed);
    }
    const candidate = path.join(trimmed, 'bin', exeName);
    if (isExecutable(candidate)) {
      return path.resolve(candidate);
    }
  }

  // 3. System PATH
  if (pathEnv) {
    const separator = process.platform === 'win32' ? ';' : ':';
    const dirs = pathEnv.split(separator).filter((d) => d.trim().length > 0);
    for (const dir of dirs) {
      const candidate = path.join(dir.trim(), exeName);
      if (isExecutable(candidate)) {
        return path.resolve(candidate);
      }
    }
  }

  return undefined;
}

export function parseJavaMajorVersion(versionOutput: string): number | undefined {
  if (!versionOutput) {
    return undefined;
  }
  // Matches e.g.:
  // openjdk version "21.0.12.1" 2026-08-18
  // java version "1.8.0_312"
  // openjdk version "25-ea"
  const match = /(?:openjdk|java)\s+version\s+"(\d+)(?:\.(\d+))?/i.exec(versionOutput);
  if (match) {
    const firstNum = parseInt(match[1], 10);
    if (firstNum === 1 && match[2]) {
      return parseInt(match[2], 10);
    }
    return firstNum;
  }

  // Fallback for non-standard formats (e.g., "openjdk 21 2023-09-19")
  const fallbackMatch = /(?:openjdk|java)\s+(\d+)/i.exec(versionOutput);
  if (fallbackMatch) {
    return parseInt(fallbackMatch[1], 10);
  }

  return undefined;
}

export function queryJavaVersion(javaExecutable: string): { majorVersion: number; rawOutput: string } {
  const result = child_process.spawnSync(javaExecutable, ['-version'], {
    encoding: 'utf8',
    windowsHide: true,
  });

  const output = (result.stderr || '') + (result.stdout || '');
  const version = parseJavaMajorVersion(output);

  if (version === undefined) {
    throw new Error(
      `Failed to determine Java version from executable "${javaExecutable}". Output:\n${output.trim()}`
    );
  }

  return { majorVersion: version, rawOutput: output.trim() };
}

export async function resolveJavaRuntime(configuredJavaHome?: string): Promise<JavaRuntimeInfo> {
  const javaPath = findJavaExecutable(configuredJavaHome);
  if (!javaPath) {
    throw new Error(
      `Java executable could not be found. Please ensure Java ${MINIMUM_JAVA_VERSION} or higher is installed and configured in 'vietTemplate.java.home', JAVA_HOME, or the system PATH.`
    );
  }

  const { majorVersion, rawOutput } = queryJavaVersion(javaPath);
  if (majorVersion < MINIMUM_JAVA_VERSION) {
    throw new Error(
      `Java ${MINIMUM_JAVA_VERSION} or higher is required to run the Viet Template Language Server. Found version ${majorVersion} at "${javaPath}". Please install JDK ${MINIMUM_JAVA_VERSION}+ or update 'vietTemplate.java.home'.`
    );
  }

  return {
    javaPath,
    version: majorVersion,
    rawOutput,
  };
}
