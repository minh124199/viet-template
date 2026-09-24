# Security Policy

## Supported Versions

Only the latest active release line is supported with security updates.

| Version | Supported | Notes |
| :--- | :--- | :--- |
| `0.2.2` | :white_check_mark: | Latest published stable release |
| `1.0.0-RC1` | :hourglass_flowing_sand: | Release candidate (locally staged and qualified; remote publication pending authorization) |
| `0.2.3` | :x: | Prepared/held, not released |
| `< 0.2.2` | :x: | Superseded / unsupported |

## Security Model and Boundaries

Viet Template is designed to execute templates across different trust environments. To constrain execution capabilities, resource consumption, and sensitive data leakage, Viet Template provides distinct execution profiles with explicit boundaries.

### Core Security Invariants

1. **Default Fail-Closed Member Access**: By default, `MemberAccessPolicy` operates in a strict fail-closed posture. Core denied classes (`ClassLoader`, `Runtime`, `ProcessBuilder`, `System`, `Thread`), reflection (`java.lang.reflect.*`, `java.lang.invoke.*`), and dangerous methods (`getClass`, `wait`, `notify`) cannot be accessed. `TemplateSecurityException` is thrown immediately and never converted to fallback misses or empty output.
2. **Velocity Compatibility vs. Reflection Permissiveness**: While Viet Template provides high-fidelity syntax compatibility with Apache Velocity 2.4.1 (295/301 scenarios exact, 100% accounted coverage), Velocity compatibility does **not** imply Velocity reflection permissiveness. Access to `.class` and `getClass()` is deliberately denied by default (`security.denial.class-property` and `security.denial.get-class-method`, ADR-0004).
3. **Spring Security Integration Scope**: `viet-template-spring-security` provides presentation-safe `$security` and `$csrf` template facades with contextual auto-escaping. Authorization checks fail closed and propagate security denials without masking.
4. **Quarkus Security Integration Scope**: `viet-template-quarkus` integrates optionally with Quarkus Security via `QuarkusSecurityView` without hard runtime dependencies, maintaining fail-closed authorization semantics and clean exception propagation.
5. **Native-Image Security Qualification Scope**: AOT-compiled templates and GraalVM native binaries enforce the identical `LinkerAccessPolicy` and sandbox invariants. Substrate VM native image execution does not bypass access checks.
6. **Unsupported and Experimental APIs Caveat**: APIs marked `@Experimental` or residing in internal packages (`*.internal.*`) or classified as public-but-internal debt (PBCIA) do not constitute stable security boundaries and must not be relied upon for security-critical access controls.

### Security Terminology Definitions

To prevent ambiguity, security terms are strictly distinguished across documentation, code, and APIs:

* **Escaping**: Transforms data so it cannot break the current syntax context (e.g. converting `&` to `&amp;` or `"` to `&quot;` in HTML).
* **Validation**: Determines whether input satisfies an allowed semantic policy (e.g. verifying a URL begins with an approved scheme like `https:`).
* **Sanitization**: Transforms potentially dangerous structured content into an allowed safe subset (e.g. stripping malicious HTML tags via an external HTML sanitizer).
* **Trusted**: The host application explicitly assumes full responsibility for the safety of a value.
* **SafeHtml**: Capability wrapper representing trusted HTML markup that may bypass `HTML_TEXT` auto-escaping. It does **not** perform sanitization.
* **SafeUrl**: Capability wrapper representing a URL accepted by the engine's URL validation policy or explicitly marked trusted by host application code.

---

### Execution Profiles

#### 1. `VTL_SAFE` (Sandbox Profile for Untrusted Template Source)

`VTL_SAFE` is designed for untrusted or externally editable template source when the host follows the documented embedding requirements.

It constrains template-visible Java capabilities, model mutation, dynamic evaluation, resource access, output handling, and execution budgets across AST, IR, and AOT execution.

Application security still depends on which objects and data the host places in the template context, which trusted capabilities it grants, the configured template repository, and the selected output context.

##### What `VTL_SAFE` Guarantees:
* **Mandatory non-weakening member-access policy**: Application policies cannot grant access to core denied classes (`ClassLoader`, `Runtime`, `ProcessBuilder`, `System`, `Thread`), reflection (`java.lang.reflect`, `java.lang.invoke`), or dangerous methods (`getClass`, `wait`, `notify`).
* **Arbitrary Java methods denied**: Only JavaBean getters, record components, `@TemplateCallable` methods, or explicitly allowlisted methods can be called.
* **Non-widening `@TemplateData` inheritance**: An unannotated subclass only exposes members declared on or overriding approved ancestor classes; newly declared subclass getters are denied.
* **Exact-signature `@TemplateCallable` matching**: Overloads with different parameter types do not inherit callable permission from an annotated sibling.
* **Record exposure limited to canonical components**: Records expose record components only; arbitrary methods and `Object` methods remain blocked.
* **Model mutation denied**: In-template property mutations (`#set($target.prop = ...)`) and index mutations (`#set($target[idx] = ...)`) are forbidden.
* **No property-to-method escalation**: Property syntax `$target.action` cannot invoke zero-argument action methods (e.g. `delete()`).
* **Dynamic `#evaluate` disabled**: Runtime evaluation of arbitrary strings is disabled by default in safe profile.
* **Shared monotonic render execution budgets**: A single `RenderBudget` tracks loop iterations (including zero-output loops with saturating arithmetic), output characters, and wall-clock deadlines across `#parse`, `#evaluate`, macros, and layouts without resetting.
* **Context-specific output escaping**: Auto-escapes interpolated variables based on output context (`HTML_TEXT`, `HTML_ATTRIBUTE_QUOTED`, `URL_COMPONENT`).
* **Trusted output wrappers restricted to matching contexts**: `SafeHtml` only bypasses `HTML_TEXT` (still escaped in attributes); `SafeUrl` only bypasses `URL_COMPONENT`.
* **Template repository root confinement**: Paths are normalized, traversal (`..`) and null bytes are rejected, and canonical paths are verified within the repository root.

---

#### 2. `VTL_CORE` & `VTL_MIGRATION`
Standard reference and directive resolution adhering to Velocity 2.4.1 compatibility. Respects configured `ExecutionLimits`, parser limits, and reflection restrictions unless explicitly widened.

#### 3. `VTL_DYNAMIC`
Explicit opt-in for dynamic features such as `#evaluate` and arbitrary method invocation. Must only be used with trusted template sources.

---

### Host Application Responsibilities

The sandbox cannot protect data or operations that the hosting application deliberately exposes. When embedding `VTL_SAFE` for untrusted template source, the host application is responsible for:

1. **Do not expose secrets unnecessarily**: Only bind data required for rendering into the template context.
2. **Do not expose powerful service/framework objects**: Never bind DI containers (Spring `ApplicationContext`, `BeanFactory`), database entities with lazy loading or mutation methods, persistence repositories, or stateful transactional services into the context.
3. **Do not construct trusted wrappers from untrusted input**: Never pass unvalidated user input to `SafeHtml.of(...)`, `SafeHtml.ofTrusted(...)`, or `SafeUrl.ofTrusted(...)`.
4. **Configure constrained template repositories**: Restrict template roots to dedicated directories. Do not configure root directories such as `/`, `/home`, or application code roots.
5. **Protect repository directories against concurrent writes**: Prevent untrusted local users from writing to or racing within the template directory to maintain strong symlink escape resistance.
6. **Configure realistic `ExecutionLimits`**: Set appropriate source size, AST node count, recursion depth, iteration limit, and wall-clock timeout quotas suited to available JVM memory and CPU capacity.
7. **Use the correct output context**: Specify the appropriate output context (`HTML_TEXT`, `HTML_ATTRIBUTE_QUOTED`, etc.) matching the destination document type.
8. **Treat escape hatches as privileged APIs**: Restrict access to `SafeHtml.ofTrusted` and `SafeUrl.ofTrusted` in application code reviews.

---

### Security Limitations & Threat Model

* **Deliberately exposed data**: `VTL_SAFE` cannot hide or redact data fields that the host application explicitly binds into the context.
* **Trusted escape hatches**: `SafeHtml.ofTrusted(...)` and `SafeUrl.ofTrusted(...)` bypass their respective validation or escaping protections only in the contexts for which they grant trust. Context-specific escaping rules continue to apply when those values are rendered into other output contexts.
* **Escaping vs Sanitization**: `HTML_TEXT` escaping encodes syntax characters to prevent tag injection; it is not equivalent to full HTML sanitization of complex rich-text markup.
* **URL Validation Scope**: URL scheme validation (`SafeUrlValidator`) checks for safe schemes (`https:`, `http:`, `mailto:`, `tel:`, safe relative paths); it does not guarantee that external web destinations are trustworthy or benign.
* **Filesystem Concurrency**: Canonical-path confinement hardens against symlink directory escapes and path traversal, assuming the template repository directory is not writable by an attacker actively racing filesystem modifications during template lookup.
* **Resource Limits**: Wall-clock execution limits are defensive safe-points, not hard operating-system level preemptive CPU isolation.
* **In-Process Defense-in-Depth**: In-JVM sandboxing provides defense-in-depth within a shared JVM; it does not replace process or container isolation in adversarial, untrusted multi-tenant cloud environments.

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
