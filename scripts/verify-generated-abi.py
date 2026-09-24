#!/usr/bin/env python3
"""
scripts/verify-generated-abi.py

Mechanically audits the runtime ABI of generated AOT template classfiles.
1. Locates compiled template classes (from build/reports/generated-template-abi-classes or integration fixtures).
2. Parses constant pool entries via `javap -verbose`.
3. Decodes JVM method descriptors to Java method signatures.
4. Parses the runtime ABI baseline at `config/api-baseline/generated-template-runtime-abi.txt`.
5. Validates referenced types, invoked methods, and accessed fields against baseline definitions:
   - Asserts member-level matching (exact 7 types, 22 invoked methods, 0 fields).
   - Detects and fails on any unregistered types, methods, or fields.
6. Correlates types against `config/api-baseline/public-surface-classification.txt`.
7. Generates a structured, machine-readable report at `build/reports/generated-template-abi.json`.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parent.parent
DEFAULT_BASELINE = ROOT_DIR / "config" / "api-baseline" / "generated-template-runtime-abi.txt"
DEFAULT_CLASSIFICATION = ROOT_DIR / "config" / "api-baseline" / "public-surface-classification.txt"
DEFAULT_REPORT = ROOT_DIR / "build" / "reports" / "generated-template-abi.json"

JAVA_MODIFIERS = {
    "public",
    "protected",
    "private",
    "static",
    "final",
    "abstract",
    "synchronized",
    "native",
    "strictfp",
    "default",
    "transient",
    "volatile",
}

BASE_TYPE_DESCRIPTORS = {
    "B": "byte",
    "C": "char",
    "D": "double",
    "F": "float",
    "I": "int",
    "J": "long",
    "S": "short",
    "Z": "boolean",
    "V": "void",
}


def decode_type_descriptor(desc: str, pos: int = 0) -> tuple[str, int]:
    """Decodes a single JVM type descriptor starting at pos.

    Returns (java_type_name, next_pos).
    Raises ValueError on malformed descriptors.
    """
    if pos >= len(desc):
        raise ValueError(f"Unexpected end of descriptor: '{desc}'")

    array_dim = 0
    while pos < len(desc) and desc[pos] == "[":
        array_dim += 1
        pos += 1

    if pos >= len(desc):
        raise ValueError(f"Descriptor truncated after array bracket: '{desc}'")

    char = desc[pos]
    if char in BASE_TYPE_DESCRIPTORS:
        base_name = BASE_TYPE_DESCRIPTORS[char]
        next_pos = pos + 1
    elif char == "L":
        semi = desc.find(";", pos)
        if semi == -1:
            raise ValueError(f"Unterminated class descriptor in '{desc}' starting at {pos}")
        internal_name = desc[pos + 1 : semi]
        base_name = internal_name.replace("/", ".")
        next_pos = semi + 1
    else:
        raise ValueError(f"Unknown type character '{char}' in descriptor '{desc}' at index {pos}")

    type_name = base_name + ("[]" * array_dim)
    return type_name, next_pos


def decode_method_descriptor(desc: str) -> tuple[list[str], str]:
    """Decodes a JVM method descriptor like (Ljava/lang/String;I)V.

    Returns (param_types, return_type).
    Raises ValueError on malformed descriptors.
    """
    desc = desc.strip()
    if not desc.startswith("("):
        raise ValueError(f"Invalid method descriptor (must start with '('): '{desc}'")

    pos = 1
    params: list[str] = []
    while pos < len(desc) and desc[pos] != ")":
        param_type, pos = decode_type_descriptor(desc, pos)
        params.append(param_type)

    if pos >= len(desc) or desc[pos] != ")":
        raise ValueError(f"Unmatched ')' in method descriptor: '{desc}'")

    pos += 1  # Skip ')'
    if pos >= len(desc):
        raise ValueError(f"Missing return type in method descriptor: '{desc}'")

    return_type, end_pos = decode_type_descriptor(desc, pos)
    if end_pos != len(desc):
        raise ValueError(f"Trailing characters after return type in descriptor: '{desc[end_pos:]}'")

    return params, return_type


def format_method_signature(return_type: str, method_name: str, params: list[str]) -> str:
    """Formats return type, method name, and parameter types as a Java signature string."""
    params_str = ", ".join(params)
    return f"{return_type} {method_name}({params_str})"


def parse_constant_pool_method(raw_entry: str) -> dict[str, Any]:
    """Parses and decodes a constant pool Methodref or InterfaceMethodref entry.

    Example input:
      io/github/minh124199/viettemplate/api/TemplateId.of:(Ljava/lang/String;)Lio/github/minh124199/viettemplate/api/TemplateId;
    """
    raw = raw_entry.strip()
    if ":" in raw:
        target, desc = raw.split(":", 1)
        target = target.strip()
        desc = desc.strip()
        owner_raw, name_raw = target.rsplit(".", 1) if "." in target else ("", target)
        owner = owner_raw.strip('"').replace("/", ".")
        name = name_raw.strip('"')
        try:
            params, ret_type = decode_method_descriptor(desc)
        except Exception:
            params, ret_type = [], "void"
        signature = format_method_signature(ret_type, name, params)
        name_and_params = f"{name}({', '.join(params)})"
        call_sig = f"{owner}.{name}({', '.join(params)})"
        full_sig = f"{ret_type} {owner}.{name}({', '.join(params)})"
        return {
            "owner": owner,
            "name": name,
            "descriptor": desc,
            "parameters": params,
            "return_type": ret_type,
            "signature": signature,
            "name_and_params": name_and_params,
            "call_signature": call_sig,
            "full_signature": full_sig,
            "raw": f"{owner}.{name}:{desc}",
        }
    else:
        owner_raw, name_raw = raw.rsplit(".", 1) if "." in raw else ("", raw)
        owner = owner_raw.strip('"').replace("/", ".")
        name = name_raw.strip('"')
        return {
            "owner": owner,
            "name": name,
            "descriptor": "()V",
            "parameters": [],
            "return_type": "void",
            "signature": f"void {name}()",
            "name_and_params": f"{name}()",
            "call_signature": f"{owner}.{name}()",
            "full_signature": f"void {owner}.{name}()",
            "raw": raw,
        }


def parse_constant_pool_field(raw_entry: str) -> dict[str, Any]:
    """Parses and decodes a constant pool Fieldref entry."""
    raw = raw_entry.strip()
    if ":" in raw:
        target, desc = raw.split(":", 1)
        target = target.strip()
        desc = desc.strip()
        owner_raw, name_raw = target.rsplit(".", 1) if "." in target else ("", target)
        owner = owner_raw.strip('"').replace("/", ".")
        name = name_raw.strip('"')
        try:
            f_type, _ = decode_type_descriptor(desc)
        except Exception:
            f_type = "unknown"
        signature = f"{f_type} {name}"
        call_sig = f"{owner}.{name}"
        full_sig = f"{f_type} {owner}.{name}"
        return {
            "owner": owner,
            "name": name,
            "descriptor": desc,
            "type": f_type,
            "signature": signature,
            "call_signature": call_sig,
            "full_signature": full_sig,
            "raw": f"{owner}.{name}:{desc}",
        }
    else:
        owner_raw, name_raw = raw.rsplit(".", 1) if "." in raw else ("", raw)
        owner = owner_raw.strip('"').replace("/", ".")
        name = name_raw.strip('"')
        return {
            "owner": owner,
            "name": name,
            "descriptor": "",
            "type": "",
            "signature": name,
            "call_signature": f"{owner}.{name}",
            "full_signature": f"{owner}.{name}",
            "raw": raw,
        }


def parse_method_call_string(sig: str) -> dict[str, Any]:
    """Parses a method call representation (raw or Java signature) into a structured dict."""
    sig = sig.strip()
    if ":" in sig and "(" in sig.split(":", 1)[1]:
        return parse_constant_pool_method(sig)

    if "(" in sig:
        before_paren, after_paren = sig.split("(", 1)
        params_str = after_paren.rstrip(")")
        params = [p.strip() for p in params_str.split(",") if p.strip()]

        tokens = before_paren.strip().split()
        if len(tokens) >= 2:
            return_type = tokens[0]
            target = tokens[1]
        else:
            return_type = None
            target = tokens[0]

        owner, name = target.rsplit(".", 1) if "." in target else ("", target)
        ret_prefix = f"{return_type} " if return_type else ""
        return {
            "owner": owner,
            "name": name,
            "descriptor": "",
            "parameters": params,
            "return_type": return_type,
            "signature": f"{ret_prefix}{name}({', '.join(params)})",
            "name_and_params": f"{name}({', '.join(params)})",
            "call_signature": f"{owner}.{name}({', '.join(params)})",
            "full_signature": sig,
        }

    owner, name = sig.rsplit(".", 1) if "." in sig else ("", sig)
    return {
        "owner": owner,
        "name": name,
        "descriptor": "()V",
        "parameters": [],
        "return_type": None,
        "signature": f"{name}()",
        "name_and_params": f"{name}()",
        "call_signature": f"{owner}.{name}()",
        "full_signature": sig,
    }


def parse_field_access_string(sig: str) -> dict[str, Any]:
    """Parses a field access representation into a structured dict."""
    sig = sig.strip()
    if ":" in sig:
        return parse_constant_pool_field(sig)

    tokens = sig.split()
    if len(tokens) >= 2:
        field_type = tokens[0]
        target = tokens[1]
    else:
        field_type = None
        target = tokens[0]

    owner, name = target.rsplit(".", 1) if "." in target else ("", target)
    return {
        "owner": owner,
        "name": name,
        "descriptor": "",
        "type": field_type,
        "signature": f"{field_type + ' ' if field_type else ''}{name}",
        "call_signature": f"{owner}.{name}",
        "full_signature": sig,
    }


def parse_member_signature(raw_sig: str) -> dict[str, Any]:
    """Parses a baseline MEMBER signature into structured components.

    Strips modifier noise (public, static, abstract, etc.).
    """
    raw = raw_sig.strip()
    if "(" in raw:
        # Method
        before_paren, after_paren = raw.split("(", 1)
        params_str = after_paren.rstrip(")")
        tokens = before_paren.strip().split()
        idx = 0
        while idx < len(tokens) and tokens[idx] in JAVA_MODIFIERS:
            idx += 1
        name = tokens[-1]
        return_type = " ".join(tokens[idx:-1]) if idx < len(tokens) - 1 else "void"
        params = [p.strip() for p in params_str.split(",") if p.strip()]
        normalized = f"{return_type} {name}({', '.join(params)})"
        name_and_params = f"{name}({', '.join(params)})"
        return {
            "kind": "method",
            "name": name,
            "return_type": return_type,
            "parameters": params,
            "normalized": normalized,
            "name_and_params": name_and_params,
            "raw": raw,
        }
    else:
        # Field
        tokens = raw.strip().split()
        idx = 0
        while idx < len(tokens) and tokens[idx] in JAVA_MODIFIERS:
            idx += 1
        name = tokens[-1]
        field_type = " ".join(tokens[idx:-1]) if idx < len(tokens) - 1 else ""
        normalized = f"{field_type} {name}".strip()
        return {
            "kind": "field",
            "name": name,
            "type": field_type,
            "normalized": normalized,
            "raw": raw,
        }


class BaselineMemberSet(set):
    """Set subclass storing raw member strings and indexed normalized signatures."""

    def __init__(self, iterable=None):
        super().__init__()
        self.methods: dict[str, dict[str, Any]] = {}
        self.fields: dict[str, dict[str, Any]] = {}
        self.normalized_signatures: set[str] = set()
        self.name_and_params: set[str] = set()
        if iterable:
            for item in iterable:
                self.add_member(item)

    def add_member(self, raw_member: str) -> None:
        self.add(raw_member)
        parsed = parse_member_signature(raw_member)
        self.normalized_signatures.add(parsed["normalized"])
        if parsed["kind"] == "method":
            self.methods[parsed["normalized"]] = parsed
            self.name_and_params.add(parsed["name_and_params"])
        elif parsed["kind"] == "field":
            self.fields[parsed["normalized"]] = parsed

    def matches_method(
        self, method_name: str, params: list[str], return_type: str | None = None
    ) -> bool:
        call_np = f"{method_name}({', '.join(params)})"
        if call_np in self.name_and_params:
            if return_type is not None:
                call_norm = f"{return_type} {call_np}"
                return call_norm in self.methods
            return True
        return False

    def matches_field(self, field_name: str, field_type: str | None = None) -> bool:
        if field_type:
            return f"{field_type} {field_name}" in self.fields
        return any(f["name"] == field_name for f in self.fields.values())


def parse_baseline(baseline_file: Path | str) -> dict[str, BaselineMemberSet]:
    """Parses generated-template-runtime-abi.txt into expected types and members."""
    baseline_path = Path(baseline_file)
    expected: dict[str, BaselineMemberSet] = {}
    current_type = None

    if not baseline_path.exists():
        return expected

    with open(baseline_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("TYPE "):
                current_type = line[5:].strip()
                expected[current_type] = BaselineMemberSet()
            elif line.startswith("MEMBER ") and current_type:
                member_sig = line[7:].strip()
                expected[current_type].add_member(member_sig)
    return expected


def load_classification(path: Path | str) -> dict[str, str]:
    """Loads public-surface-classification.txt mapping class name to category."""
    classification_path = Path(path)
    if not classification_path.exists():
        return {}
    mapping = {}
    with open(classification_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) == 2:
                mapping[parts[0]] = parts[1]
    return mapping


def locate_template_classes(root_dir: Path) -> list[Path]:
    """Finds all compiled AOT template classfiles."""
    classes = []
    # Primary audit directory produced by GeneratedTemplateAbiAuditTest
    report_classes = list((root_dir / "build" / "reports" / "generated-template-abi-classes").glob("*.class"))
    if report_classes:
        classes.extend(report_classes)

    # Secondary: target directories from tests
    for p in (root_dir / "viet-template-vtl-interpreter").glob("**/generated-template-abi-classes/*.class"):
        classes.append(p)

    # Integration test fixtures
    for p in root_dir.glob("integration-tests/**/T_*.class"):
        classes.append(p)

    # Deduplicate by filename
    seen_names = set()
    unique_classes = []
    for c in sorted(classes):
        if c.name not in seen_names:
            seen_names.add(c.name)
            unique_classes.append(c)

    return unique_classes


def analyze_class_constant_pool(class_file: Path) -> dict[str, Any]:
    """Runs javap -verbose and parses constant pool entries."""
    cmd = ["javap", "-v", str(class_file)]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, check=True)
    except Exception as e:
        print(f"[WARN] Failed to inspect {class_file}: {e}", file=sys.stderr)
        return {
            "classes": [],
            "methods": [],
            "method_details": [],
            "fields": [],
            "field_details": [],
        }

    output = proc.stdout
    classes = set()
    methods_dict: dict[str, dict[str, Any]] = {}
    fields_dict: dict[str, dict[str, Any]] = {}

    for line in output.splitlines():
        # Match Class references
        m_class = re.search(r"Class\s+#\d+\s+//\s+(.*)", line)
        if m_class:
            cname = m_class.group(1).strip().strip('"').replace("/", ".")
            if not cname.startswith("io.github.minh124199.viettemplate.generated") and not cname.startswith("["):
                classes.add(cname)

        # Match Methodref & InterfaceMethodref
        m_meth = re.search(r"(?:Methodref|InterfaceMethodref)\s+#\d+\.#\d+\s+//\s+(.*)", line)
        if m_meth:
            raw_entry = m_meth.group(1).strip()
            parsed_method = parse_constant_pool_method(raw_entry)
            if not parsed_method["owner"].startswith("io.github.minh124199.viettemplate.generated"):
                call_sig = parsed_method["call_signature"]
                methods_dict[call_sig] = parsed_method

        # Match Fieldref
        m_field = re.search(r"Fieldref\s+#\d+\.#\d+\s+//\s+(.*)", line)
        if m_field:
            raw_entry = m_field.group(1).strip()
            parsed_field = parse_constant_pool_field(raw_entry)
            if not parsed_field["owner"].startswith("io.github.minh124199.viettemplate.generated"):
                call_sig = parsed_field["call_signature"]
                fields_dict[call_sig] = parsed_field

    sorted_methods = sorted(methods_dict.values(), key=lambda m: m["call_signature"])
    sorted_fields = sorted(fields_dict.values(), key=lambda f: f["call_signature"])

    return {
        "classes": sorted(classes),
        "methods": [m["call_signature"] for m in sorted_methods],
        "method_details": sorted_methods,
        "fields": [f["call_signature"] for f in sorted_fields],
        "field_details": sorted_fields,
    }


def validate_abi(
    report_or_types: Any,
    baseline: dict[str, BaselineMemberSet],
    invoked_methods: list[dict[str, Any]] | None = None,
    accessed_fields: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Validates runtime ABI against the baseline.

    Supports two calling conventions:
    1. validate_abi(report_dict, baseline_dict)
    2. validate_abi(viet_types_list, baseline_dict, invoked_methods_list, accessed_fields_list)

    Asserts:
    - Exactly 7 referenced Viet Template types (all registered in baseline).
    - Exactly 22 invoked Viet Template methods (all registered under owner in baseline).
    - Exactly 0 accessed Viet Template fields.
    """
    if isinstance(report_or_types, dict):
        report = report_or_types
        runtime_abi = report.get("runtime_abi", {})
        viet_types = sorted(runtime_abi.get("viet_template_types", []))

        # Prefer structured method details if present; fallback to parsing strings
        raw_method_details = runtime_abi.get("viet_template_method_details")
        if raw_method_details is not None:
            methods = raw_method_details
        else:
            methods = [
                parse_method_call_string(m) for m in runtime_abi.get("viet_template_methods", [])
            ]

        raw_field_details = runtime_abi.get("viet_template_field_details")
        if raw_field_details is not None:
            fields = raw_field_details
        else:
            fields = [
                parse_field_access_string(f) for f in runtime_abi.get("viet_template_fields", [])
            ]
    else:
        viet_types = sorted(report_or_types)
        methods = invoked_methods or []
        fields = accessed_fields or []

    errors: list[str] = []
    unregistered_types: list[str] = []
    unregistered_methods: list[str] = []
    unregistered_fields: list[str] = []

    # 1. Type validation
    baseline_types = set(baseline.keys())
    for vt in viet_types:
        if vt not in baseline_types:
            unregistered_types.append(vt)
            errors.append(f"Generated template references unregistered type: {vt}")

    # 2. Method validation
    for m in methods:
        owner = m.get("owner", "")
        name = m.get("name", "")
        params = m.get("parameters", [])
        ret_type = m.get("return_type")
        call_sig = m.get("call_signature", f"{owner}.{name}({', '.join(params)})")

        if owner not in baseline:
            unregistered_methods.append(call_sig)
            errors.append(f"Generated template invokes method on unregistered type: {call_sig}")
        else:
            member_set = baseline[owner]
            if not member_set.matches_method(name, params, ret_type):
                unregistered_methods.append(call_sig)
                errors.append(f"Generated template invokes unregistered method: {call_sig}")

    # 3. Field validation
    for f in fields:
        owner = f.get("owner", "")
        name = f.get("name", "")
        f_type = f.get("type")
        call_sig = f.get("call_signature", f"{owner}.{name}")

        if owner not in baseline:
            unregistered_fields.append(call_sig)
            errors.append(f"Generated template accesses field on unregistered type: {call_sig}")
        else:
            member_set = baseline[owner]
            if not member_set.matches_field(name, f_type):
                unregistered_fields.append(call_sig)
                errors.append(f"Generated template accesses unregistered field: {call_sig}")

    # 4. Member-level exact count assertions
    expected_type_count = 7
    expected_method_count = 22
    expected_field_count = 0

    type_count_matches = len(viet_types) == expected_type_count
    method_count_matches = len(methods) == expected_method_count
    field_count_matches = len(fields) == expected_field_count

    if not type_count_matches:
        errors.append(
            f"Referenced type count mismatch: expected {expected_type_count}, found {len(viet_types)}: {viet_types}"
        )
    if not method_count_matches:
        method_names = [m.get("call_signature", str(m)) for m in methods]
        errors.append(
            f"Invoked method count mismatch: expected {expected_method_count}, found {len(methods)}: {method_names}"
        )
    if not field_count_matches:
        field_names = [f.get("call_signature", str(f)) for f in fields]
        errors.append(
            f"Accessed field count mismatch: expected {expected_field_count}, found {len(fields)}: {field_names}"
        )

    status = "PASSED" if not errors else "FAILED"
    return {
        "status": status,
        "errors": errors,
        "unregistered_types": sorted(unregistered_types),
        "unregistered_methods": sorted(unregistered_methods),
        "unregistered_fields": sorted(unregistered_fields),
        "type_count_matches": type_count_matches,
        "method_count_matches": method_count_matches,
        "field_count_matches": field_count_matches,
        "total_types": len(viet_types),
        "total_methods": len(methods),
        "total_fields": len(fields),
    }


def build_abi_report(
    classes: list[Path],
    baseline_path: Path | None = None,
    classification_path: Path | None = None,
) -> dict[str, Any]:
    """Aggregates ABI references across all analyzed template classfiles and builds report."""
    all_referenced_types = set()
    invoked_methods_map: dict[str, dict[str, Any]] = {}
    accessed_fields_map: dict[str, dict[str, Any]] = {}
    per_class_summary: dict[str, Any] = {}

    for c in classes:
        res = analyze_class_constant_pool(c)
        per_class_summary[c.name] = {
            "classes": res["classes"],
            "methods": res["methods"],
            "fields": res["fields"],
        }
        all_referenced_types.update(res["classes"])
        for m in res["method_details"]:
            invoked_methods_map[m["call_signature"]] = m
        for f in res["field_details"]:
            accessed_fields_map[f["call_signature"]] = f

    viet_types = sorted([t for t in all_referenced_types if "minh124199" in t])
    jdk_types = sorted([t for t in all_referenced_types if "minh124199" not in t])

    viet_method_details = sorted(
        [m for m in invoked_methods_map.values() if "minh124199" in m["owner"]],
        key=lambda m: m["call_signature"],
    )
    viet_field_details = sorted(
        [f for f in accessed_fields_map.values() if "minh124199" in f["owner"]],
        key=lambda f: f["call_signature"],
    )

    viet_methods = [m["call_signature"] for m in viet_method_details]
    viet_method_signatures = [m["signature"] for m in viet_method_details]
    viet_methods_raw = [m["raw"] for m in viet_method_details]
    viet_fields = [f["call_signature"] for f in viet_field_details]

    # Public surface classifications
    classifications: dict[str, str] = {}
    if classification_path and Path(classification_path).exists():
        cmap = load_classification(classification_path)
        for vt in viet_types:
            classifications[vt] = cmap.get(vt, "UNCLASSIFIED")

    # Baseline validation
    baseline_file_str = str(baseline_path) if baseline_path else None
    classification_file_str = str(classification_path) if classification_path else None

    baseline = parse_baseline(baseline_path) if baseline_path else {}
    validation = validate_abi(viet_types, baseline, viet_method_details, viet_field_details)

    summary = {
        "total_types": len(viet_types),
        "total_methods": len(viet_methods),
        "total_fields": len(viet_fields),
        "total_jdk_types": len(jdk_types),
        "status": validation["status"],
    }

    report = {
        "metadata": {
            "total_classes_analyzed": len(classes),
            "analyzed_files": [c.name for c in classes],
            "baseline_file": baseline_file_str,
            "classification_file": classification_file_str,
        },
        "summary": summary,
        "validation": validation,
        "classifications": classifications,
        "runtime_abi": {
            "viet_template_types": viet_types,
            "jdk_types": jdk_types,
            "viet_template_methods": viet_methods,
            "viet_template_method_signatures": viet_method_signatures,
            "viet_template_methods_raw": viet_methods_raw,
            "viet_template_method_details": viet_method_details,
            "viet_template_fields": viet_fields,
            "viet_template_field_details": viet_field_details,
        },
        "per_class_details": per_class_summary,
    }
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify generated template runtime ABI.")
    parser.add_argument(
        "--baseline",
        type=Path,
        default=DEFAULT_BASELINE,
        help="Path to generated-template-runtime-abi.txt baseline",
    )
    parser.add_argument(
        "--classification",
        type=Path,
        default=DEFAULT_CLASSIFICATION,
        help="Path to public-surface-classification.txt",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_REPORT,
        help="Path to write generated-template-abi.json report",
    )
    args = parser.parse_args()

    print("=== Viet Template Generated Template Runtime ABI Audit ===")
    classes = locate_template_classes(ROOT_DIR)

    if not classes:
        print("[INFO] No compiled template classfiles found. Running GeneratedTemplateAbiAuditTest to generate them...")
        res = subprocess.run(
            ["./gradlew", ":viet-template-vtl-interpreter:test", "--tests", "GeneratedTemplateAbiAuditTest", "--no-daemon"],
            cwd=str(ROOT_DIR),
            capture_output=True,
            text=True,
        )
        if res.returncode != 0:
            print(f"[ERROR] Failed to compile representative template suite:\n{res.stderr}", file=sys.stderr)
            sys.exit(1)
        classes = locate_template_classes(ROOT_DIR)

    print(f"[INFO] Analyzed {len(classes)} compiled template classfiles.")
    report = build_abi_report(
        classes,
        baseline_path=args.baseline,
        classification_path=args.classification,
    )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    try:
        report_display = args.report.relative_to(ROOT_DIR)
    except ValueError:
        report_display = args.report
    print(f"[PASS] ABI report written to: {report_display}")

    validation = report["validation"]
    classifications = report.get("classifications", {})

    print("\n--- Verified Runtime ABI Types (7 expected) ---")
    for vt in report["runtime_abi"]["viet_template_types"]:
        cat = f" [{classifications.get(vt, 'UNCLASSIFIED')}]" if classifications else ""
        print(f"  [OK] {vt}{cat}")

    print("\n--- Verified Runtime ABI Method Calls (22 expected) ---")
    for vm in report["runtime_abi"]["viet_template_methods"]:
        print(f"  [OK] {vm}")

    print("\n--- Runtime ABI Audit Summary ---")
    print(f"  Referenced Viet Template Types:   {validation['total_types']} / 7")
    print(f"  Invoked Viet Template Methods:    {validation['total_methods']} / 22")
    print(f"  Accessed Viet Template Fields:    {validation['total_fields']} / 0")

    if validation["errors"]:
        print("\n[FAILED] Generated template ABI audit FAILED:")
        for err in validation["errors"]:
            print(f"  - {err}")
        sys.exit(1)
    else:
        print("\n[SUCCESS] Generated template runtime ABI audit PASSED! 0 unregistered runtime dependencies.")
        sys.exit(0)


if __name__ == "__main__":
    main()
