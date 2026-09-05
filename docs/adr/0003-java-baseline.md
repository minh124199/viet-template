# ADR-0003 — Java 17 Runtime Baseline, Isolated Modern Compiler Backend

- Status: Accepted with validation requirement
- Date: 2026-09-05

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
