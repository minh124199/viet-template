#!/usr/bin/env python3
"""
test_verify_public_surface.py

Automated unit tests for signature leak detection in verify-public-surface-classification.py.
Verifies that signature leaks are caught across:
- Method parameters
- Return types
- Generic types (single and nested)
- Throws clauses
- Constructors
- Records / record components
- Array types
- Multiple leaks in a single signature

Also verifies:
- Suppression of false positives on inner class prefixes (Outer vs Outer$Inner)
- Self-class reference suppression
- Clean classes with no leaks
- Empty internal types set
"""

import importlib.util
import os
import sys
import unittest

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
VERIFIER_PATH = os.path.join(SCRIPT_DIR, "verify-public-surface-classification.py")

spec = importlib.util.spec_from_file_location("verify_public_surface", VERIFIER_PATH)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)

build_leak_pattern = verifier.build_leak_pattern
scan_signature_lines = verifier.scan_signature_lines


class TestVerifyPublicSurface(unittest.TestCase):

    def setUp(self):
        self.internal_types = {
            "io.github.minh124199.viettemplate.internal.InternalParam",
            "io.github.minh124199.viettemplate.internal.InternalReturn",
            "io.github.minh124199.viettemplate.internal.InternalGeneric",
            "io.github.minh124199.viettemplate.internal.InternalException",
            "io.github.minh124199.viettemplate.internal.InternalHandle",
            "io.github.minh124199.viettemplate.internal.InternalSecret",
            "io.github.minh124199.viettemplate.internal.InternalElement",
            "io.github.minh124199.viettemplate.internal.InternalA",
            "io.github.minh124199.viettemplate.internal.InternalB",
            "io.github.minh124199.viettemplate.internal.InternalC",
            "io.github.minh124199.viettemplate.internal.Outer",
        }
        self.pattern = build_leak_pattern(self.internal_types)
        self.test_cls = "io.github.minh124199.viettemplate.api.PublicService"

    def test_parameter_leak(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public void process(io.github.minh124199.viettemplate.internal.InternalParam);",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 1)
        cls, line, leaked = leaks[0]
        self.assertEqual(cls, self.test_cls)
        self.assertEqual(leaked, "io.github.minh124199.viettemplate.internal.InternalParam")
        self.assertIn("process", line)

    def test_return_type_leak(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public io.github.minh124199.viettemplate.internal.InternalReturn computeResult();",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 1)
        cls, line, leaked = leaks[0]
        self.assertEqual(cls, self.test_cls)
        self.assertEqual(leaked, "io.github.minh124199.viettemplate.internal.InternalReturn")
        self.assertIn("computeResult", line)

    def test_generic_type_argument_leak(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public java.util.List<io.github.minh124199.viettemplate.internal.InternalGeneric> getItems();",
            "  public java.util.Map<java.lang.String, java.util.List<io.github.minh124199.viettemplate.internal.InternalGeneric>> getNestedMap();",
            "  public void accept(java.util.Optional<io.github.minh124199.viettemplate.internal.InternalGeneric>);",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 3)
        for _, _, leaked in leaks:
            self.assertEqual(leaked, "io.github.minh124199.viettemplate.internal.InternalGeneric")

    def test_throws_clause_leak(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public void execute() throws io.github.minh124199.viettemplate.internal.InternalException;",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 1)
        cls, line, leaked = leaks[0]
        self.assertEqual(cls, self.test_cls)
        self.assertEqual(leaked, "io.github.minh124199.viettemplate.internal.InternalException")
        self.assertIn("throws", line)

    def test_constructor_leak(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public io.github.minh124199.viettemplate.api.PublicService(io.github.minh124199.viettemplate.internal.InternalHandle);",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 1)
        cls, line, leaked = leaks[0]
        self.assertEqual(cls, self.test_cls)
        self.assertEqual(leaked, "io.github.minh124199.viettemplate.internal.InternalHandle")

    def test_record_component_leak(self):
        record_cls = "io.github.minh124199.viettemplate.api.PublicRecord"
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicRecord extends java.lang.Record {",
            "  public io.github.minh124199.viettemplate.api.PublicRecord(io.github.minh124199.viettemplate.internal.InternalSecret, java.lang.String);",
            "  public io.github.minh124199.viettemplate.internal.InternalSecret secret();",
            "  public java.lang.String name();",
            "}",
        ]
        leaks = scan_signature_lines(record_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 2)
        leaked_types = [l[2] for l in leaks]
        self.assertEqual(leaked_types, [
            "io.github.minh124199.viettemplate.internal.InternalSecret",
            "io.github.minh124199.viettemplate.internal.InternalSecret",
        ])

    def test_array_type_leak(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public io.github.minh124199.viettemplate.internal.InternalElement[] getElements();",
            "  public void setElements(io.github.minh124199.viettemplate.internal.InternalElement[]);",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 2)
        for _, _, leaked in leaks:
            self.assertEqual(leaked, "io.github.minh124199.viettemplate.internal.InternalElement")

    def test_multiple_leaks_in_single_signature(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public io.github.minh124199.viettemplate.internal.InternalA transform(io.github.minh124199.viettemplate.internal.InternalB) throws io.github.minh124199.viettemplate.internal.InternalC;",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 3)
        detected = {l[2] for l in leaks}
        self.assertEqual(detected, {
            "io.github.minh124199.viettemplate.internal.InternalA",
            "io.github.minh124199.viettemplate.internal.InternalB",
            "io.github.minh124199.viettemplate.internal.InternalC",
        })

    def test_inner_class_prefix_false_positive_suppression(self):
        # Outer is internal: io.github.minh124199.viettemplate.internal.Outer
        # But Outer$Inner is a public/stable class: io.github.minh124199.viettemplate.internal.Outer$Inner
        # A signature referencing Outer$Inner must NOT be flagged as leaking Outer!
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public io.github.minh124199.viettemplate.internal.Outer$Inner getInner();",
            "  public void setInner(io.github.minh124199.viettemplate.internal.Outer$Inner);",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 0, f"False positive detected on inner class prefix: {leaks}")

    def test_clean_class_no_leaks(self):
        lines = [
            "public final class io.github.minh124199.viettemplate.api.PublicService {",
            "  public static final java.lang.String VERSION;",
            "  public io.github.minh124199.viettemplate.api.PublicService(java.lang.String);",
            "  public java.lang.String getName();",
            "  public int getCount();",
            "  public boolean isReady();",
            "  public java.util.List<java.lang.String> getNames();",
            "  public java.util.Map<java.lang.String, java.lang.Object> getContext();",
            "  public void render(java.lang.Appendable) throws java.io.IOException;",
            "}",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 0)

    def test_self_class_not_reported_as_leak(self):
        # If cls itself is in internal_types (e.g. self-referencing methods/constructors),
        # it should not report itself as a leak on its own declaration lines.
        cls = "io.github.minh124199.viettemplate.internal.InternalHandle"
        lines = [
            "public final class io.github.minh124199.viettemplate.internal.InternalHandle {",
            "  public io.github.minh124199.viettemplate.internal.InternalHandle();",
            "  public io.github.minh124199.viettemplate.internal.InternalHandle copy();",
            "}",
        ]
        leaks = scan_signature_lines(cls, lines, self.pattern)
        self.assertEqual(len(leaks), 0)

    def test_empty_internal_types(self):
        pattern = build_leak_pattern(set())
        self.assertIsNone(pattern)
        lines = [
            "public void doSomething(java.lang.String str);",
        ]
        leaks = scan_signature_lines(self.test_cls, lines, pattern)
        self.assertEqual(leaks, [])


class TestNewCategories(unittest.TestCase):
    """
    Verifies that INTERNAL_CROSS_PACKAGE and BENCHMARK_SUPPORT_INTERNAL
    types are treated as internal (non-stable) for signature leak purposes.
    """

    def setUp(self):
        # Simulate INTERNAL_CROSS_PACKAGE and BENCHMARK_SUPPORT_INTERNAL in the internal set
        self.internal_types = {
            "io.github.minh124199.viettemplate.vtl.internal.InternalCrossPackageType",
            "io.github.minh124199.viettemplate.vtl.interpreter.BenchmarkSupportType",
        }
        self.pattern = build_leak_pattern(self.internal_types)
        self.stable_cls = "io.github.minh124199.viettemplate.api.StableApi"

    def test_internal_cross_package_leaks_are_detected(self):
        """INTERNAL_CROSS_PACKAGE must not appear in stable API signatures."""
        lines = [
            "public interface io.github.minh124199.viettemplate.api.StableApi {",
            "  public io.github.minh124199.viettemplate.vtl.internal.InternalCrossPackageType getInternal();",
            "}",
        ]
        leaks = scan_signature_lines(self.stable_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 1)
        _, _, leaked = leaks[0]
        self.assertEqual(leaked, "io.github.minh124199.viettemplate.vtl.internal.InternalCrossPackageType")

    def test_benchmark_support_internal_leaks_are_detected(self):
        """BENCHMARK_SUPPORT_INTERNAL must not appear in stable API signatures."""
        lines = [
            "public interface io.github.minh124199.viettemplate.api.StableApi {",
            "  public void setup(io.github.minh124199.viettemplate.vtl.interpreter.BenchmarkSupportType);",
            "}",
        ]
        leaks = scan_signature_lines(self.stable_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 1)

    def test_clean_stable_api_has_no_leaks(self):
        """A stable class with only stable types in signatures produces no leaks."""
        lines = [
            "public interface io.github.minh124199.viettemplate.api.StableApi {",
            "  public java.lang.String render(java.util.Map<java.lang.String, java.lang.Object>);",
            "}",
        ]
        leaks = scan_signature_lines(self.stable_cls, lines, self.pattern)
        self.assertEqual(len(leaks), 0)


if __name__ == "__main__":
    unittest.main()
