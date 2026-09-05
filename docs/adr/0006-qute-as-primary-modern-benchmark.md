# ADR-0006 — Qute Typed Mode Is the Primary Modern Benchmark Comparator

- Status: Accepted
- Date: 2026-09-05

## Context

Beating traditional interpreted/general-purpose engines is not sufficient evidence that the new compiler architecture is competitive. Quarkus Qute already performs build-time validation, supports type-safe templates, minimizes reflection, and can use generated value resolvers.

## Decision

Benchmark reports must include, where technically feasible and fair:

- Apache Velocity migration/dynamic scenario;
- Thymeleaf standard scenario;
- Qute dynamic scenario;
- Qute typed/generated-resolver scenario;
- jte;
- Rocker;
- handwritten renderer.

Qute typed/generated-resolver mode is the principal modern architectural comparator. jte/Rocker and handwritten rendering define the compiled-template performance neighborhood/ceiling.

## Rules

- same logical page/data;
- equivalent escaping;
- equivalent output destination;
- template compilation excluded from render-only results;
- cold compile/startup measured separately;
- exact versions/settings published;
- throughput and allocation both reported;
- no product claim derived from third-party benchmark numbers.
