import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "verify_documentation",
    ROOT / "scripts" / "verify-documentation.py",
)
doc_verifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(doc_verifier)


class DocumentationVerifierTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.repo_root = ROOT
        cls.known_props = doc_verifier.get_known_spring_properties(cls.repo_root)
        cls.known_types = doc_verifier.get_known_public_types(cls.repo_root)
        cls.known_diags = doc_verifier.get_known_diagnostic_codes(cls.repo_root)

    # -------------------------------------------------------------------------
    # Check 1: Release Date Integrity
    # -------------------------------------------------------------------------

    def test_current_repo_release_dates_are_clean(self):
        errors = doc_verifier.check_release_dates(self.repo_root)
        self.assertEqual([], errors, f"Unexpected stale release date errors: {errors}")

    def test_rejects_stale_release_date_in_temp_repo(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_root = Path(tmp_dir)
            (tmp_root / "docs").mkdir()
            bad_doc = tmp_root / "docs" / "test.md"
            bad_doc.write_text("Released 0.2.2 on 2026-09-21.", encoding="utf-8")
            errors = doc_verifier.check_release_dates(tmp_root)
            self.assertTrue(any("2026-09-21" in err for err in errors))

    # -------------------------------------------------------------------------
    # Check 2: Consumer Installation Snippets
    # -------------------------------------------------------------------------

    def test_current_repo_consumer_snippets_are_clean(self):
        errors = doc_verifier.check_all_consumer_snippets(self.repo_root)
        self.assertEqual([], errors, f"Unexpected consumer snippet errors: {errors}")

    def test_consumer_snippets_accepts_valid_released_version(self):
        valid_maven = """
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.2</version>
</dependency>
"""
        errors = doc_verifier.check_consumer_snippets_in_text(valid_maven, released_version="0.2.2")
        self.assertEqual([], errors)

        valid_gradle = 'implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.2")'
        errors = doc_verifier.check_consumer_snippets_in_text(valid_gradle, released_version="0.2.2")
        self.assertEqual([], errors)

    def test_consumer_snippets_rejects_unreleased_or_stale_version(self):
        stale_maven = """
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
"""
        errors = doc_verifier.check_consumer_snippets_in_text(stale_maven, released_version="0.2.2")
        self.assertTrue(any("0.2.0" in err for err in errors))

        stale_gradle = 'implementation("io.github.minh124199:viet-template-spring-boot-starter:0.2.0")'
        errors = doc_verifier.check_consumer_snippets_in_text(stale_gradle, released_version="0.2.2")
        self.assertTrue(any("0.2.0" in err for err in errors))

    def test_consumer_snippets_rejects_unlabeled_snapshot(self):
        snapshot_maven = """
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.3-SNAPSHOT</version>
</dependency>
"""
        errors = doc_verifier.check_consumer_snippets_in_text(snapshot_maven, released_version="0.2.2")
        self.assertTrue(any("without explicit snapshot/development label" in err for err in errors))

    def test_consumer_snippets_accepts_labeled_snapshot(self):
        labeled_maven = """
<!-- Active development snapshot build -->
<dependency>
    <groupId>io.github.minh124199</groupId>
    <artifactId>viet-template-spring-boot-starter</artifactId>
    <version>0.2.3-SNAPSHOT</version>
</dependency>
"""
        errors = doc_verifier.check_consumer_snippets_in_text(labeled_maven, released_version="0.2.2")
        self.assertEqual([], errors)

    # -------------------------------------------------------------------------
    # Check 3: Maven Coordinates & Gradle Plugin ID
    # -------------------------------------------------------------------------

    def test_current_repo_coordinates_are_clean(self):
        errors = doc_verifier.check_all_coordinates(self.repo_root)
        self.assertEqual([], errors, f"Unexpected coordinate errors: {errors}")

    def test_coordinates_rejects_erroneous_maven_group(self):
        bad_text = "Add dependency `com.github.minh124199:viet-template-api:0.2.2`."
        errors = doc_verifier.check_coordinates_in_text(bad_text)
        self.assertTrue(any("Invalid Maven group 'com.github.minh124199'" in err for err in errors))

    def test_coordinates_rejects_erroneous_gradle_plugin_id(self):
        bad_text = 'Apply the plugin using plugin ID `com.github.minh124199.viet-template`.'
        errors = doc_verifier.check_coordinates_in_text(bad_text)
        self.assertTrue(any("Invalid Gradle plugin ID" in err for err in errors))

        bad_syntax = 'plugins { id("io.github.minh124199:viet-template") }'
        errors = doc_verifier.check_coordinates_in_text(bad_syntax)
        self.assertTrue(any("Invalid Gradle plugin ID" in err for err in errors))

    # -------------------------------------------------------------------------
    # Check 4: Compiler Baseline Documentation
    # -------------------------------------------------------------------------

    def test_current_repo_compiler_baseline_documented(self):
        errors = doc_verifier.check_compiler_baseline_documented(self.repo_root)
        self.assertEqual([], errors, f"Unexpected compiler baseline errors: {errors}")

    def test_compiler_baseline_rejects_missing_java21(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_root = Path(tmp_dir)
            (tmp_root / "README.md").write_text("Java baseline is Java 17.", encoding="utf-8")
            errors = doc_verifier.check_compiler_baseline_documented(tmp_root)
            self.assertTrue(any("Java 21" in err for err in errors))

    # -------------------------------------------------------------------------
    # Check 5: Spring Configuration Properties
    # -------------------------------------------------------------------------

    def test_current_repo_spring_properties_are_clean(self):
        errors = doc_verifier.check_all_spring_properties(self.repo_root)
        self.assertEqual([], errors, f"Unexpected spring property errors: {errors}")

    def test_spring_properties_accepts_valid_properties(self):
        valid_text = """
| `viet-template.suffix` | String | Template suffix |
| `viet-template.runtime-compilation-enabled` | boolean | AOT flag |
| `viet-template.security.enabled` | boolean | Security flag |
```properties
viet-template.cache=true
viet-template.order=1
```
"""
        errors = doc_verifier.check_spring_properties_in_text(valid_text, self.known_props)
        self.assertEqual([], errors)

    def test_spring_properties_rejects_unknown_property(self):
        invalid_text = """
| `viet-template.unknown-prop` | String | Fake |
```properties
viet-template.non-existent=123
```
"""
        errors = doc_verifier.check_spring_properties_in_text(invalid_text, self.known_props)
        self.assertTrue(any("viet-template.unknown-prop" in err for err in errors))
        self.assertTrue(any("viet-template.non-existent" in err for err in errors))

    # -------------------------------------------------------------------------
    # Check 6: Internal Markdown Link Resolution
    # -------------------------------------------------------------------------

    def test_current_repo_internal_markdown_links_are_clean(self):
        errors = doc_verifier.check_all_markdown_links(self.repo_root)
        self.assertEqual([], errors, f"Unexpected link errors: {errors}")

    def test_internal_links_rejects_missing_target(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_root = Path(tmp_dir)
            doc_file = tmp_root / "guide.md"
            doc_file.write_text("See [link](non-existent.md).", encoding="utf-8")
            errors = doc_verifier.check_links_in_content(doc_file.read_text(), doc_file, tmp_root)
            self.assertTrue(any("non-existent.md" in err for err in errors))

    def test_internal_links_resolves_valid_anchor(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_root = Path(tmp_dir)
            target_doc = tmp_root / "target.md"
            target_doc.write_text("# Target Section\n\nSome text.", encoding="utf-8")
            source_doc = tmp_root / "source.md"
            source_doc.write_text("See [Target](target.md#target-section).", encoding="utf-8")
            errors = doc_verifier.check_links_in_content(source_doc.read_text(), source_doc, tmp_root)
            self.assertEqual([], errors)

    def test_internal_links_rejects_broken_anchor(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_root = Path(tmp_dir)
            target_doc = tmp_root / "target.md"
            target_doc.write_text("# Target Section\n\nSome text.", encoding="utf-8")
            source_doc = tmp_root / "source.md"
            source_doc.write_text("See [Target](target.md#missing-heading).", encoding="utf-8")
            errors = doc_verifier.check_links_in_content(source_doc.read_text(), source_doc, tmp_root)
            self.assertTrue(any("missing-heading" in err for err in errors))

    # -------------------------------------------------------------------------
    # Check 7: Public Type Classification Alignment
    # -------------------------------------------------------------------------

    def test_current_repo_public_types_are_clean(self):
        errors = doc_verifier.check_all_public_types(self.repo_root)
        self.assertEqual([], errors, f"Unexpected public type errors: {errors}")

    def test_public_types_rejects_unknown_production_type(self):
        bad_text = "Use `io.github.minh124199.viettemplate.api.NonExistentClass` to render."
        errors = doc_verifier.check_public_types_in_text(bad_text, self.known_types)
        self.assertTrue(any("NonExistentClass" in err for err in errors))

    def test_public_types_rejects_classification_mismatch(self):
        # SecurityView is STABLE_API; documenting it as STABLE_SPI should trigger mismatch
        bad_table = "| `io.github.minh124199.viettemplate.spring.security.SecurityView` | `STABLE_SPI` | Description |"
        errors = doc_verifier.check_public_types_in_text(bad_table, self.known_types)
        self.assertTrue(any("Type classification mismatch" in err for err in errors))

    # -------------------------------------------------------------------------
    # Check 8: Diagnostic Code Consistency
    # -------------------------------------------------------------------------

    def test_current_repo_diagnostic_codes_are_clean(self):
        errors = doc_verifier.check_all_diagnostic_codes(self.repo_root)
        self.assertEqual([], errors, f"Unexpected diagnostic code errors: {errors}")

    def test_diagnostic_codes_accepts_known_and_valid_namespaces(self):
        valid_text = "Emits `VTLS2101`, `VTLS2104`, `VTLSEC2401`, and `VTLP1003`."
        errors = doc_verifier.check_diagnostic_codes_in_text(valid_text, self.known_diags)
        self.assertEqual([], errors)

    def test_diagnostic_codes_rejects_unknown_code(self):
        bad_text = "Fails with `SECURITY:UNKNOWN_OP` error."
        errors = doc_verifier.check_diagnostic_codes_in_text(bad_text, self.known_diags)
        self.assertTrue(any("SECURITY:UNKNOWN_OP" in err for err in errors))

    # -------------------------------------------------------------------------
    # Orchestration
    # -------------------------------------------------------------------------

    def test_verify_all_on_repository_passes(self):
        errors = doc_verifier.verify_all(self.repo_root)
        self.assertEqual([], errors, f"verify_all failed with: {errors}")


if __name__ == "__main__":
    unittest.main()
