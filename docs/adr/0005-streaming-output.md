# ADR-0005 — Streaming Output Is the Runtime Primitive

- Status: Accepted
- Date: 2026-09-05

## Context

Returning a `String` from every render forces at least one complete-page buffer and can add character-to-byte conversion and copies before an HTTP response is written.

## Decision

The primary runtime contract renders to `TemplateOutput`. Implementations include Writer-backed, UTF-8 OutputStream-backed, and String-building adapters. Compiled templates emit static chunks directly and dynamic values through escaping/encoding helpers.

Convenience `String render(...)` APIs are adapters, not the internal execution model.

## Consequences

- lower allocation ceiling;
- direct servlet/output-stream integration;
- static literals can be pre-encoded for UTF-8 output;
- APIs must define I/O error behavior explicitly;
- reactive/non-blocking output requires a separate design rather than pretending a blocking output abstraction provides back-pressure.
