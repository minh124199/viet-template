"""
scripts/tests/test_dependabot_config.py

Automated validation of GitHub Dependabot configuration (.github/dependabot.yml).

Architectural connection to scripts/tests/test_action_pins.py:
- Dependabot is configured strictly for the "github-actions" package ecosystem
  on a weekly schedule to propose dependency updates.
- Dependabot automatically proposes version updates and commit-SHA updates.
- scripts/tests/test_action_pins.py strictly enforces that all external actions
  remain pinned to immutable 40-character commit SHAs with same-line '# vX.Y.Z'
  release comments.
- Consequently, auto-merge is prohibited; each proposed update must undergo
  full CI verification, review, and adhere to SHA pinning policy before merging.
- No other package ecosystems (Maven, Gradle, Docker, etc.) are permitted in
  Dependabot to avoid supply chain drift and unvetted dependency updates.
"""

from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
DEPENDABOT_FILE = ROOT / ".github" / "dependabot.yml"

ALLOWED_UPDATE_KEYS = {
    "package-ecosystem",
    "directory",
    "schedule",
    "open-pull-requests-limit",
}


def _check_no_automerge(obj, path: str = "") -> list[str]:
    """Recursively checks that no key or string value references auto-merge."""
    errors = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            key_str = str(k).lower()
            if "automerge" in key_str or "auto-merge" in key_str:
                errors.append(f"Disallowed auto-merge configuration in key '{path}{k}'")
            errors.extend(_check_no_automerge(v, path=f"{path}{k}."))
    elif isinstance(obj, list):
        for idx, item in enumerate(obj):
            errors.extend(_check_no_automerge(item, path=f"{path}[{idx}]."))
    elif isinstance(obj, str):
        val_str = obj.lower()
        if "automerge" in val_str or "auto-merge" in val_str:
            errors.append(f"Disallowed auto-merge reference in value at '{path}': '{obj}'")
    return errors


def validate_dependabot_dict(data: dict) -> list[str]:
    """
    Validates a parsed Dependabot configuration dictionary against project policy:
    1. Mapping structure with version 2.
    2. Exactly one update entry configured.
    3. Package ecosystem must be strictly 'github-actions'.
    4. Target directory must be '/'.
    5. Schedule interval must be 'weekly'.
    6. No auto-merge configurations exist.
    7. No disallowed ecosystems or unexpected keys exist.
    """
    errors = []
    if not isinstance(data, dict):
        return ["Dependabot configuration must be a YAML dictionary mapping"]

    version = data.get("version")
    if version != 2:
        errors.append(f"Dependabot version must be 2, found: {version!r}")

    updates = data.get("updates")
    if not isinstance(updates, list):
        errors.append(f"'updates' must be a list, found: {type(updates).__name__}")
        return errors

    if len(updates) != 1:
        errors.append(f"Expected exactly 1 update entry in 'updates', found {len(updates)}")
        return errors

    entry = updates[0]
    if not isinstance(entry, dict):
        errors.append(f"Update entry must be a dictionary mapping, found: {type(entry).__name__}")
        return errors

    extra_keys = set(entry.keys()) - ALLOWED_UPDATE_KEYS
    if extra_keys:
        errors.append(f"Disallowed or unrecognized keys in update entry: {sorted(extra_keys)}")

    ecosystem = entry.get("package-ecosystem")
    if ecosystem != "github-actions":
        errors.append(
            f"Package ecosystem must be strictly 'github-actions', found: {ecosystem!r}"
        )

    directory = entry.get("directory")
    if directory != "/":
        errors.append(f"Target directory must be '/', found: {directory!r}")

    schedule = entry.get("schedule")
    if not isinstance(schedule, dict):
        errors.append(f"'schedule' must be a dictionary, found: {type(schedule).__name__}")
    else:
        interval = schedule.get("interval")
        if interval != "weekly":
            errors.append(f"Schedule interval must be 'weekly', found: {interval!r}")

    # Enforce absence of auto-merge
    errors.extend(_check_no_automerge(data))

    return errors


def validate_dependabot_content(content: str) -> list[str]:
    """Parses YAML content and validates it against Dependabot policy."""
    try:
        data = yaml.safe_load(content)
    except yaml.YAMLError as exc:
        return [f"YAML syntax error: {exc}"]
    return validate_dependabot_dict(data)


def validate_dependabot_file(file_path: Path) -> list[str]:
    """Reads a file and validates its content against Dependabot policy."""
    if not file_path.is_file():
        return [f"Dependabot configuration file not found at: {file_path}"]
    content = file_path.read_text(encoding="utf-8")
    return validate_dependabot_content(content)


class ActualDependabotConfigTests(unittest.TestCase):
    """
    Tests that .github/dependabot.yml exists and adheres strictly to
    the project Dependabot version update policy.
    """

    def test_dependabot_file_exists(self):
        self.assertTrue(
            DEPENDABOT_FILE.is_file(),
            f"Dependabot configuration file missing at {DEPENDABOT_FILE}",
        )

    def test_dependabot_file_passes_policy_validation(self):
        errors = validate_dependabot_file(DEPENDABOT_FILE)
        self.assertEqual([], errors, f"Dependabot validation errors:\n" + "\n".join(errors))

    def test_dependabot_exact_expected_structure(self):
        content = DEPENDABOT_FILE.read_text(encoding="utf-8")
        data = yaml.safe_load(content)
        self.assertEqual(data.get("version"), 2)

        updates = data.get("updates", [])
        self.assertEqual(len(updates), 1)

        entry = updates[0]
        self.assertEqual(entry.get("package-ecosystem"), "github-actions")
        self.assertEqual(entry.get("directory"), "/")
        self.assertEqual(entry.get("schedule", {}).get("interval"), "weekly")
        self.assertEqual(entry.get("open-pull-requests-limit"), 10)

    def test_no_automerge_configured(self):
        content = DEPENDABOT_FILE.read_text(encoding="utf-8").lower()
        self.assertNotIn("automerge", content)
        self.assertNotIn("auto-merge", content)

    def test_no_disallowed_package_ecosystems(self):
        content = DEPENDABOT_FILE.read_text(encoding="utf-8")
        data = yaml.safe_load(content)
        ecosystems = [u.get("package-ecosystem") for u in data.get("updates", [])]
        self.assertEqual(["github-actions"], ecosystems)


class SyntheticDependabotConfigNegativeTests(unittest.TestCase):
    """
    Synthetic tests verifying that invalid versions, multiple ecosystems,
    non-weekly schedules, non-root directories, and auto-merge directives
    are rejected deterministically.
    """

    def test_valid_config_passes(self):
        valid_yaml = """
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
"""
        errors = validate_dependabot_content(valid_yaml)
        self.assertEqual([], errors)

    def test_rejects_invalid_version(self):
        bad_yaml = """
version: 1
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
"""
        errors = validate_dependabot_content(bad_yaml)
        self.assertTrue(any("version must be 2" in e for e in errors))

    def test_rejects_empty_updates(self):
        bad_yaml = """
version: 2
updates: []
"""
        errors = validate_dependabot_content(bad_yaml)
        self.assertTrue(any("Expected exactly 1 update entry" in e for e in errors))

    def test_rejects_multiple_updates(self):
        bad_yaml = """
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
"""
        errors = validate_dependabot_content(bad_yaml)
        self.assertTrue(any("Expected exactly 1 update entry" in e for e in errors))

    def test_rejects_disallowed_ecosystems(self):
        for eco in ["maven", "gradle", "docker", "npm", "pip"]:
            bad_yaml = f"""
version: 2
updates:
  - package-ecosystem: "{eco}"
    directory: "/"
    schedule:
      interval: "weekly"
"""
            errors = validate_dependabot_content(bad_yaml)
            self.assertTrue(
                any("strictly 'github-actions'" in e for e in errors),
                f"Expected error for ecosystem {eco}",
            )

    def test_rejects_non_root_directory(self):
        bad_yaml = """
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/.github/workflows"
    schedule:
      interval: "weekly"
"""
        errors = validate_dependabot_content(bad_yaml)
        self.assertTrue(any("Target directory must be '/'" in e for e in errors))

    def test_rejects_non_weekly_schedule(self):
        for interval in ["daily", "monthly"]:
            bad_yaml = f"""
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "{interval}"
"""
            errors = validate_dependabot_content(bad_yaml)
            self.assertTrue(
                any("interval must be 'weekly'" in e for e in errors),
                f"Expected error for interval {interval}",
            )

    def test_rejects_automerge_key(self):
        bad_yaml = """
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    automerge: true
"""
        errors = validate_dependabot_content(bad_yaml)
        self.assertTrue(any("auto-merge" in e.lower() for e in errors))

    def test_rejects_automerge_value(self):
        bad_yaml = """
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    labels:
      - "auto-merge"
"""
        errors = validate_dependabot_content(bad_yaml)
        self.assertTrue(any("auto-merge" in e.lower() for e in errors))


if __name__ == "__main__":
    unittest.main()
