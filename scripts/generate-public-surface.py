#!/usr/bin/env python3
"""
generate-public-surface.py

Fast batch javap inspecting all compiled classes across production modules:
- viet-template-api
- viet-template-runtime
- viet-template-language-vtl
- viet-template-vtl-interpreter

Generates config/api-baseline/public-surface-classification.txt sorted deterministically.
Classifies each public/protected type into one of four deterministic categories:
- STABLE_API: Consumer-facing contracts under long-term 1.x compatibility guarantee.
- STABLE_SPI: Extensibility and service-provider interfaces for third-party implementors.
- EXPERIMENTAL: Emerging language/tooling APIs subject to evolution before 1.0.
- PUBLIC_BUT_INTERNAL_ACCIDENT: Classes made public due to package boundaries prior to modularization.
"""

import os
import sys
import subprocess
import argparse

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
BASELINE_API_FILE = os.path.join(REPO_ROOT, "config/api-baseline/1.0-public-api.txt")
OUTPUT_FILE = os.path.join(REPO_ROOT, "config/api-baseline/public-surface-classification.txt")

JAVAP = os.environ.get("JAVAP_BIN")
if not JAVAP:
    candidates = [
        "/home/lynguyen/opt/usr/lib/jvm/java-17-openjdk/bin/javap",
        "/usr/lib/jvm/java-17-openjdk/bin/javap",
        "javap",
    ]
    for c in candidates:
        if os.path.exists(c) or c == "javap":
            JAVAP = c
            break

MODULES = [
    "viet-template-api",
    "viet-template-runtime",
    "viet-template-language-vtl",
    "viet-template-vtl-interpreter",
    "viet-template-spring",
    "viet-template-spring-boot-autoconfigure",
    "viet-template-spring-security",
]

FULL_CP = ":".join([os.path.join(REPO_ROOT, m, "build/classes/java/main") for m in MODULES])

STABLE_SPI_SET = {
    "io.github.minh124199.viettemplate.api.TemplateRepository",
    "io.github.minh124199.viettemplate.api.TemplateOutput",
    "io.github.minh124199.viettemplate.api.TemplateEngineProvider",
    "io.github.minh124199.viettemplate.api.RenderContextContributor",
    "io.github.minh124199.viettemplate.api.ContributorContext",
    "io.github.minh124199.viettemplate.api.LayoutResolver",
    "io.github.minh124199.viettemplate.api.LayoutRenderPlan",
    "io.github.minh124199.viettemplate.api.MemberAccessPolicy",
    "io.github.minh124199.viettemplate.api.MemberAccessPolicy$Builder",
    "io.github.minh124199.viettemplate.runtime.Escaper",
    "io.github.minh124199.viettemplate.runtime.EscapeMode",
    "io.github.minh124199.viettemplate.runtime.EscaperRegistry",
    "io.github.minh124199.viettemplate.runtime.SafeContent",
    "io.github.minh124199.viettemplate.runtime.StandardEscapers",
    "io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateEngineCustomizer",
    "io.github.minh124199.viettemplate.spring.web.servlet.SpringRenderAttributes",
    "io.github.minh124199.viettemplate.spring.security.SecurityViewFactory",
    "io.github.minh124199.viettemplate.spring.security.CsrfViewFactory",
    "io.github.minh124199.viettemplate.spring.security.SpringSecurityRenderContextContributor",
}

EXPERIMENTAL_SET = {
    "io.github.minh124199.viettemplate.language.vtl.VtlFeatureState",
    "io.github.minh124199.viettemplate.language.vtl.VtlFrontend",
    "io.github.minh124199.viettemplate.language.vtl.VtlProfile",
    "io.github.minh124199.viettemplate.language.vtl.parser.VtlParseResult",
    "io.github.minh124199.viettemplate.language.vtl.parser.VtlParser",
    "io.github.minh124199.viettemplate.language.vtl.parser.VtlParserOptions",
}


STABLE_API_EXTRA_SET = {
    "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateAutoConfiguration",
    "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateProperties",
    "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateProperties$Security",
    "io.github.minh124199.viettemplate.spring.boot.autoconfigure.VietTemplateSecurityAutoConfiguration",
    "io.github.minh124199.viettemplate.spring.security.CsrfView",
    "io.github.minh124199.viettemplate.spring.security.SecurityView",
    "io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView",
    "io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateViewResolver",
}


def load_baseline_api_types(path):
    if not os.path.exists(path):
        return set()
    types = set()
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith("TYPE "):
                parts = line.split()
                for i, p in enumerate(parts):
                    if p in ("class", "interface", "enum", "record", "@interface") and i + 1 < len(parts):
                        cls = parts[i + 1].split("<")[0]
                        types.add(cls)
    return types


def get_public_types():
    all_classes = []
    for mod in MODULES:
        classes_dir = os.path.join(REPO_ROOT, mod, "build/classes/java/main")
        if not os.path.exists(classes_dir):
            continue
        for root, _, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class") and not f.endswith("package-info.class"):
                    rel = os.path.relpath(os.path.join(root, f), classes_dir)
                    cls_name = rel[:-6].replace(os.sep, ".")
                    all_classes.append(cls_name)

    all_classes.sort()
    pub_types = set()
    batch_size = 100
    for i in range(0, len(all_classes), batch_size):
        batch = all_classes[i : i + batch_size]
        cmd = [JAVAP, "-protected", "-cp", FULL_CP] + batch
        proc = subprocess.run(cmd, capture_output=True, text=True)
        for line in proc.stdout.splitlines():
            l = line.strip()
            if (
                "class " in l
                or "interface " in l
                or "enum " in l
                or "record " in l
                or "@interface " in l
            ) and not l.startswith("Compiled"):
                if l.startswith("public ") or l.startswith("protected "):
                    parts = l.split()
                    for idx, p in enumerate(parts):
                        if (
                            p in ("class", "interface", "enum", "record", "@interface")
                            and idx + 1 < len(parts)
                        ):
                            c = parts[idx + 1].split("<")[0]
                            pub_types.add(c)
                            break

    return sorted(pub_types)


def classify_types(public_types, baseline_api_types):
    classified = []
    counts = {
        "STABLE_API": 0,
        "STABLE_SPI": 0,
        "EXPERIMENTAL": 0,
        "PUBLIC_BUT_INTERNAL_ACCIDENT": 0,
    }

    for cls in public_types:
        if cls in STABLE_SPI_SET:
            cat = "STABLE_SPI"
        elif cls in EXPERIMENTAL_SET:
            cat = "EXPERIMENTAL"
        elif cls in baseline_api_types or cls in STABLE_API_EXTRA_SET:
            cat = "STABLE_API"
        else:
            cat = "PUBLIC_BUT_INTERNAL_ACCIDENT"

        counts[cat] += 1
        classified.append((cls, cat))

    return classified, counts


def main():
    parser = argparse.ArgumentParser(description="Generate public surface classification baseline.")
    parser.add_argument("--output", "-o", default=OUTPUT_FILE, help="Path to write classification file")
    args = parser.parse_args()

    baseline_api = load_baseline_api_types(BASELINE_API_FILE)
    public_types = get_public_types()
    classified, counts = classify_types(public_types, baseline_api)

    lines = [
        "# Viet Template Public Surface Classification Baseline",
        f"# Total Types: {len(classified)}",
        f"# STABLE_API: {counts['STABLE_API']}",
        f"# STABLE_SPI: {counts['STABLE_SPI']}",
        f"# EXPERIMENTAL: {counts['EXPERIMENTAL']}",
        f"# PUBLIC_BUT_INTERNAL_ACCIDENT: {counts['PUBLIC_BUT_INTERNAL_ACCIDENT']}",
        "#",
        "# Categories:",
        "#   STABLE_API: Consumer-facing contracts under long-term 1.x compatibility guarantee",
        "#   STABLE_SPI: Extensibility and service-provider interfaces for third-party implementors",
        "#   EXPERIMENTAL: Emerging language/tooling APIs subject to evolution before 1.0",
        "#   PUBLIC_BUT_INTERNAL_ACCIDENT: Classes made public due to package boundaries prior to modularization",
        "#",
        "# Format: <FULLY_QUALIFIED_CLASS_NAME> <CATEGORY>",
        "",
    ]

    for cls, cat in sorted(classified, key=lambda x: x[0]):
        lines.append(f"{cls} {cat}")

    lines.append("")

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"Generated public surface classification for {len(classified)} types:")
    print(f"  STABLE_API: {counts['STABLE_API']}")
    print(f"  STABLE_SPI: {counts['STABLE_SPI']}")
    print(f"  EXPERIMENTAL: {counts['EXPERIMENTAL']}")
    print(f"  PUBLIC_BUT_INTERNAL_ACCIDENT: {counts['PUBLIC_BUT_INTERNAL_ACCIDENT']}")
    print(f"Written to: {args.output}")


if __name__ == "__main__":
    main()
