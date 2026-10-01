# Viet Template Language Support for Visual Studio Code

Official Visual Studio Code extension providing Language Server Protocol (LSP) intelligence, semantic diagnostics, syntax highlighting, autocompletion, hover documentation, and definition jumping for Viet Template (`*.vtl`, `*.vm`, `*.vt`) files.

## Features

- **Syntax Highlighting**: Comprehensive TextMate grammar highlighting directives (`#set`, `#if`, `#elseif`, `#else`, `#foreach`, `#macro`, `#define`, `#parse`, `#include`, `#evaluate`, `#stop`, `#break`, `#end`), variables (`$var`, `$!var`, `${var}`), expressions, string literals, and comments (`##`, `#* *#`).
- **Real-Time Semantic Diagnostics**: Immediate validation of template syntax, unmatched directives, contract schema compliance (`*.vt-schema.json`), nullable dereferences, and security boundaries.
- **Context-Sensitive Autocompletion**:
  - Directives triggered on `#`
  - In-scope template variables and schema parameters triggered on `$`
  - Object properties and model members triggered on `.`
- **Hover Documentation**: Detailed type signatures, nullability information, and documentation comments on variables, properties, and directives.
- **Go-to-Definition Navigation**: Jump directly to variable declarations (`#set`, `#foreach`) or canonical contract schema definitions (`*.vt-schema.json`).
- **Server Restart Command**: Convenient command to reboot the language server without restarting VS Code.

## Requirements

The Viet Template Language Server requires **Java 21 or higher** (JDK 21+) installed on your machine.

The extension automatically discovers Java using:
1. `vietTemplate.java.home` in VS Code settings.
2. The `JAVA_HOME` environment variable.
3. System `PATH`.

## Configuration Options

This extension contributes the following configuration settings:

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `vietTemplate.java.home` | `string` | `null` | Absolute path to the JDK 21+ installation directory. |
| `vietTemplate.languageServer.jarPath` | `string` | `null` | Path to a custom language server JAR file. If omitted, the bundled server JAR is used. |
| `vietTemplate.languageServer.trace` | `string` | `"off"` | Tracing level for LSP communications (`"off"`, `"messages"`, `"verbose"`). |
| `vietTemplate.languageServer.vmArgs` | `string[]` | `[]` | Extra JVM command-line flags (e.g. `["-Xmx512m"]`). |

## Commands

- **Viet Template: Restart Language Server** (`vietTemplate.restartServer`): Restarts the background language server process.

## Contract Schemas

To enable full static type checking, auto-completion, and definition jumping across template variables and properties, define a canonical contract schema with the naming convention:
`<template-name>.vt-schema.json` in the same directory as your template.

## License

Apache License 2.0
