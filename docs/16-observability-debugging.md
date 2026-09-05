# 16 — Observability and Debugging

## 1. Questions tooling must answer

- Which template rendered?
- Interpreter, dynamic or AOT?
- What percentage of expressions are typed/direct?
- Why is this expression dynamic?
- What template source span failed?
- What include/macro path led there?
- Are templates recompiling unexpectedly?
- Is a call site megamorphic?

## 2. Compile report

```text
Template: orders/list.vm
Backend: AOT_BYTECODE
Typed expressions: 93%
Dynamic sites: 2
Static chunks: 19
Static UTF-8 bytes: 12,842
Direct properties: 37
Macros inlined: 3
Partials: 4 direct calls
Warnings: 1
```

## 3. Explain plan

Expose span, source expression, type/nullability, access strategy, security decision, escape mode and optimization notes.

## 4. Metrics

Optional Micrometer integration outside core:

```text
viet.template.render.duration
viet.template.render.errors
viet.template.render.output.bytes
viet.template.compile.duration
viet.template.compile.errors
viet.template.cache.hit/miss
viet.template.dynamic.link
viet.template.dynamic.megamorphic
viet.template.reload.count
```

Avoid high-cardinality template-name tags by default.

## 5. Tracing

At most one normal render span, not a span per expression. Attributes may include template logical name, backend and typed flag.

## 6. Template stack

```text
orders/list.vm:42 $order.total
 called from macro priceRow at macros/order.vm:18
 parsed from orders/list.vm:31
```

## 7. Debug outputs

Allow opt-in dump of AST, analyzed AST, IR before/after passes, generated class, `javap` output and dynamic linkage trace.

## 8. Diagnostic namespaces

```text
VTLPxxxx parser
VTLSxxxx semantic
VTLSECxx security
VTLCxxxx compiler
VTLRxxxx runtime
VTLSPRxx Spring
```
