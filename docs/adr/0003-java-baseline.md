# ADR-0003 — Java 17 Runtime Baseline, Isolated Modern Compiler Backend

- Status: Superseded by ADR-0007
- Date: 2026-09-05 (Superseded: 2026-09-17)

> **Note:** Superseded by [ADR-0007](0007-java21-minimum-baseline.md) and [ADR-0008](0008-java25-primary-runtime.md). The project has advanced to Java 21 as the minimum baseline (`-release 21`, class file major version 65) with Java 25 as the primary runtime and build toolchain. Java 17 support is retired per [ADR-0010](0010-java17-retirement-and-compatibility.md).

## Context

Spring Framework 7 retains a Java 17 baseline, while newer JDKs provide compiler-facing facilities such as the standard Class-File API. Requiring the newest JDK at application runtime would unnecessarily reduce adoption; refusing newer compiler APIs would reduce implementation options.

## Decision

- Public runtime/core modules target Java 17 unless a module explicitly states otherwise.
- CI tests runtime behavior on Java 17, 21, and 25.
- A separate optional `compiler-jdk25` module may require JDK 25 and use `java.lang.classfile`.
- The compiler backend SPI is isolated so another backend can be supplied if required.
- Exact class-file target compatibility from the JDK-25 compiler module is a prototype/test item; no cross-target claim is made until CI verifies the resulting artifacts on each supported runtime.

## Consequences

Build-time compilation may run on a newer JDK than the deployed application. Build plugins must produce clear diagnostics when the requested backend/toolchain is unavailable.
