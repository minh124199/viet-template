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
   - **Sandbox Policy**: Arbitrary Java reflection is strictly denied. Access to dangerous runtime classes (`Class`, `ClassLoader`, `Runtime`, `ProcessBuilder`, `Thread`, `System`, etc.) is blocked by default (`VtlSecurityPolicy`).
   - **Resource Limits**: Configurable execution limits (`ExecutionLimits`) guard against algorithmic complexity attacks, infinite loops (`maxLoopIterations`), deep recursion (`maxMacroDepth`, `maxParseDepth`), and memory exhaustion (`maxOutputCharacters`).
   - **Template Path Sandboxing**: `TemplateId` strictly forbids root access (`/`), Windows backslashes (`\`), and path traversal sequences (`..`).

2. **`VTL_CORE` & `VTL_MIGRATION`**:
   - Standard reference and directive resolution adhering to Velocity 2.4.1 compatibility.
   - Respects configured `ExecutionLimits` and reflection restrictions unless explicitly widened.

3. **`VTL_DYNAMIC`**:
   - Explicit opt-in for dynamic features such as `#evaluate` and arbitrary method invocation.
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
