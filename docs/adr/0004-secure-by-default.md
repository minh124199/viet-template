# ADR-0004 — Secure by Default; Dynamism Is a Capability

- Status: Accepted
- Date: 2026-09-05

## Context

Template expressions that can navigate arbitrary Java objects, invoke methods, access framework containers, resolve arbitrary resources, or evaluate newly generated template text can become code-execution or data-exposure surfaces when templates are not fully trusted.

## Decision

The default production profile grants the minimum useful capabilities:

- read explicitly exposed model properties;
- perform documented pure template operations;
- render/include templates inside configured roots;
- HTML-escape interpolated values by default in HTML mode.

It does not implicitly permit:

- arbitrary method invocation;
- reflection/class loading;
- process/runtime/system access;
- access to dependency-injection containers;
- raw servlet request/response/session traversal;
- unrestricted filesystem/network access;
- `#evaluate` or equivalent runtime template compilation.

Every broader operation is represented as a capability/policy and is visible in template compile reports.

## Consequences

Some legacy Velocity templates require migration changes or explicit compatibility settings. This is intentional and must be documented rather than bypassed with hidden reflection fallbacks.
