import os
from pathlib import Path
import re
import shutil
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
BETA_ADDON = "pscanrulesBeta-beta-50.zap"
BETA_SHA256 = "a979f7cd5d8be10e0338b417e006ec1e2f6ec79101010d570ddb5199892e921b"
BETA_URL = "https://github.com/zaproxy/zap-extensions/releases/download/pscanrulesBeta-v50/" + BETA_ADDON


def workflow_script(name):
    workflow = (ROOT / ".github/workflows/dast-baseline.yml").read_text(encoding="utf-8")
    step = workflow.split("      - name: " + name + "\n", 1)[1].split("      - name:", 1)[0]
    return textwrap.dedent(step.split("        run: |\n", 1)[1])


def download_script():
    return workflow_script("Download and verify pinned beta passive rules")


def bash_path():
    return (str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe")
            if os.name == "nt" else shutil.which("bash"))


class DastBaselineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/dast-baseline.yml").read_text(encoding="utf-8")

    def test_one_zap_session_without_a_surface_matrix(self):
        jobs = self.workflow.split("jobs:\n", 1)[1]
        self.assertEqual(re.findall(r"^  ([\w-]+):$", jobs, re.MULTILINE), ["zap-baseline"])
        self.assertNotIn("strategy:", jobs)
        self.assertNotIn("${{ matrix.", self.workflow)
        self.assertEqual(self.workflow.count("docker run --rm"), 1)

    def test_one_bounded_automation_plan_retains_the_pinned_scanner(self):
        self.assertIn("zap.sh -cmd -silent -autorun /zap/wrk/zap-plan.yaml", self.workflow)
        self.assertIn("timeout --signal=TERM --kill-after=30s 10m docker run", self.workflow)
        self.assertIn("timeout-minutes: 20", self.workflow)
        self.assertRegex(self.workflow, r"ghcr\.io/zaproxy/zaproxy@sha256:[a-f0-9]{64}")

    def test_beta_addon_is_pinned_checked_and_mounted_as_one_read_only_file(self):
        script = download_script()
        self.assertIn(BETA_URL, script)
        self.assertIn(BETA_SHA256, script)
        self.assertIn("sha256sum --check --strict", script)
        self.assertIn('chmod 644 "$addon"', script)
        self.assertIn('type=bind,source=${RUNNER_TEMP}/zap-addons/' + BETA_ADDON
                      + ',target=/zap/plugin/' + BETA_ADDON + ',readonly', self.workflow)
        self.assertLess(self.workflow.index("preflight --output"), self.workflow.index("sha256sum --check"))
        self.assertLess(self.workflow.index("sha256sum --check"), self.workflow.index("docker run"))
        self.assertNotIn("-addoninstall", self.workflow)
        self.assertNotIn("-addonupdate", self.workflow)

    def test_addon_download_has_retries_and_independent_time_bounds(self):
        step = self.workflow.split("      - name: Download and verify pinned beta passive rules\n", 1)[1].split("      - name:", 1)[0]
        self.assertIn("timeout-minutes: 3", step)
        for option in ("--fail", "--location", "--proto '=https'", "--proto-redir '=https'",
                       "--retry 3", "--retry-all-errors", "--retry-max-time 120",
                       "--connect-timeout 10", "--max-time 30"):
            self.assertIn(option, step)

    def test_target_preflight_precedes_the_scanner_and_uses_a_credential_free_checkout(self):
        self.assertLess(self.workflow.index("preflight --output"), self.workflow.index("docker run"))
        self.assertIn("persist-credentials: false", self.workflow)
        self.assertIn("BASE_URL: ${{ inputs.base-url || vars.FRONTEND_URL_DEV }}", self.workflow)

    def test_preflight_has_its_own_wall_clock_deadline_not_only_socket_timeouts(self):
        preflight = self.workflow.split("      - name: Validate the live target and prepare required requests\n", 1)[1].split("      - name:", 1)[0]
        self.assertIn("timeout-minutes: 1", preflight)
        self.assertIn("timeout --signal=TERM --kill-after=5s 30s python3", preflight)

    def test_tooling_regressions_run_in_security_ci_not_the_live_scan(self):
        security = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        self.assertIn("python3 -m unittest discover -s .github/tests -p '*_test.py'", security)
        self.assertNotIn("-m unittest discover", self.workflow)

    def test_reports_share_one_artifact_but_exclude_the_raw_har(self):
        self.assertIn('work="${RUNNER_TEMP}/zap-baseline"', self.workflow)
        self.assertIn("if: always()", self.workflow)
        upload = self.workflow.split("      - name: Upload ZAP reports\n", 1)[1]
        self.assertIn("name: zap-baseline", upload)
        for name in ("report.*", "coverage.*", "preflight.json", "requests.json", "zap-plan.yaml", "passive-*.json"):
            self.assertIn("${{ runner.temp }}/zap-baseline/" + name, upload)
        self.assertNotIn("traffic.har", upload)
        self.assertIn("retention-days: 30", self.workflow)
        self.assertNotIn("SURFACE", self.workflow)

    def test_coverage_verification_runs_after_failed_or_skipped_scans(self):
        verify = self.workflow.split("      - name: Verify recorded coverage and summarise findings\n", 1)[1]
        self.assertIn("if: always() && steps.checkout.outcome == 'success'", verify)
        self.assertIn("SCAN_OUTCOME: ${{ steps.zap.outcome }}", verify)
        self.assertIn('--scan-outcome "$SCAN_OUTCOME"', verify)
        self.assertIn('--summary "$GITHUB_STEP_SUMMARY"', verify)
        self.assertNotIn("continue-on-error", self.workflow)

    def test_scan_implementation_changes_are_in_the_pr_filter(self):
        self.assertIn("'.github/scripts/dast-baseline.py'", self.workflow)
        self.assertIn("'.github/tests/dast*_test.py'", self.workflow)


if __name__ == "__main__":
    unittest.main()
