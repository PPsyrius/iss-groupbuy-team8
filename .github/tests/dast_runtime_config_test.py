"""Runtime checks are optional, but an explicitly requested run must not silently skip."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DastRuntimeConfigurationTest(unittest.TestCase):
    def run_suite(self, pattern="dast_runtime_test.py", **settings):
        environment = {key: value for key, value in os.environ.items()
                       if key not in ("DAST_ZAP_HOME", "DAST_ZAP_ADDON", "DAST_ZAP_DOCKER")}
        return subprocess.run([sys.executable, "-B", "-m", "unittest", "discover", "-s", ".github/tests",
            "-p", pattern, "-v"], cwd=ROOT, env={**environment, **settings},
            capture_output=True, text=True, timeout=10)

    def test_unrequested_native_checks_stay_skipped(self):
        completed = self.run_suite()
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("skipped=4", completed.stderr)

    def test_requested_native_checks_fail_when_the_addon_setting_is_missing(self):
        completed = self.run_suite(DAST_ZAP_HOME="requested-local-bundle")
        self.assertNotEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("DAST_ZAP_ADDON is required when DAST_ZAP_HOME is set", completed.stderr)
        self.assertNotIn("skipped=4", completed.stderr)

    def test_requested_native_checks_fail_when_the_addon_file_is_missing(self):
        with tempfile.TemporaryDirectory(dir=os.environ.get("DAST_TEST_TMP")) as directory:
            completed = self.run_suite(DAST_ZAP_HOME="requested-local-bundle",
                                       DAST_ZAP_ADDON=str(Path(directory) / "missing-beta-addon.zap"))
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("DAST_ZAP_ADDON must name an existing file", completed.stderr)

    def test_requested_native_checks_fail_before_starting_zap_when_the_checksum_is_wrong(self):
        with tempfile.TemporaryDirectory(dir=os.environ.get("DAST_TEST_TMP")) as directory:
            addon = Path(directory) / "wrong-addon.zap"
            addon.write_bytes(b"not the pinned beta add-on")
            completed = self.run_suite(DAST_ZAP_HOME="requested-local-bundle", DAST_ZAP_ADDON=str(addon))
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("DAST_ZAP_ADDON does not match the pinned SHA-256", completed.stderr)

    def test_unrequested_docker_checks_stay_skipped(self):
        completed = self.run_suite(pattern="dast_container_test.py")
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("skipped=2", completed.stderr)

    def test_requested_docker_checks_fail_when_the_addon_setting_is_missing(self):
        completed = self.run_suite(pattern="dast_container_test.py", DAST_ZAP_DOCKER="1")
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("DAST_ZAP_ADDON is required when DAST_ZAP_DOCKER is set", completed.stderr)

    def test_invalid_docker_opt_in_fails_instead_of_silently_skipping(self):
        completed = self.run_suite(pattern="dast_container_test.py", DAST_ZAP_DOCKER="yes")
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("DAST_ZAP_DOCKER must be 1", completed.stderr)


if __name__ == "__main__":
    unittest.main()
