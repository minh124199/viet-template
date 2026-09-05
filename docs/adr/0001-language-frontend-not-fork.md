# ADR-0001 — VTL Compatibility Is a Frontend, Not the Internal Architecture

- Status: Accepted
- Date: 2026-09-05

## Context

The project intends to accept a useful subset/superset of Apache Velocity Template Language syntax so existing `.vm` templates can migrate with minimal rewriting. A direct fork or an AST/runtime modeled around Velocity internals would make optimization, security, typed compilation, and future syntax evolution unnecessarily dependent on historical implementation choices.

## Decision

Treat VTL-compatible syntax as one parser/frontend that lowers into a project-owned semantic model and Template IR.

```text
VTL source -> VTL lexer/parser -> project AST -> semantic model -> Template IR
                                                       |
                                      future frontend --+
```

No public runtime API is named or shaped around Apache Velocity implementation classes.

## Consequences

### Positive

- compiler/backend design remains independent;
- future syntax can reuse the same IR/runtime;
- compatibility can be versioned explicitly;
- security restrictions can intentionally differ by profile;
- internal implementation can evolve without mirroring Velocity internals.

### Negative

- compatibility requires a dedicated TCK/differential harness;
- not every Velocity extension/plugin can be reused directly;
- migration mode may need adapters rather than binary compatibility.

## Rejected alternatives

1. Fork Velocity and optimize the existing runtime: rejected because it preserves the architecture we are trying to escape.
2. Generate Velocity AST nodes and optimize them: rejected because it couples the optimizer to compatibility implementation details.
3. Promise full Velocity API compatibility: rejected for 1.0; syntax/behavior compatibility is the target, not binary API compatibility.
