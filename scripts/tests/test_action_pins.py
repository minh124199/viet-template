from pathlib import Path
import re
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS_DIR = ROOT / ".github" / "workflows"

HEX_SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")
VERSION_COMMENT_PATTERN = re.compile(r"^v\d+(\.\d+)*(-[a-zA-Z0-9.]+)?$")
USES_LINE_PATTERN = re.compile(r"^\s*uses:\s*(.+)$")


def validate_uses_expression(raw_expr: str, location: str = "") -> list[str]:
    """
    Validates a single GitHub Actions uses: expression string.
    Returns a list of validation error messages (empty if valid).
    """
    errors = []
    loc_prefix = f"{location}: " if location else ""

    if "#" in raw_expr:
        action_part, comment_part = raw_expr.split("#", 1)
        action_part = action_part.strip().strip("'\"")
        comment_part = comment_part.strip()
    else:
        action_part = raw_expr.strip().strip("'\"")
        comment_part = ""

    # Local actions and local reusable workflows (starting with ./) are allowed
    if action_part.startswith("./"):
        return errors

    if "@" not in action_part:
        errors.append(f"{loc_prefix}Action '{action_part}' must specify a version pin with '@'")
        return errors

    action_name, _, ref = action_part.partition("@")
    action_name = action_name.strip()
    ref = ref.strip()

    if not HEX_SHA_PATTERN.match(ref):
        errors.append(
            f"{loc_prefix}Action '{action_name}' reference '{ref}' is not an exact 40-character commit SHA"
        )

    if not comment_part:
        errors.append(
            f"{loc_prefix}Action '{action_part}' is missing an adjacent version comment (expected '# v<version>')"
        )
    elif not VERSION_COMMENT_PATTERN.match(comment_part):
        errors.append(
            f"{loc_prefix}Action '{action_part}' has invalid version comment '{comment_part}' (expected '# v<version>')"
        )

    return errors


def validate_workflow_content(content: str, filename: str = "workflow.yml") -> list[str]:
    """
    Validates all uses: lines in a workflow content string.
    Returns a list of validation errors.
    """
    errors = []
    lines = content.splitlines()

    for lineno, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        match = USES_LINE_PATTERN.match(line)
        if match:
            raw_expr = match.group(1).strip()
            line_errors = validate_uses_expression(raw_expr, location=f"{filename}:{lineno}")
            errors.extend(line_errors)

    return errors


def validate_workflow_file(file_path: Path) -> list[str]:
    """
    Validates all action pins in a workflow file.
    """
    content = file_path.read_text(encoding="utf-8")
    return validate_workflow_content(content, filename=file_path.name)


class ActionPinsWorkflowTests(unittest.TestCase):
    """
    Validates that all actual workflows under .github/workflows/ adhere to
    the 40-character commit-SHA pinning and release-comment policy.
    """

    def test_all_workflow_files_pass_action_pin_validation(self):
        workflow_files = sorted(WORKFLOWS_DIR.glob("*.yml"))
        self.assertGreaterEqual(
            len(workflow_files), 7, f"Expected at least 7 workflow files under {WORKFLOWS_DIR}"
        )

        all_errors = []
        total_actions_scanned = 0

        for workflow_file in workflow_files:
            content = workflow_file.read_text(encoding="utf-8")
            # Ensure valid YAML
            data = yaml.safe_load(content)
            self.assertIsInstance(
                data, dict, f"Workflow file {workflow_file} must parse as a valid YAML mapping"
            )

            file_errors = validate_workflow_file(workflow_file)
            all_errors.extend(file_errors)

            for line in content.splitlines():
                if not line.strip().startswith("#") and USES_LINE_PATTERN.match(line):
                    total_actions_scanned += 1

        self.assertEqual([], all_errors, f"Found action pin errors:\n" + "\n".join(all_errors))
        self.assertGreater(
            total_actions_scanned,
            0,
            "Expected at least one action use across workflows to be scanned",
        )

    def test_modernized_shas_and_comments_are_present(self):
        canonical_pins = {
            "actions/checkout": "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1",
            "actions/setup-java": "actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1",
            "actions/setup-python": "actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97 # v7.0.0",
            "actions/upload-artifact": "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1",
            "gradle/actions/setup-gradle": "gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0",
        }

        counts = {family: 0 for family in canonical_pins}
        for workflow_file in sorted(WORKFLOWS_DIR.glob("*.yml")):
            content = workflow_file.read_text(encoding="utf-8")
            for lineno, line in enumerate(content.splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith("#"):
                    continue
                match = USES_LINE_PATTERN.match(line)
                if not match:
                    continue
                raw_expr = match.group(1).strip()
                action_target = raw_expr.split("#", 1)[0].strip().strip("'\"")
                action_name = action_target.partition("@")[0].strip()
                if action_name in canonical_pins:
                    self.assertEqual(
                        canonical_pins[action_name],
                        raw_expr,
                        f"Action {action_name} in {workflow_file.name}:{lineno} does not match canonical pin",
                    )
                    counts[action_name] += 1

        for family, count in counts.items():
            self.assertGreater(
                count,
                0,
                f"Expected action family '{family}' to be used at least once across workflows",
            )

    def test_stale_action_shas_are_completely_absent(self):
        stale_shas = [
            "11bd71901bbe5b1630ceea73d27597364c9af683",  # old checkout v4.2.2
            "cf277c60eb25467037889841efdb72551f06f6c3",  # old setup-java v4.9.1
            "a26af69be951a213d495a4c3e4e4022e16d87065",  # old setup-python v5.6.0
            "65c4c4a1ddee5b72f698fdd19549f0f0fb45cf08",  # old upload-artifact v4.6.0
            "ea165f8d65b6e75b540449e92b4886f43607fa02",  # old upload-artifact v4.6.2
            "65462800fd760344b1a7b4382951275a0abb4808",  # old upload-artifact v4.3.3
            "748248ddd2a24f49513d8f472f81c3a07d4d50e1",  # old setup-gradle v4.4.4
        ]
        for workflow_file in sorted(WORKFLOWS_DIR.glob("*.yml")):
            content = workflow_file.read_text(encoding="utf-8")
            for stale_sha in stale_shas:
                self.assertNotIn(
                    stale_sha,
                    content,
                    f"Stale SHA {stale_sha} found in {workflow_file.name}",
                )


class SyntheticActionPinNegativeTests(unittest.TestCase):
    """
    Synthetic tests verifying that mutable tags, truncated SHAs, missing
    comments, and malformed uses expressions are strictly rejected, while
    local references and properly pinned actions are accepted.
    """

    def test_valid_external_action_passes(self):
        line = "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertEqual([], errors)

    def test_valid_local_action_passes(self):
        line = "uses: ./.github/actions/custom-action"
        errors = validate_workflow_content(line)
        self.assertEqual([], errors)

    def test_valid_local_workflow_passes(self):
        line = "uses: ./.github/workflows/reusable.yml"
        errors = validate_workflow_content(line)
        self.assertEqual([], errors)

    def test_rejects_mutable_tag_version(self):
        line = "uses: actions/checkout@v7 # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("not an exact 40-character commit SHA" in e for e in errors))

    def test_rejects_mutable_tag_main(self):
        line = "uses: actions/checkout@main # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("not an exact 40-character commit SHA" in e for e in errors))

    def test_rejects_mutable_tag_master(self):
        line = "uses: actions/checkout@master # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("not an exact 40-character commit SHA" in e for e in errors))

    def test_rejects_mutable_tag_latest(self):
        line = "uses: actions/checkout@latest # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("not an exact 40-character commit SHA" in e for e in errors))

    def test_rejects_truncated_sha(self):
        line = "uses: actions/checkout@3d3c42e # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("not an exact 40-character commit SHA" in e for e in errors))

    def test_rejects_missing_version_comment(self):
        line = "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("missing an adjacent version comment" in e for e in errors))

    def test_rejects_invalid_version_comment_non_v_prefix(self):
        line = "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # latest"
        errors = validate_workflow_content(line)
        self.assertTrue(any("invalid version comment" in e for e in errors))

    def test_rejects_missing_at_symbol(self):
        line = "uses: actions/checkout # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertTrue(any("must specify a version pin with '@'" in e for e in errors))

    def test_handles_quoted_action_spec(self):
        line = "uses: 'actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1' # v7.0.1"
        errors = validate_workflow_content(line)
        self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
