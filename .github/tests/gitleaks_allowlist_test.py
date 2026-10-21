from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GitleaksAllowlistTest(unittest.TestCase):
    def test_only_reviewed_historical_digest_fingerprints_are_ignored(self):
        lines = (ROOT / ".gitleaksignore").read_text(encoding="utf-8").splitlines()
        fingerprints = [line for line in lines if line and not line.startswith("#")]
        expected_lines = {11, 12, 44, 46, 67, 79, 117, 118, 119, 120, 126, 155, 156, 167, 168, 169, 174, 175, 177, 178}
        prefix = "983b401a62619f7d9b2087e92f8367bb88eeadba:scripts/stripe-dev-contract-baseline.json:generic-api-key:"
        self.assertEqual(set(fingerprints), {f"{prefix}{line}" for line in expected_lines})
        self.assertEqual(len(fingerprints), len(expected_lines))
        for fingerprint in fingerprints:
            self.assertRegex(fingerprint, r"^[a-f0-9]{40}:[^:]+:generic-api-key:[0-9]+$")

    def test_secret_scan_remains_independent_and_codeql_remains_opt_in(self):
        workflow = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        gitleaks = workflow.split("  gitleaks:\n", 1)[1].split("\n  trivy-deps:", 1)[0]
        self.assertNotRegex(gitleaks, r"(?m)^    needs:")
        self.assertIn("fetch-depth: 0", gitleaks)
        self.assertIn("vars.ENABLE_CODEQL == 'true'", workflow)

    def test_allowlist_changes_run_their_policy_tests(self):
        workflow = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        workflow_filter = workflow.split("filters: |", 1)[1].split("deployment:", 1)[0]
        self.assertIn("- '.gitleaksignore'", workflow_filter)
        self.assertIn("- '.gitleaks.toml'", workflow_filter)


if __name__ == "__main__":
    unittest.main()
