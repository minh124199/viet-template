import * as vscode from 'vscode';
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
} from 'vscode-languageclient/node';
import {
  LANGUAGE_ID,
  SERVER_ID,
  SERVER_NAME,
  RESTART_SERVER_COMMAND,
} from './constants';
import { getExtensionConfig } from './configuration';
import { resolveJavaRuntime } from './java-runtime';
import { buildServerOptions, resolveServerLaunchConfig } from './server-launcher';

let client: LanguageClient | undefined;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const config = getExtensionConfig();

  let javaInfo;
  try {
    javaInfo = await resolveJavaRuntime(config.javaHome);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    vscode.window.showErrorMessage(message);
    return;
  }

  let launchConfig;
  try {
    launchConfig = resolveServerLaunchConfig(
      javaInfo.javaPath,
      context.extensionPath,
      config.jarPath,
      config.vmArgs
    );
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err);
    vscode.window.showErrorMessage(message);
    return;
  }

  const serverOptions: ServerOptions = buildServerOptions(launchConfig);

  const clientOptions: LanguageClientOptions = {
    documentSelector: [
      { scheme: 'file', language: LANGUAGE_ID },
      { scheme: 'untitled', language: LANGUAGE_ID },
    ],
    synchronize: {
      fileEvents: [
        vscode.workspace.createFileSystemWatcher('**/*.{vtl,vm,vt}'),
        vscode.workspace.createFileSystemWatcher('**/*.vt-schema.json'),
      ],
    },
  };

  client = new LanguageClient(SERVER_ID, SERVER_NAME, serverOptions, clientOptions);

  const restartCommand = vscode.commands.registerCommand(
    RESTART_SERVER_COMMAND,
    async () => {
      vscode.window.showInformationMessage(
        'Restarting Viet Template Language Server...'
      );
      try {
        if (client) {
          await client.stop();
        }
        const freshConfig = getExtensionConfig();
        const freshJava = await resolveJavaRuntime(freshConfig.javaHome);
        const freshLaunch = resolveServerLaunchConfig(
          freshJava.javaPath,
          context.extensionPath,
          freshConfig.jarPath,
          freshConfig.vmArgs
        );
        const freshServerOptions = buildServerOptions(freshLaunch);
        client = new LanguageClient(
          SERVER_ID,
          SERVER_NAME,
          freshServerOptions,
          clientOptions
        );
        await client.start();
        vscode.window.showInformationMessage(
          'Viet Template Language Server restarted successfully.'
        );
      } catch (err: unknown) {
        const errorMsg = err instanceof Error ? err.message : String(err);
        vscode.window.showErrorMessage(
          `Failed to restart Viet Template Language Server: ${errorMsg}`
        );
      }
    }
  );

  context.subscriptions.push(restartCommand);

  try {
    await client.start();
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    vscode.window.showErrorMessage(
      `Failed to start Viet Template Language Server: ${errorMsg}`
    );
  }
}

export function deactivate(): Thenable<void> | undefined {
  if (!client) {
    return undefined;
  }
  return client.stop();
}
