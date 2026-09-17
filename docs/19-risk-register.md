# 19 — Risk Register

| ID | Risk | Probability | Impact | Mitigation | Current Status |
|---|---|---:|---:|---|---|
| R1 | Compatibility scope becomes endless legacy clone | High | High | Versioned profiles + explicit unsupported list + TCK | **Mitigated**: `VTL_CORE` profile defined, unsupported legacy directives rejected, differential TCK active across 300+ scenarios. |
| R2 | Typed mode requires too much annotation | Medium | High | Infer from interfaces/signatures; optional schema files | **Mitigated**: M15 AOT compiler infers schemas directly from Java models and records; typed models require zero template annotations. |
| R3 | PIC complexity gives little speedup | Medium | Medium | MethodHandle prototype/JMH before invokedynamic | **Mitigated**: MethodHandle PIC implemented, verified with JMH, and optimized with low-concurrency fast path in M19.3a2. |
| R4 | Generated methods too large for JIT | Medium | High | Method-size planner and benchmark gates | **Mitigated**: Method-size splitting and instruction budget enforcement implemented in bytecode compiler (`MethodSizeSplittingTest`). |
| R5 | Direct UTF-8 complexity has small benefit | Medium | Medium | Capability-based optional path; benchmark first | **Mitigated**: Zero-allocation UTF-8 streaming output and buffer pooling implemented in M19.3b with empirical throughput gains. |
| R6 | Sandbox bypass through object graph | Medium | Critical | Capability model, blocked type families, DTO guidance, review | **Mitigated**: Strict `MemberAccessPolicy` and blocked package/class mechanisms implemented and hardened with security fuzzing in M14. |
| R7 | `#evaluate` breaks security/AOT guarantees | High | High | Disabled default, dynamic profile only, strict budgets | **Mitigated**: Dynamic evaluation disabled by default, strictly rejected in AOT/production mode via `rejectRuntimeCompilation`. |
| R8 | Spring exposes container/request accidentally | Medium | High | Model-only defaults + security tests | **Mitigated**: Spring integration (M16) exposes model-only defaults; request/session attribute exposure is opt-in and tested. |
| R9 | JDK25 compiler limits adoption | Medium | Medium | Java17 runtime + compiler SPI/alternate backend | **Mitigated**: Runtime baseline strictly kept at Java 17; compiler backend isolated behind compiler SPI; CI tests JDK 17, 21, and 25. |
| R10 | Class-File target assumptions fail | Low/Med | Medium | Prototype early; test generated classes on each runtime | **Mitigated**: Java 17 classfile bytecode generation verified across JDK 17, 21, and 25 runtimes in CI matrix. |
| R11 | VTL parser quirks harder than expected | High | Medium | Differential corpus from phase 1 | **Mitigated**: Parser implemented with Pratt expression parsing, token slices, and verified against Velocity 2.4.1 test fixtures. |
| R12 | Performance does not beat typed Qute/jte | Medium | High | Handwritten baseline; allocation focus; preserve migration/tooling value | **Mitigated**: Low-allocation design, zero-alloc HTML escaping, and flat slot variable access deliver competitive performance in JMH. |
| R13 | Benchmarks are unfair/noisy | Medium | High | Reproducible configs/raw data/equivalent feature modes | **Mitigated**: Dedicated `viet-template-benchmarks` module with standardized JMH harnesses, memory profiling, and raw result capture. |
| R14 | Hot reload leaks classloaders/metaspace | Medium | High | Generation loader + leak stress tests | **Mitigated**: Generation-scoped ClassLoaders with weak-reference caches and atomic registry swap verified by `ClassLoaderLeakTest`. |
| R15 | Native dynamic path needs reflection metadata | High | Medium | Native-safe typed profile and build failures for unsupported dynamic features | **Mitigated**: AOT compiler generates direct bytecode registered in `templates.idx`; Spring AOT auto-registers hints for model types. |
| R16 | Name/trademark confusion with Velocity | Medium | Medium | Working codename only; final naming review | **Mitigated**: Project identity established as "Viet Template" under group `io.github.minh124199`; Velocity terms strictly isolated to compat. |
| R17 | Clean-room provenance violation | Low | High | Public docs/black-box tests; code provenance rule | **Mitigated**: Independent clean-room implementation from specification; differential TCK uses black-box oracle testing only. |
| R18 | Extension SPI becomes arbitrary scripting | Medium | High | Export/capability model | **Mitigated**: Explicit `RenderContextContributor` and `MemberAccessPolicy` SPIs with immutable context registration in M12.5 and M14. |
| R19 | Auto-escaping claim exceeds actual context protection | Medium | Critical | Conservative documented contexts; audit before stronger claims | **Mitigated**: HTML context escaping specified conservatively via `HtmlEscaper`, verified by fuzz tests and regression suites. |
| R20 | Too many modules slow delivery | Medium | Medium | Preserve logical boundaries but consolidate early physical modules | **Mitigated**: Consolidated conceptual architecture into 11 physical build modules while preserving strict logical package boundaries. |

## Post-Prototype & 1.0 Release Priorities

With prototype and stabilization milestones complete through Milestone M16 (including public API/SPI stabilization, AOT build tooling, and Spring integration with verified baseline Spring Framework 6.1.14 / Spring Boot 3.3.5 targeting the 6.1.x / 3.3.x line), priorities transition to 1.0 release readiness:

1. **API/SPI Binary Compatibility Enforcement**: Maintain zero breaking changes to `STABLE_API` and `STABLE_SPI` types across patch and minor releases, verified by automated compatibility tooling (`scripts/verify-api-compatibility.py`).
2. **Production Hardening in External Consuming Projects**: Validate `viet-template-spring-boot-starter` and AOT plugins against real-world Spring Boot 3.3.x enterprise applications.
3. **Comprehensive Developer Documentation**: Complete end-to-end documentation including typed model guides, AOT/native-image production configuration, and extension SPI tutorials.
4. **Final TCK & Release Gates**: Execute final independent TCK runs (Milestone M18) and cross-engine benchmark verification before tagging 1.0.0.
