# Security Policy

## Supported Versions

Only the latest active development line is supported with security updates.

| Version | Supported | Notes |
| :--- | :--- | :--- |
| `0.1.x` | :white_check_mark: | Current active development line |
| `< 0.1.0` | :x: | Pre-release snapshots / unsupported |

## Security Model and Boundaries

Viet Template is designed to execute templates safely across different trust environments. To prevent vulnerabilities such as remote code execution, denial of service, and sensitive data leakage, Viet Template implements strict execution profiles:

1. **`VTL_SAFE` (Recommended for Untrusted Content)**:
   - **Sandbox & Member Access Policy**: Arbitrary Java reflection is strictly denied. Access to dangerous runtime classes (`Class`, `ClassLoader`, `Runtime`, `ProcessBuilder`, `Thread`, `System`, concurrency executors, etc.) is blocked by default (`MemberAccessPolicy`, `VtlSecurityPolicy`). Granular safe allowlists can be defined via `MemberAccessPolicy.allowlistBuilder()`.
   - **Sensitive Object Classifier**: `SensitiveObjectClassifier` performs zero-dependency structural hierarchy analysis to block internal runtime and container objects (e.g., Spring `ApplicationContext`, `BeanFactory`).
   - **Unified Monotonic Render Budget**: Monotonic execution budget (`RenderBudget`) enforces limits on output characters (`maxOutputCharacters`), loop iterations (`maxLoopIterations`), and wall-clock execution time (`maxExecutionTimeMillis` via `System.nanoTime()`). The budget is strictly shared across `#parse`, `#include`, `#evaluate`, macros, and screen/layout plans.
   - **Static Parser & AST Complexity Limits**: `VtlParser` enforces static limits on input size (`maxSourceCharacters`), AST node count (`maxAstNodes`), expression depth (`maxExpressionDepth`), and directive nesting depth (`maxDirectiveNesting`).
   - **Template Path Sandboxing & Root Confinement**: `TemplateId` strictly rejects null bytes (`\0`), URL-encoded traversal sequences (`%2e`, `%2f`, `%5c`, `%00`), URI schemes (`:`), and Windows drive roots (`C:`). Filesystem repositories enforce real-path confinement and reject escaping symlinks.
   - **Protected Context Variables**: Engine variables such as layout `$screen_content` are registered as protected keys (`MutableRenderContext.protectedKeys()`), preventing in-template `#set` overwrites.
   - **Cache Partitioning**: Compilation cache keys incorporate the cryptographic SHA-256 fingerprint (`SecurityPolicyFingerprint`) of the active policy.

2. **`VTL_CORE` & `VTL_MIGRATION`**:
   - Standard reference and directive resolution adhering to Velocity 2.4.1 compatibility.
   - Respects configured `ExecutionLimits`, parser limits, and reflection restrictions unless explicitly widened.

3. **`VTL_DYNAMIC`**:
   - Explicit opt-in for dynamic features such as `#evaluate` and arbitrary method invocation.
   - `#evaluate` is disabled by default in other profiles and strictly bounded when enabled.
   - Must only be used with trusted template sources.

## Reporting a Vulnerability

Viet Template takes security seriously. If you discover a security vulnerability, please report it privately:

- **GitHub Advisory (Preferred)**: Submit a draft advisory via [GitHub Private Vulnerability Reporting](https://github.com/minh124199/viet-template/security/advisories/new).
  > **Note**: GitHub Private Vulnerability Reporting requires activation by repository administrators under **Settings > Code security and analysis**. If the advisory submission link is not yet active, please reach out to the project maintainer via the contact options on GitHub ([@minh124199](https://github.com/minh124199)) to request a private channel.
- **Important**: **Do not open public GitHub issues or discussions to report exploitable security vulnerabilities or provide exploit reproductions.**

### What to Include

Please provide:
- A detailed description of the vulnerability and attack vector.
- A minimal reproducible example (template source and model bindings).
- The affected version and execution profile (e.g., `VTL_SAFE` sandbox bypass).
- An assessment of potential impact (e.g., sandbox escape, Denial of Service, information disclosure).

### Disclosure Timeline

- **Initial Response**: We will make a reasonable effort to acknowledge valid security reports promptly.
- **Triage & Fix**: We will work with the reporter to investigate, reproduce, and prepare a verified fix.
- **Advisory Release**: Coordinated public disclosure via a GitHub Security Advisory and release notes once a patch has been published.

Please do not disclose security issues publicly until a fix has been released.
