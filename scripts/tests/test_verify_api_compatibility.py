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

    def test_non_sealed_becoming_sealed_detected(self):
        b1_content = """
TYPE public class com.example.Extensible
  MEMBER public void run()
"""
        p1 = self.create_file("b1.txt", b1_content)
        surface = {
            "com.example.Extensible": {
                "header": "public sealed class com.example.Extensible permits com.example.ExtensibleSub",
                "members": ["public void run()"],
            }
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1)],
            classification_path=None,
            surface_override=surface,
        )
        self.assertEqual(res, 1)

    def test_sealed_becoming_non_sealed_allowed(self):
        b1_content = """
TYPE public sealed class com.example.Sealed permits com.example.Sub
  MEMBER public void run()
"""
        p1 = self.create_file("b1.txt", b1_content)
        surface = {
            "com.example.Sealed": {
                "header": "public non-sealed class com.example.Sealed",
                "members": ["public void run()"],
            }
        }
        res = compat.check_compatibility(
            baseline_specs=[("b1", p1)],
            classification_path=None,
            surface_override=surface,
        )
        self.assertEqual(res, 0)

    def test_permits_list_changes_detected(self):
        b1_content = """
TYPE public sealed interface com.example.Expr permits com.example.Add, com.example.Sub
  MEMBER public abstract int eval()
"""
        p1 = self.create_file("b1.txt", b1_content)
        # Permitted subclass removed
        surface_removed = {
            "com.example.Expr": {
                "header": "public sealed interface com.example.Expr permits com.example.Add",
                "members": ["public abstract int eval()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_removed,
            ),
            1,
        )

        # Permitted subclass added
        surface_added = {
            "com.example.Expr": {
                "header": "public sealed interface com.example.Expr permits com.example.Add, com.example.Sub, com.example.Mul",
                "members": ["public abstract int eval()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_added,
            ),
            1,
        )

    def test_record_and_class_conversion_detected(self):
        b1_content = """
TYPE public final class com.example.Point
  MEMBER public int x()
"""
        p1 = self.create_file("b1.txt", b1_content)
        surface_record = {
            "com.example.Point": {
                "header": "public record com.example.Point(int x)",
                "members": ["public int x()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_record,
            ),
            1,
        )

        b2_content = """
TYPE public record com.example.Data(java.lang.String name)
  MEMBER public java.lang.String name()
"""
        p2 = self.create_file("b2.txt", b2_content)
        surface_class = {
            "com.example.Data": {
                "header": "public final class com.example.Data",
                "members": ["public java.lang.String name()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b2", p2)],
                classification_path=None,
                surface_override=surface_class,
            ),
            1,
        )

    def test_non_final_becoming_final_detected(self):
        b1_content = """
TYPE public class com.example.Base
  MEMBER public void op()
"""
        p1 = self.create_file("b1.txt", b1_content)
        surface_final = {
            "com.example.Base": {
                "header": "public final class com.example.Base",
                "members": ["public void op()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_final,
            ),
            1,
        )

    def test_final_becoming_non_final_allowed(self):
        b1_content = """
TYPE public final class com.example.Leaf
  MEMBER public void op()
"""
        p1 = self.create_file("b1.txt", b1_content)
        surface_non_final = {
            "com.example.Leaf": {
                "header": "public class com.example.Leaf",
                "members": ["public void op()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_non_final,
            ),
            0,
        )

    def test_method_modifier_changes_detected(self):
        b1_content = """
TYPE public interface com.example.Service
  MEMBER public default void execute()
  MEMBER public static void helper()
"""
        p1 = self.create_file("b1.txt", b1_content)
        # default became abstract
        surface_abstract = {
            "com.example.Service": {
                "header": "public interface com.example.Service",
                "members": [
                    "public abstract void execute()",
                    "public static void helper()",
                ],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_abstract,
            ),
            1,
        )

        # static became instance
        surface_static_change = {
            "com.example.Service": {
                "header": "public interface com.example.Service",
                "members": [
                    "public default void execute()",
                    "public default void helper()",
                ],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface_static_change,
            ),
            1,
        )

    def test_method_becoming_final_in_class_detected(self):
        b1_content = """
TYPE public class com.example.Component
  MEMBER public void action()
"""
        p1 = self.create_file("b1.txt", b1_content)
        surface = {
            "com.example.Component": {
                "header": "public class com.example.Component",
                "members": ["public final void action()"],
            }
        }
        self.assertEqual(
            compat.check_compatibility(
                baseline_specs=[("b1", p1)],
                classification_path=None,
                surface_override=surface,
            ),
            1,
        )


if __name__ == "__main__":
    unittest.main()
