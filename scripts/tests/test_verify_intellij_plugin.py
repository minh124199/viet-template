"""Unit tests for verify-intellij-plugin.py governance script."""

import importlib.util
import os
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "verify_intellij_plugin",
    ROOT / "scripts" / "verify-intellij-plugin.py",
)
mod = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(mod)

verify_plugin_xml = mod.verify_plugin_xml
verify_build_gradle_kts = mod.verify_build_gradle_kts
verify_settings_gradle_kts = mod.verify_settings_gradle_kts
verify_no_hardcoded_paths = mod.verify_no_hardcoded_paths
verify_distribution_archive = mod.verify_distribution_archive
verify_intellij_plugin = mod.verify_intellij_plugin
DEFAULT_INTELLIJ_DIR = mod.DEFAULT_INTELLIJ_DIR


class VerifyIntelliJPluginTests(unittest.TestCase):
    def test_actual_repository_plugin_passes(self):
        result = verify_intellij_plugin(DEFAULT_INTELLIJ_DIR, check_distribution=False)
        self.assertTrue(
            result["passed"],
            f"Expected verify_intellij_plugin to pass on repository directory, but got errors:\n{result['errors']}",
        )
        self.assertEqual(len(result["errors"]), 0)

    def test_plugin_xml_validation_detects_missing_metadata(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            xml_path = Path(tmp_dir) / "plugin.xml"
            xml_path.write_text("<idea-plugin><id>wrong.id</id></idea-plugin>", encoding="utf-8")
            errors = verify_plugin_xml(xml_path)
            self.assertTrue(any("wrong.id" in e for e in errors))
            self.assertTrue(any("<name>" in e for e in errors))
            self.assertTrue(any("<version>" in e for e in errors))

    def test_plugin_xml_validation_detects_missing_extensions(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            xml_path = Path(tmp_dir) / "plugin.xml"
            # Valid metadata but empty extensions
            xml_path.write_text(
                """<idea-plugin>
                    <id>io.github.minh124199.viet-template-intellij</id>
                    <name>Viet Template</name>
                    <version>1.2.0</version>
                    <vendor>Viet Template</vendor>
                    <depends>com.intellij.modules.platform</depends>
                    <extensions defaultExtensionNs="com.intellij"/>
                </idea-plugin>""",
                encoding="utf-8",
            )
            errors = verify_plugin_xml(xml_path)
            self.assertTrue(any("<fileType>" in e for e in errors))
            self.assertTrue(any("<lang.syntaxHighlighterFactory>" in e for e in errors))
            self.assertTrue(any("<lang.commenter>" in e for e in errors))
            self.assertTrue(any("<externalAnnotator>" in e for e in errors))

    def test_build_gradle_validation_detects_missing_since_build(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            gradle_path = Path(tmp_dir) / "build.gradle.kts"
            gradle_path.write_text(
                """
                plugins { id("org.jetbrains.intellij.platform") }
                java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
                intellijPlatform {
                    intellijIdeaCommunity("2024.2.4")
                    pluginConfiguration {
                        id = "io.github.minh124199.viet-template-intellij"
                        name = "Viet Template"
                        version = "1.2.0"
                        ideaVersion {
                            sinceBuild = "241"
                            untilBuild = "251.*"
                        }
                    }
                }
                val bundleLspServer = tasks.register<Jar>("bundleLspServer") {}
                tasks.named("processResources") {}
                """,
                encoding="utf-8",
            )
            errors = verify_build_gradle_kts(gradle_path)
            self.assertTrue(any("sinceBuild" in e for e in errors))

    def test_settings_gradle_validation(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            settings_path = Path(tmp_dir) / "settings.gradle.kts"
            settings_path.write_text('rootProject.name = "wrong-name"', encoding="utf-8")
            errors = verify_settings_gradle_kts(settings_path)
            self.assertTrue(any("rootProject.name" in e for e in errors))

    def test_hardcoded_path_detection(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            test_dir = Path(tmp_dir)
            clean_file = test_dir / "Clean.java"
            clean_file.write_text("String x = \"relative/path\";", encoding="utf-8")

            bad_file = test_dir / "Bad.java"
            bad_file.write_text("String path = \"/home/user/code\";", encoding="utf-8")

            errors = verify_no_hardcoded_paths(test_dir)
            self.assertEqual(len(errors), 1)
            self.assertIn("Forbidden hardcoded path detected", errors[0])
            self.assertIn("Bad.java", errors[0])

    def test_distribution_archive_validation(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            zip_path = Path(tmp_dir) / "viet-template-intellij-1.1.0.zip"
            # Non-existent
            errors = verify_distribution_archive(zip_path)
            self.assertTrue(any("not found" in e for e in errors))

            # Create dummy malformed zip
            with zipfile.ZipFile(zip_path, "w") as zf:
                zf.writestr("something.txt", "dummy")
            errors = verify_distribution_archive(zip_path)
            self.assertTrue(any("missing main plugin jar" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
