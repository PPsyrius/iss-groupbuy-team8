"""Guard browser caching without reintroducing per-job apt installs."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class FrontendBrowserSetupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        workflow = (ROOT / ".github/workflows/ci-frontend.yml").read_text(encoding="utf-8")
        cls.jobs = {
            name: workflow.split(f"  {name}:\n", 1)[1].split("\n  package-dev:", 1)[0]
            for name in ("e2e-preview", "e2e-dev")
        }

    def test_native_dependencies_use_the_pinned_runner_without_apt(self):
        for name, job in self.jobs.items():
            with self.subTest(job=name):
                self.assertIn("runs-on: ubuntu-24.04", job)
                self.assertIn("run: pnpm exec playwright install --only-shell chromium", job)
                self.assertNotIn("run: pnpm exec playwright install --with-deps", job)
                self.assertNotIn("PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS", job)

    def test_browser_cache_is_exact_and_platform_and_lockfile_scoped(self):
        for name, job in self.jobs.items():
            with self.subTest(job=name):
                self.assertIn("path: ~/.cache/ms-playwright", job)
                self.assertIn("${{ runner.os }}-${{ runner.arch }}-ubuntu24-playwright-${{ hashFiles('frontend/pnpm-lock.yaml') }}", job)
                self.assertNotIn("restore-keys:", job)
                self.assertIn("if: steps.playwright-cache.outputs.cache-hit != 'true'", job)

    def test_download_is_bounded_without_increasing_job_timeout(self):
        for name, job in self.jobs.items():
            with self.subTest(job=name):
                self.assertIn("    timeout-minutes: 10", job)
                install = job.split("      - name: Install Chromium headless shell\n", 1)[1].split("      - name:", 1)[0]
                self.assertIn("timeout-minutes: 2", install)


if __name__ == "__main__":
    unittest.main()
