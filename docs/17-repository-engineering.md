# 17 — Repository, Engineering and Release Design

## 1. Project coordinates and ownership

Initial public identity:

```text
Project:      Viet Template
Repository:   github.com/minh124199/viet-template
Maven group:  io.github.minh124199
Java package: io.github.minh124199.viettemplate
Artifacts:    viet-template-*
```

The repository begins under the maintainer's personal GitHub account (`minh124199`). An organization may be introduced later, but repository ownership and Maven/Java namespace stability are separate concerns. Do not rename published `io.github.minh124199` coordinates merely because the GitHub repository is transferred.

Package policy:

```text
io.github.minh124199.viettemplate.api
io.github.minh124199.viettemplate.source
io.github.minh124199.viettemplate.parser
io.github.minh124199.viettemplate.semantic
io.github.minh124199.viettemplate.ir
io.github.minh124199.viettemplate.runtime
io.github.minh124199.viettemplate.compiler
io.github.minh124199.viettemplate.compat.velocity
io.github.minh124199.viettemplate.spring
```

Velocity-specific names are confined to compatibility/frontend packages. Core compiler/runtime types use engine-neutral terminology.

## 2. Repository shape

```text
/
├── README.md
├── LICENSE / NOTICE / SECURITY.md / CONTRIBUTING.md
├── docs/
├── modules/
│   ├── api source parser semantics ir runtime engine
│   ├── compiler-spi compiler-interpreter compiler-dynamic compiler-jdk25
│   └── spring spring-boot-autoconfigure
├── tooling/
│   ├── maven-plugin
│   ├── gradle-plugin
│   └── migration-cli
├── tests/
│   ├── tck
│   ├── velocity-differential
│   ├── fuzz
│   └── spring-smoke
├── benchmarks/
└── examples/
```

## 3. Build system

Choose one primary root build. Maven is straightforward for Maven Central libraries; Gradle is attractive for incremental plugin development. Do not maintain two equivalent top-level builds.

## 4. Toolchains

Conceptually:

```text
most modules --release 17
compiler-jdk25 --release 25
```

## 5. Quality gates

Formatting, static checks, dependency-boundary rules, reproducible build checks, vulnerability scanning, API compatibility after 1.0, targeted mutation tests, and fuzz smoke.

## 6. Dependency policy

Ideal core:

```text
api/runtime/parser/semantics: JDK only
compiler-jdk25: JDK only
Spring adapter: Spring APIs
```

Testing/benchmarks may use richer dependencies.

## 7. Logging

Use tiny event/listener or `System.Logger`; concrete logging adapter optional. No noisy hot-path logging by default.

## 8. CI jobs

```text
format-static
unit-java17
unit-java21
unit-java25
compiler-jdk25
velocity-differential
spring-boot-4.1
spring-preview
fuzz-smoke
jmh-smoke
native-smoke
reproducible-build
```

Full benchmark/fuzz campaigns run nightly.

## 9. Release docs

Every release documents compatibility matrix, JDK/framework support, security defaults, migration notes, known limitations, benchmark report and generated-artifact compatibility.

## 10. Contribution rules

Language changes need spec + tests + compatibility/security impact. Optimization changes need semantic-equivalence tests and benchmark before/after evidence.
