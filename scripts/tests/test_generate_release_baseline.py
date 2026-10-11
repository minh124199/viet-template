import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str):
    path = ROOT / "scripts" / name
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gen = load_script("generate-release-baseline.py")


class GenerateReleaseBaselineTests(unittest.TestCase):
    def test_verify_release_tag_provenance_success(self):
        prov = gen.verify_release_tag_provenance("1.4.0", "v1.4.0", ROOT)
        self.assertEqual(prov["releaseVersion"], "1.4.0")
        self.assertEqual(prov["tag"], "v1.4.0")
        self.assertEqual(prov["tagObjectSha"], "615ffa9ea291fbd9226e040fd321f7ed018bd01a")
        self.assertEqual(prov["sourceCommitSha"], "1ee485beee4b7588dd154881ede1e76985e339dc")
        self.assertEqual(prov["treeSha"], "8ddc255797a8bfce88ee30e1815d8f56e809b61e")
        self.assertEqual(prov["maintenanceLine"], "1.4.x")

    def test_verify_release_tag_provenance_rejects_nonexistent_tag(self):
        with self.assertRaises(ValueError):
            gen.verify_release_tag_provenance("1.4.0", "v999.0.0-nonexistent", ROOT)

    def test_categorize_types_into_baselines_partitions_all_types(self):
        types = [
            "io.github.minh124199.viettemplate.api.TemplateEngine",
            "io.github.minh124199.viettemplate.aot.TemplateAotCompiler",
            "io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView",
            "io.github.minh124199.viettemplate.spring.security.SecurityView",
            "io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer",
        ]
        groups = gen.categorize_types_into_baselines(types)
        self.assertIn("io.github.minh124199.viettemplate.api.TemplateEngine", groups["core"])
        self.assertIn("io.github.minh124199.viettemplate.aot.TemplateAotCompiler", groups["aot"])
        self.assertIn("io.github.minh124199.viettemplate.spring.web.servlet.VietTemplateView", groups["spring"])
        self.assertIn("io.github.minh124199.viettemplate.spring.security.SecurityView", groups["spring-security"])
        self.assertIn("io.github.minh124199.viettemplate.quarkus.VietTemplateRenderer", groups["quarkus"])

    def test_format_baseline_text_deterministic_order(self):
        member_map = {
            "com.example.B": {
                "header": "public class com.example.B",
                "members": ["public void beta()", "public void alpha()"],
            },
            "com.example.A": {
                "header": "public class com.example.A",
                "members": ["public void execute()"],
            },
        }
        text = gen.format_baseline_text("core", "1.4.0", ["com.example.B", "com.example.A"], member_map)
        lines = text.splitlines()

        # A should come before B
        idx_a = -1
        idx_b = -1
        for i, l in enumerate(lines):
            if "TYPE public class com.example.A" in l:
                idx_a = i
            elif "TYPE public class com.example.B" in l:
                idx_b = i
        self.assertTrue(idx_a != -1 and idx_b != -1 and idx_a < idx_b)

        # In B, alpha should come before beta
        idx_alpha = -1
        idx_beta = -1
        for i, l in enumerate(lines):
            if "MEMBER public void alpha()" in l:
                idx_alpha = i
            elif "MEMBER public void beta()" in l:
                idx_beta = i
        self.assertTrue(idx_alpha != -1 and idx_beta != -1 and idx_alpha < idx_beta)


if __name__ == "__main__":
    unittest.main()
