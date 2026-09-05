# 13 — Build Tooling, AOT and Native Image

## 1. Build-time goal

Production builds can fail before deployment when templates are syntactically/semantically invalid.

```text
compile model classes
  -> discover template schemas
  -> parse/analyze templates
  -> optimize/generate classes
  -> package classes + template index
```

## 2. Maven plugin

Publish the plugin under Maven group `io.github.minh124199` with artifact id `viet-template-maven-plugin`. Prefer `viet-template` as the plugin goal prefix.

Goals:

```text
viet-template:validate
viet-template:compile
viet-template:migration-report
viet-template:explain
```

Important config: template roots, compatibility profile, execution mode, security profile, `failOnDynamicFallback`.

Bind after model bytecode required for analysis exists, and before packaging generated classes.

## 3. Gradle plugin

The Gradle plugin should use a stable id under the project namespace, preferably `io.github.minh124199.viet-template`, subject to Gradle Plugin Portal validation.

Tasks:

```text
validateVietTemplates
compileVietTemplates
vietTemplateMigrationReport
vietTemplateExplain
```

Declare inputs for incremental builds: template sources, relevant model classpath/schema fingerprints, config, engine/compiler version and extension jars.

## 4. Generated index

```text
META-INF/viet-template/templates.idx
```

Maps logical template id to generated class, source/schema/policy fingerprint, dependencies and execution mode.

## 5. Incremental compilation

Graph:

```text
page.vm -> partial/header.vm
page.vm -> macro/forms.vm
page.vm -> model User
```

Recompile only dependents of changed source/schema/profile/policy/compiler fingerprint.

## 6. JDK strategy

- runtime baseline: Java 17;
- test 17/21/25;
- JDK Class-File backend runs on Java 25;
- alternate backend remains possible through compiler SPI.

Do not assume generated class target behavior: validate target/runtime combinations in prototype and CI.

## 7. JPMS

Introduce module descriptors after package boundaries stabilize. Keep compiler internals flexible during early development.

## 8. Native image

Recommended native-safe profile:

```text
AOT templates required
no #evaluate
no arbitrary dynamic class discovery
static include/parse targets
registered schemas for remaining dynamic data
```

Typed direct access is naturally native-friendly because reflection/resource discovery is minimized.

## 9. Spring AOT

- register runtime-loaded template resources only if still needed;
- generated classes are ordinary application classes;
- add reflection hints only for explicit dynamic fallback;
- fail build when native-safe policy sees unsupported dynamic features.

## 10. Reproducibility

Generated class names and artifacts depend on stable logical ids/hashes, not absolute workstation path or timestamp.

## 11. Debug metadata

Embed compact template/span mapping. Explore SMAP/source-debug metadata later for better stack traces/tooling.

## 12. CI matrix

```text
Java 17 core/interpreter/Spring smoke
Java 21 full general tests
Java 25 full tests + classfile compiler
Spring 7.0 / Boot 4.1 required
Spring 7.1 / Boot 4.2 preview compatibility job
```
