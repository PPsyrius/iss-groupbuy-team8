from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class SbomWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/sbom.yml").read_text(encoding="utf-8")

    def test_one_license_scan_per_part_and_pinned_converter(self):
        self.assertEqual(self.workflow.count("uses: aquasecurity/trivy-action@"), 2)
        self.assertEqual(self.workflow.count("scan-type: fs"), 2)
        self.assertEqual(self.workflow.count("scanners: license"), 2)
        self.assertNotIn("format: cyclonedx", self.workflow)
        self.assertIn('trivy convert --format cyclonedx --output "$RUNNER_TEMP/sbom/${part}.cdx.json"', self.workflow)
        backend, frontend = self.workflow.split("      - name: Frontend licence inventory\n", 1)
        self.assertNotIn("skip-setup-trivy:", backend)
        self.assertIn("skip-setup-trivy: true", frontend.split("      - name: Convert", 1)[0])
        pins = re.findall(r"uses: (aquasecurity/trivy-action@[a-f0-9]{40})", self.workflow)
        self.assertEqual(pins, [pins[0], pins[0]])
        security = (ROOT / ".github/workflows/security.yml").read_text(encoding="utf-8")
        self.assertIn(pins[0], security)
        self.assertIn("TRIVY_VERSION: v0.74.0", self.workflow)
        dependency_scan = security.split("  trivy-deps:\n", 1)[1].split("  trivy-config:\n", 1)[0]
        self.assertEqual(dependency_scan.count("version: v0.74.0"), 2)

    def test_licence_findings_are_advisory_but_errors_are_not_swallowed(self):
        self.assertEqual(self.workflow.count("exit-code: '0'"), 2)
        self.assertNotIn("continue-on-error", self.workflow)
        self.assertNotIn("|| true", self.workflow)
        self.assertIn("    timeout-minutes: 10\n", self.workflow)

    def test_safe_checkout_and_dependency_preparation_are_preserved(self):
        self.assertIn("contents: read", self.workflow)
        self.assertIn("persist-credentials: false", self.workflow)
        self.assertIn("pnpm install --frozen-lockfile --ignore-scripts", self.workflow)
        self.assertIn("TRIVY_OFFLINE_SCAN: 'true'", self.workflow)
        self.assertIn("dependency:go-offline -DskipTests", self.workflow)

    def test_tests_run_before_dependency_preparation_and_releases_need_no_label(self):
        self.assertIn("python3 -B -m unittest discover -s .github/tests -p 'sbom*_test.py'", self.workflow)
        self.assertLess(self.workflow.index("-m unittest discover"), self.workflow.index("dependency:go-offline"))
        self.assertIn("tags: ['v*']", self.workflow)
        push_trigger = self.workflow.split("  push:\n", 1)[1].split("  schedule:\n", 1)[0]
        self.assertNotIn("branches:", push_trigger)
        self.assertIn("workflow_dispatch:", self.workflow)

    def test_pr_scans_are_label_event_opt_in_with_job_level_concurrency(self):
        trigger = self.workflow.split("  pull_request:\n", 1)[1].split("\npermissions:", 1)[0]
        self.assertIn("branches: [dev]", trigger)
        self.assertIn("types: [labeled]", trigger)
        self.assertNotIn("paths:", trigger)
        self.assertNotIn("synchronize", trigger)
        self.assertNotIn("pull_request_target", self.workflow)
        self.assertIn("if: github.event_name != 'pull_request' || github.event.label.name == 'run-sbom'", self.workflow)
        self.assertNotIn("\nconcurrency:", self.workflow)
        self.assertLess(self.workflow.index("    concurrency:"), self.workflow.index("    runs-on:"))

    def test_preparation_scans_conversion_and_validation_are_bounded(self):
        steps = self.workflow.split("      - name: ")[1:]
        for name in ("Populate local Maven", "Install frontend", "Prepare output", "Backend licence",
                     "Frontend licence", "Convert the same inventories", "Validate reports", "Test inventory"):
            step = next(step for step in steps if step.startswith(name))
            self.assertIn("timeout-minutes:", step, name)

    def test_fresh_output_and_validation_precede_upload(self):
        self.assertIn('run: mkdir "$RUNNER_TEMP/sbom"', self.workflow)
        self.assertNotIn('mkdir -p "$RUNNER_TEMP/sbom"', self.workflow)
        validate = self.workflow.index("python3 .github/scripts/sbom-report.py")
        self.assertLess(self.workflow.index("trivy convert"), validate)
        self.assertLess(validate, self.workflow.index("uses: actions/upload-artifact@"))
        for argument in ('--revision "$GITHUB_SHA"', '--ref "$GITHUB_REF"', '--trivy-version "$TRIVY_VERSION"',
                         '--run-url "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"'):
            self.assertIn(argument, self.workflow)

    def test_seven_explicit_artifact_files_and_existing_retention(self):
        upload = self.workflow.split("      - name: Upload SBOMs and licence reports\n", 1)[1]
        self.assertIn("if: always()", upload)
        self.assertIn("if-no-files-found: error", upload)
        self.assertIn("retention-days: 90", upload)
        files = re.findall(r"\$\{\{ runner.temp \}\}/sbom/([^\s]+)", upload)
        self.assertEqual(set(files), {"backend-licences.json", "frontend-licences.json", "backend.cdx.json", "frontend.cdx.json", "inventory.json", "summary.md", "manifest.json"})


if __name__ == "__main__":
    unittest.main()
