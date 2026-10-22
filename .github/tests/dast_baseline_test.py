from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


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
        self.assertIn("zap.sh -cmd -addoninstall pscanrulesBeta -autorun /zap/wrk/zap-plan.yaml", self.workflow)
        self.assertIn("timeout --signal=TERM --kill-after=30s 10m docker run", self.workflow)
        self.assertIn("timeout-minutes: 20", self.workflow)
        self.assertRegex(self.workflow, r"ghcr\.io/zaproxy/zaproxy@sha256:[a-f0-9]{64}")

    def test_target_preflight_precedes_the_scanner_and_uses_a_credential_free_checkout(self):
        self.assertLess(self.workflow.index("preflight --output"), self.workflow.index("docker run"))
        self.assertIn("persist-credentials: false", self.workflow)
        self.assertIn("BASE_URL: ${{ inputs.base-url || vars.FRONTEND_URL_DEV }}", self.workflow)

    def test_preflight_has_its_own_wall_clock_deadline_not_only_socket_timeouts(self):
        preflight = self.workflow.split("      - name: Validate the live target and prepare required requests\n", 1)[1].split("      - name:", 1)[0]
        self.assertIn("timeout-minutes: 1", preflight)
        self.assertIn("timeout --signal=TERM --kill-after=5s 30s python3", preflight)

    def test_fast_dast_regressions_run_before_any_live_target_request(self):
        self.assertIn("python3 -B -m unittest discover -s .github/tests -p 'dast*_test.py'", self.workflow)
        self.assertLess(self.workflow.index("-m unittest discover"), self.workflow.index("preflight --output"))

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
