# 19 — Risk Register

| ID | Risk | Probability | Impact | Mitigation |
|---|---|---:|---:|---|
| R1 | Compatibility scope becomes endless legacy clone | High | High | Versioned profiles + explicit unsupported list + TCK |
| R2 | Typed mode requires too much annotation | Medium | High | Infer from interfaces/signatures; optional schema files |
| R3 | PIC complexity gives little speedup | Medium | Medium | MethodHandle prototype/JMH before invokedynamic |
| R4 | Generated methods too large for JIT | Medium | High | Method-size planner and benchmark gates |
| R5 | Direct UTF-8 complexity has small benefit | Medium | Medium | Capability-based optional path; benchmark first |
| R6 | Sandbox bypass through object graph | Medium | Critical | Capability model, blocked type families, DTO guidance, review |
| R7 | `#evaluate` breaks security/AOT guarantees | High | High | Disabled default, dynamic profile only, strict budgets |
| R8 | Spring exposes container/request accidentally | Medium | High | Model-only defaults + security tests |
| R9 | JDK25 compiler limits adoption | Medium | Medium | Java17 runtime + compiler SPI/alternate backend |
| R10 | Class-File target assumptions fail | Low/Med | Medium | Prototype early; test generated classes on each runtime |
| R11 | VTL parser quirks harder than expected | High | Medium | Differential corpus from phase 1 |
| R12 | Performance does not beat typed Qute/jte | Medium | High | Handwritten baseline; allocation focus; preserve migration/tooling value |
| R13 | Benchmarks are unfair/noisy | Medium | High | Reproducible configs/raw data/equivalent feature modes |
| R14 | Hot reload leaks classloaders/metaspace | Medium | High | Generation loader + leak stress tests |
| R15 | Native dynamic path needs reflection metadata | High | Medium | Native-safe typed profile and build failures for unsupported dynamic features |
| R16 | Name/trademark confusion with Velocity | Medium | Medium | Working codename only; final naming review |
| R17 | Clean-room provenance violation | Low | High | Public docs/black-box tests; code provenance rule |
| R18 | Extension SPI becomes arbitrary scripting | Medium | High | Export/capability model |
| R19 | Auto-escaping claim exceeds actual context protection | Medium | Critical | Conservative documented contexts; audit before stronger claims |
| R20 | Too many modules slow delivery | Medium | Medium | Preserve logical boundaries but consolidate early physical modules |

## Prototype priorities

Validate parser compatibility complexity, bytecode/JDK baseline, typed API ergonomics, Qute/jte performance ceiling, and security of dynamic member access before committing to 1.0 architecture.
