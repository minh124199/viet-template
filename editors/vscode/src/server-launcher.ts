import * as fs from 'fs';
import * as path from 'path';
import { ServerOptions } from 'vscode-languageclient/node';
import { BUNDLED_JAR_NAME, MAIN_LSP_CLASS } from './constants';

export type LaunchMode = 'custom-jar' | 'bundled-jar' | 'dev-classpath';

export interface ServerLaunchConfig {
  command: string;
  args: string[];
  launchMode: LaunchMode;
  targetPath: string;
}

const REQUIRED_MODULES = [
  'viet-template-vtl-interpreter',
  'viet-template-language-vtl',
  'viet-template-runtime',
  'viet-template-api',
];

function findRepoRoot(startDir: string): string | undefined {
  let curr = path.resolve(startDir);
  while (curr && curr !== path.dirname(curr)) {
    if (
      fs.existsSync(path.join(curr, 'pom.xml')) &&
      fs.existsSync(path.join(curr, 'viet-template-vtl-interpreter'))
    ) {
      return curr;
    }
    curr = path.dirname(curr);
  }
  return undefined;
}

function resolveModuleClasspathEntry(repoRoot: string, moduleName: string): string | undefined {
  // 1. Maven target/classes
  const mavenClasses = path.join(repoRoot, moduleName, 'target', 'classes');
  if (fs.existsSync(mavenClasses)) {
    return mavenClasses;
  }

  // 2. Gradle build/classes/java/main
  const gradleClasses = path.join(repoRoot, moduleName, 'build', 'classes', 'java', 'main');
  if (fs.existsSync(gradleClasses)) {
    return gradleClasses;
  }

  // 3. Maven target/*.jar
  const mavenTarget = path.join(repoRoot, moduleName, 'target');
  if (fs.existsSync(mavenTarget)) {
    const files = fs.readdirSync(mavenTarget);
    const jar = files.find(
      (f) =>
        f.startsWith(moduleName) &&
        f.endsWith('.jar') &&
        !f.endsWith('-sources.jar') &&
        !f.endsWith('-javadoc.jar') &&
        !f.endsWith('-tests.jar')
    );
    if (jar) {
      return path.join(mavenTarget, jar);
    }
  }

  // 4. Gradle build/libs/*.jar
  const gradleLibs = path.join(repoRoot, moduleName, 'build', 'libs');
  if (fs.existsSync(gradleLibs)) {
    const files = fs.readdirSync(gradleLibs);
    const jar = files.find(
      (f) =>
        f.startsWith(moduleName) &&
        f.endsWith('.jar') &&
        !f.endsWith('-sources.jar') &&
        !f.endsWith('-javadoc.jar') &&
        !f.endsWith('-tests.jar')
    );
    if (jar) {
      return path.join(gradleLibs, jar);
    }
  }

  return undefined;
}

export function resolveDevClasspath(extensionPath: string): string | undefined {
  const repoRoot = findRepoRoot(extensionPath);
  if (!repoRoot) {
    return undefined;
  }

  const entries: string[] = [];
  for (const mod of REQUIRED_MODULES) {
    const entry = resolveModuleClasspathEntry(repoRoot, mod);
    if (!entry) {
      return undefined;
    }
    entries.push(entry);
  }

  return entries.join(path.delimiter);
}

export function resolveServerLaunchConfig(
  javaPath: string,
  extensionPath: string,
  customJarPath?: string,
  vmArgs: string[] = []
): ServerLaunchConfig {
  // 1. Custom JAR from configuration
  if (customJarPath && customJarPath.trim().length > 0) {
    const resolvedJar = path.resolve(customJarPath.trim());
    if (fs.existsSync(resolvedJar)) {
      return {
        command: javaPath,
        args: [...vmArgs, '-jar', resolvedJar],
        launchMode: 'custom-jar',
        targetPath: resolvedJar,
      };
    }
  }

  // 2. Bundled JAR in extension/server
  const bundledJar = path.join(extensionPath, 'server', BUNDLED_JAR_NAME);
  if (fs.existsSync(bundledJar)) {
    return {
      command: javaPath,
      args: [...vmArgs, '-jar', bundledJar],
      launchMode: 'bundled-jar',
      targetPath: bundledJar,
    };
  }

  // 3. Development classpath fallback
  const devClasspath = resolveDevClasspath(extensionPath);
  if (devClasspath) {
    return {
      command: javaPath,
      args: [...vmArgs, '-cp', devClasspath, MAIN_LSP_CLASS],
      launchMode: 'dev-classpath',
      targetPath: devClasspath,
    };
  }

  throw new Error(
    `Unable to locate Viet Template Language Server. Checked custom jar ("${customJarPath || ''}"), bundled jar ("${bundledJar}"), and development repository classes.`
  );
}

export function buildServerOptions(launchConfig: ServerLaunchConfig): ServerOptions {
  return {
    command: launchConfig.command,
    args: launchConfig.args,
    options: {
      env: process.env,
    },
  };
}
