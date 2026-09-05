# ADR-0002 — Compile-First Architecture With Three Execution Tiers

- Status: Accepted
- Date: 2026-09-05

## Context

A single execution mechanism creates an undesirable tradeoff: an interpreter maximizes dynamism but sacrifices the performance ceiling; fully static compilation maximizes optimization but cannot faithfully execute every legacy dynamic construct.

## Decision

Implement one language/semantic pipeline with three execution tiers:

1. **Reference interpreter** — correctness, development, diagnostics, fallback.
2. **Optimized dynamic** — cached member resolution through explicit PIC/MethodHandle machinery.
3. **Typed/AOT bytecode** — direct member calls, primitive specialization, pre-encoded static output, production path.

All tiers consume the same normalized semantic model/IR where practical and must pass the same shared TCK.

## Required property

Backend selection must be observable through compiler reports. A template must never silently drop from fully typed/AOT to a dynamic path without a reason available to tooling.

Example report:

```text
users/list.vm
  backend: AOT
  static access sites: 17
  dynamic access sites: 1
  reason for dynamic site #1: model type of $metadata is unknown
```

## Consequences

- compatibility does not permanently cap performance;
- interpreter becomes the semantic oracle for compiled backends;
- users can choose migration convenience versus strict optimization;
- implementation complexity is higher and requires backend differential testing.
