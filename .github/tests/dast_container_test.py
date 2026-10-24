"""Opt-in offline regressions for the workflow's actual Docker mount and ZAP command."""

import errno
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from uuid import uuid4

from dast_baseline_test import BETA_ADDON, BETA_SHA256, ROOT, bash_path, workflow_script
import dast_runtime_test as runtime
from dast_traffic_test import module


@unittest.skipUnless(os.environ.get("DAST_ZAP_DOCKER"), "Set DAST_ZAP_DOCKER=1 to request Docker runtime checks")
class DastContainerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.environ["DAST_ZAP_DOCKER"] != "1":
            raise RuntimeError("DAST_ZAP_DOCKER must be 1 when Docker runtime checks are requested")
        if not os.environ.get("DAST_ZAP_ADDON"):
            raise RuntimeError("DAST_ZAP_ADDON is required when DAST_ZAP_DOCKER is set")
        cls.addon = Path(os.environ["DAST_ZAP_ADDON"]).expanduser().resolve()
        if not cls.addon.is_file() or hashlib.sha256(cls.addon.read_bytes()).hexdigest() != BETA_SHA256:
            raise RuntimeError("DAST_ZAP_ADDON must be the existing checksum-verified beta file")
        cls.bash = bash_path()
        if not cls.bash or not shutil.which("docker"):
            raise RuntimeError("Bash and Docker are required for requested Docker runtime checks")
        workflow = (ROOT / ".github/workflows/dast-baseline.yml").read_text(encoding="utf-8")
        images = set(re.findall(r"ghcr\.io/zaproxy/zaproxy@sha256:[a-f0-9]{64}", workflow))
        if len(images) != 1:
            raise RuntimeError("The workflow must use one pinned scanner image")
        image = images.pop()
        inspected = subprocess.run(["docker", "image", "inspect", image], capture_output=True, text=True, timeout=15)
        if inspected.returncode:
            raise RuntimeError(f"Docker must be running with the pinned image already pulled: docker pull {image}\n"
                               + inspected.stderr)

    def run_case(self, case):
        with tempfile.TemporaryDirectory(prefix="dast-container-", dir=os.environ.get("DAST_TEST_TMP")) as directory:
            root = Path(directory)
            work = root / "zap-baseline"
            work.mkdir()
            addons = root / "zap-addons"
            addons.mkdir()
            shutil.copyfile(self.addon, addons / BETA_ADDON)
            script = workflow_script("Run ZAP baseline (passive only)")
            self.assertEqual(script.count("docker run --rm --init"), 1)
            script = script.replace("docker run --rm --init",
                'docker run --rm --init --pull=never --network none --hostname localhost '
                '--mount "type=bind,source=${DAST_TEST_GITHUB},target=/workspace/.github,readonly"')
            # Keep the workflow's ZAP arguments verbatim; the wrapper only starts a loopback fixture first.
            script, count = re.subn(r"(?m)^(\s*)(zap\.sh .+)$",
                r"\1python3 -B /workspace/.github/tests/dast_container_test.py --fixture " + case + r" \2", script)
            self.assertEqual(count, 1)
            if case == "missing-beta":
                script, count = re.subn(r'^.*--mount "[^"\n]*target=/zap/plugin/[^"\n]*" \\\n', "", script, flags=re.M)
                self.assertEqual(count, 1)
            run_id = "fixture-" + uuid4().hex
            try:
                completed = subprocess.run([self.bash, "--noprofile", "--norc", "-s"], input=script,
                    capture_output=True, text=True, timeout=180, env={**os.environ,
                        "RUNNER_TEMP": root.as_posix(), "GITHUB_RUN_ID": run_id, "GITHUB_RUN_ATTEMPT": "1",
                        "DAST_TEST_GITHUB": (ROOT / ".github").as_posix(),
                        "MSYS_NO_PATHCONV": "1", "MSYS2_ARG_CONV_EXCL": "*"})
            finally:
                # Also clean up if the host's subprocess deadline interrupts the workflow's EXIT trap.
                subprocess.run(["docker", "rm", "-f", "dast-" + run_id + "-1"], capture_output=True, timeout=15)
            evidence = work / "container-result.json"
            self.assertTrue(evidence.is_file(), completed.stdout + completed.stderr)
            result = json.loads(evidence.read_text(encoding="utf-8"))
            result["console_tail"] = (work / "zap-console.log").read_text(encoding="utf-8")[-5000:]
            self.assertEqual(completed.returncode, result["zap_exit"], completed.stdout + completed.stderr)
            self.assertEqual(result["coverage"]["covered"], 10, result)
            self.assertTrue(result["safe_requests"], result)
            self.assertFalse((work / "traffic.har").exists())
            return result

    def test_workflow_mount_and_command_complete_all_requests_and_passive_rules_offline(self):
        result = self.run_case("healthy")
        self.assertEqual(result["zap_exit"], 0, result)
        self.assertTrue(result["addon_read_only"], result)
        self.assertTrue(result["coverage"]["complete"], result)
        passive = result["coverage"]["passive_scan"]
        self.assertEqual((passive["executed_rules"], passive["enabled_rules"], passive["queued"]), (67, 67, 0))

    def test_omitting_the_beta_mount_still_fails_despite_ten_valid_responses(self):
        result = self.run_case("missing-beta")
        self.assertEqual(result["zap_exit"], 2, result)
        self.assertFalse(result["coverage"]["complete"], result)
        self.assertIn("Required passive rules are missing or disabled: 10063, 90004", result["coverage"]["errors"])


def run_fixture(case, command):
    """Run inside the network-disabled pinned container, not against the deployed environment."""
    fixture = runtime.DastRuntimeTest("test_native_plan_records_all_ten_gets_without_following_oauth_or_submitting_forms")
    fixture.setUp()
    try:
        output = Path("/zap/wrk")
        module.preflight(output, "http://127.0.0.1:" + str(fixture.server.server_port))
        read_only = False
        if case == "healthy":
            addon = Path("/zap/plugin") / BETA_ADDON
            if hashlib.sha256(addon.read_bytes()).hexdigest() != BETA_SHA256:
                raise RuntimeError("The workflow did not mount the pinned add-on")
            try:
                with addon.open("ab"):
                    pass
            except OSError as error:
                read_only = error.errno == errno.EROFS
        with (output / "zap-console.log").open("w", encoding="utf-8") as log:
            completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=150)
        coverage = module.verify(output, "success" if completed.returncode == 0 else "failure")
        (output / "coverage.md").write_text(module.markdown(coverage), encoding="utf-8")
        result = {"zap_exit": completed.returncode, "coverage": coverage, "addon_read_only": read_only,
                  "safe_requests": all(method == "GET" and not path.startswith("/api/v1/auth/")
                                       for method, path in fixture.received)}
        module.write_json(output / "container-result.json", result)
        print(json.dumps({"case": case, "zap_exit": completed.returncode, "complete": coverage["complete"],
                          "covered": coverage["covered"], "passive_scan": coverage.get("passive_scan")}))
        return completed.returncode
    finally:
        fixture.doCleanups()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--fixture":
        sys.exit(run_fixture(sys.argv[2], sys.argv[3:]))
    unittest.main()
