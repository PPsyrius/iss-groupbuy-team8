import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "terraform_checkov_summary", ROOT / ".github/scripts/terraform_checkov_summary.py"
)
REPORT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPORT)


def checkov_step(workflow):
    return workflow.split("      - name: Checkov policy scan (advisory findings)\n", 1)[1].split(
        "\n      - ", 1
    )[0].rstrip()


def fixture():
    return {
        "check_type": "terraform",
        "summary": {"passed": 1, "failed": 1, "skipped": 0,
                    "parsing_errors": 0, "resource_count": 1},
        "results": {
            "passed_checks": [{}],
            "failed_checks": [{"check_id": "CKV_AWS_1", "resource": "aws_example.test",
                               "check_name": "Example finding"}],
            "skipped_checks": [], "parsing_errors": [],
        },
    }


class TerraformReportTest(unittest.TestCase):
    def test_failed_policy_checks_are_reported_not_rejected(self):
        text = REPORT.summarize(fixture())
        self.assertIn("failed: 1", text)
        self.assertIn("CKV_AWS_1 | aws_example.test | Example finding", text)

    def test_skipped_checks_include_the_reason_and_escape_markdown(self):
        report = fixture()
        report["summary"]["skipped"] = 1
        report["results"]["skipped_checks"] = [{
            "check_id": "CKV_AWS_2", "resource": "test",
            "check_name": "Policy", "suppress_comment": "Reviewed | reason\nline",
        }]
        self.assertIn("Reviewed \\| reason line", REPORT.summarize(report))

    def test_empty_scan_is_rejected(self):
        report = fixture()
        report["summary"]["resource_count"] = 0
        with self.assertRaises(ValueError):
            REPORT.summarize(report)

    def test_parsing_errors_are_rejected(self):
        for field in ("summary", "results"):
            report = fixture()
            report[field]["parsing_errors"] = 1 if field == "summary" else ["bad.tf"]
            with self.assertRaises(ValueError):
                REPORT.summarize(report)

    def test_wrong_report_type_is_rejected(self):
        for report in ([], {"check_type": "secrets"}):
            with self.assertRaises(ValueError):
                REPORT.summarize(report)

    def test_inconsistent_counts_are_rejected(self):
        report = fixture()
        report["summary"]["failed"] = 0
        with self.assertRaises(ValueError):
            REPORT.summarize(report)

    def test_resources_without_any_policy_results_are_rejected(self):
        report = fixture()
        for status in ("passed", "failed", "skipped"):
            report["summary"][status] = 0
            report["results"][f"{status}_checks"] = []
        with self.assertRaises(ValueError):
            REPORT.summarize(report)

    def test_cli_fails_on_malformed_or_incomplete_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            for contents in ("not json", "{}", json.dumps({"check_type": "terraform"})):
                path.write_text(contents, encoding="utf-8")
                result = subprocess.run(
                    [sys.executable, str(ROOT / ".github/scripts/terraform_checkov_summary.py"),
                     str(path)], capture_output=True, text=True, check=False,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")
                self.assertIn("Invalid Checkov report:", result.stderr)

    def test_cli_succeeds_with_advisory_findings(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            path.write_text(json.dumps(fixture()), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(ROOT / ".github/scripts/terraform_checkov_summary.py"),
                 str(path)], capture_output=True, text=True, check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("failed: 1", result.stdout)


class TerraformWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/infra-swe5006.yml").read_text(encoding="utf-8")

    def test_both_stacks_and_tool_versions_are_pinned(self):
        for value in ("[frontend-stack, backend-app-stack]", "TERRAFORM_VERSION: 1.16.4",
                      "CHECKOV_VERSION: 3.3.22", "fail-fast: false"):
            self.assertIn(value, self.workflow)
        actions = re.findall(r"uses: (\S+)", self.workflow)
        self.assertTrue(actions)
        for action in actions:
            self.assertRegex(action, r"@[a-f0-9]{40}$")

    def test_static_commands_preserve_lockfile_and_disable_backend(self):
        for value in ("fmt -check -recursive", "init -backend=false -input=false -lockfile=readonly",
                      'validate', '--framework terraform --download-external-modules false',
                      "--soft-fail --output json", "terraform_checkov_summary.py"):
            self.assertIn(value, self.workflow)
        self.assertNotIn("continue-on-error", self.workflow)

    def test_no_cloud_credentials_state_or_mutation_commands(self):
        for value in ("id-token:", "secrets.", "configure-aws-credentials", "environment:",
                      "terraform plan", "terraform apply", "terraform destroy", "pull_request_target"):
            self.assertNotIn(value, self.workflow)
        self.assertIn("permissions: {}", self.workflow)
        self.assertIn("contents: read", self.workflow)
        self.assertIn("persist-credentials: false", self.workflow)

    def test_pr_triggers_include_inputs_and_tooling(self):
        for path in ("infra/terraform/**", "deployment/frontend/cloudfront/**",
                     "deployment/backend/ecr-lifecycle-policy.json",
                     ".github/workflows/infra-swe5006.yml",
                     ".github/scripts/terraform_checkov_summary.py",
                     ".github/tests/terraform_ci_test.py"):
            self.assertIn(json.dumps(path), self.workflow)
        self.assertIn("branches: [dev]", self.workflow)

    def test_checkov_step_excludes_later_steps(self):
        later = '\n      - name: Later step\n        run: echo unexpected\n'
        self.assertEqual(checkov_step(self.workflow), checkov_step(self.workflow.rstrip() + later))


class TerraformWorkflowShellTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Prefer Git Bash over the Windows WSL launcher. Linux CI uses bash.
        git_bash = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
        cls.bash = str(git_bash) if os.name == "nt" and git_bash.is_file() else shutil.which("bash")
        if not cls.bash:
            raise unittest.SkipTest("Bash is needed for workflow shell regressions")
        workflow = (ROOT / ".github/workflows/infra-swe5006.yml").read_text(encoding="utf-8")
        cls.step = checkov_step(workflow)
        cls.script = textwrap.dedent(cls.step.split("        run: |\n", 1)[1])

    def run_step(self, contents, scanner_exit=0, summary_path_is_directory=False):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            if summary_path_is_directory:
                summary.mkdir()
            else:
                summary.write_text("Existing summary\n", encoding="utf-8")
            env = dict(os.environ, RUNNER_TEMP=Path(directory).as_posix(), STACK="frontend-stack",
                       GITHUB_STEP_SUMMARY=summary.as_posix(), CHECKOV_VERSION="3.3.22",
                       CHECKOV_REPORT=contents, SCANNER_EXIT=str(scanner_exit),
                       PYTHON_EXE=Path(sys.executable).as_posix())
            # Only external scanner installation is stubbed. Execute the actual
            # workflow block, real reporter and real summary writes. Use -e
            # without pipefail so the original masked-pipeline bug also regresses.
            stubs = '''pipx() { printf '%s' "$CHECKOV_REPORT"; return "$SCANNER_EXIT"; }
python3() { "$PYTHON_EXE" "$@"; }
'''
            result = subprocess.run(
                [self.bash, "--noprofile", "--norc", "-e", "-c", stubs + self.script],
                cwd=ROOT, env=env, capture_output=True, text=True, check=False,
            )
            published = None if summary_path_is_directory else summary.read_text(encoding="utf-8")
            return result, published

    def test_workflow_explicitly_uses_bash(self):
        self.assertIn("        shell: bash\n", self.step.split("        run:", 1)[0])

    def test_advisory_findings_succeed_and_publish(self):
        result, published = self.run_step(json.dumps(fixture()))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("failed: 1", result.stdout)
        self.assertIn("failed: 1", published)
        self.assertTrue(published.startswith("Existing summary\n"))

    def test_invalid_report_fails_step_without_publishing(self):
        for contents in ("{}", "not json"):
            with self.subTest(contents=contents):
                result, published = self.run_step(contents)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Invalid Checkov report:", result.stderr)
                self.assertEqual(published, "Existing summary\n")

    def test_empty_scan_and_parsing_error_fail_step(self):
        for field, value in (("resource_count", 0), ("parsing_errors", 1)):
            with self.subTest(field=field):
                report = fixture()
                report["summary"][field] = value
                result, published = self.run_step(json.dumps(report))
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(published, "Existing summary\n")

    def test_scanner_failure_fails_step_even_with_valid_json(self):
        result, published = self.run_step(json.dumps(fixture()), scanner_exit=2)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(published, "Existing summary\n")

    def test_summary_write_failure_fails_step(self):
        result, _ = self.run_step(json.dumps(fixture()), summary_path_is_directory=True)
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
