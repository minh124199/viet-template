import importlib.util
import os
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_script(name):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


compat = load_script("verify-api-compatibility.py")


class ApiCompatibilityVerifierTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.path = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def create_file(self, rel_path, content):
        file_path = self.path / rel_path
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content, encoding="utf-8")
        return str(file_path)

    def test_parse_baseline_extracts_types_and_members(self):
        content = """
# Sample baseline
TYPE public final class com.example.Foo
  MEMBER public java.lang.String bar()
  MEMBER public void baz(int)
"""
        p = self.create_file("baseline.txt", content)
        parsed = compat.parse_baseline(p)
        self.assertIn("com.example.Foo", parsed)
        self.assertEqual(parsed["com.example.Foo"]["header"], "public final class com.example.Foo")
        self.assertEqual(
            parsed["com.example.Foo"]["members"],
            ["public java.lang.String bar()", "public void baz(int)"],
        )

    def test_duplicate_baseline_ownership_detected(self):
        b1_content = """
TYPE public class com.example.Common
  MEMBER public void doWork()
"""
        b2_content = """
TYPE public class com.example.Common
  MEMBER public void doWork()
"""
        p1 = self.create_file("b1.txt", b1_content)
        p2 = self.create_file("b2.txt", b2_content)

        surface = {
            "com.example.Common": {
                "header": "public class com.example.Common",
                "members": ["public void doWork()"],
            }
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1), ("b2", p2)],
            classification_path=None,
            surface_override=surface,
        )
        self.assertEqual(res, 1)

    def test_missing_stable_type_detected(self):
        b1_content = """
TYPE public class com.example.Foo
  MEMBER public void doFoo()
"""
        classif_content = """
com.example.Foo STABLE_API
com.example.Bar STABLE_API
"""
        p1 = self.create_file("b1.txt", b1_content)
        p_classif = self.create_file("classif.txt", classif_content)

        surface = {
            "com.example.Foo": {
                "header": "public class com.example.Foo",
                "members": ["public void doFoo()"],
            },
            "com.example.Bar": {
                "header": "public class com.example.Bar",
                "members": ["public void doBar()"],
            },
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1)],
            classification_path=p_classif,
            surface_override=surface,
        )
        self.assertEqual(res, 1)

    def test_declassified_baseline_type_detected(self):
        b1_content = """
TYPE public class com.example.InternalAccident
  MEMBER public void leak()
"""
        classif_content = """
com.example.InternalAccident PUBLIC_BUT_INTERNAL_ACCIDENT
"""
        p1 = self.create_file("b1.txt", b1_content)
        p_classif = self.create_file("classif.txt", classif_content)

        surface = {
            "com.example.InternalAccident": {
                "header": "public class com.example.InternalAccident",
                "members": ["public void leak()"],
            }
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1)],
            classification_path=p_classif,
            surface_override=surface,
        )
        self.assertEqual(res, 1)

    def test_removed_method_detected(self):
        b1_content = """
TYPE public class com.example.Foo
  MEMBER public void methodA()
  MEMBER public void methodB()
"""
        classif_content = """
com.example.Foo STABLE_API
"""
        p1 = self.create_file("b1.txt", b1_content)
        p_classif = self.create_file("classif.txt", classif_content)

        # methodB is missing in current surface
        surface = {
            "com.example.Foo": {
                "header": "public class com.example.Foo",
                "members": ["public void methodA()"],
            }
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1)],
            classification_path=p_classif,
            surface_override=surface,
        )
        self.assertEqual(res, 1)

    def test_added_abstract_interface_method_detected(self):
        b1_content = """
TYPE public interface com.example.FooSpi
  MEMBER public abstract void methodA()
"""
        classif_content = """
com.example.FooSpi STABLE_SPI
"""
        p1 = self.create_file("b1.txt", b1_content)
        p_classif = self.create_file("classif.txt", classif_content)

        # Added abstract method without default implementation breaks external implementors
        surface = {
            "com.example.FooSpi": {
                "header": "public interface com.example.FooSpi",
                "members": [
                    "public abstract void methodA()",
                    "public abstract void methodB()",
                ],
            }
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1)],
            classification_path=p_classif,
            surface_override=surface,
        )
        self.assertEqual(res, 1)

    def test_valid_surface_passes(self):
        b1_content = """
TYPE public class com.example.Foo
  MEMBER public void methodA()
"""
        b2_content = """
TYPE public interface com.example.BarSpi
  MEMBER public abstract void execute()
"""
        classif_content = """
com.example.Foo STABLE_API
com.example.BarSpi STABLE_SPI
"""
        p1 = self.create_file("b1.txt", b1_content)
        p2 = self.create_file("b2.txt", b2_content)
        p_classif = self.create_file("classif.txt", classif_content)

        surface = {
            "com.example.Foo": {
                "header": "public class com.example.Foo",
                "members": ["public void methodA()"],
            },
            "com.example.BarSpi": {
                "header": "public interface com.example.BarSpi",
                "members": ["public abstract void execute()"],
            },
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1), ("b2", p2)],
            classification_path=p_classif,
            surface_override=surface,
        )
        self.assertEqual(res, 0)


if __name__ == "__main__":
    unittest.main()
