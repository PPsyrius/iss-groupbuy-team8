"""No-network regression tests for the persistent, read-only load observer."""

import contextlib
from datetime import datetime, timedelta, timezone
import importlib.util
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("load_observer", ROOT / "infra/swe5001/scripts/observe-load.py")
OBS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(OBS)


class ObserverTests(unittest.TestCase):
    def test_requires_persistent_confirmation_and_rejects_cloudshell(self):
        with patch.dict(os.environ, {}, clear=True):
            OBS.guard_host(True)
            with self.assertRaises(ValueError):
                OBS.guard_host(False)
        for environment in ({"AWS_EXECUTION_ENV": "CloudShell"}, {"USER": "cloudshell-user"}, {"CLOUDSHELL_ENVIRONMENT_ID": "id"}):
            with patch.dict(os.environ, environment, clear=True), self.assertRaises(ValueError):
                OBS.guard_host(True)

    def test_requires_observed_credential_expiry_with_buffer(self):
        for minutes in (10, 54, 60):
            value = {"Expiration": (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat(),
                     "AccessKeyId": "private-key", "SecretAccessKey": "private-secret", "SessionToken": "private-token"}
            with patch.object(OBS, "read_aws", return_value=value):
                if minutes < 55:
                    with self.assertRaises(ValueError):
                        OBS.credential_expiry()
                else:
                    self.assertEqual(OBS.credential_expiry(), value["Expiration"])
        with patch.object(OBS, "read_aws", return_value={}), self.assertRaises(ValueError):
            OBS.credential_expiry()

    def test_refuses_mutation_without_calling_cli(self):
        with patch.dict(OBS.LOAD, {"aws": lambda *args: self.fail("CLI must not be called")}):
            for args in (("ecs", "update-service"), ("ecs", "run-task"), ("application-autoscaling", "register-scalable-target")):
                with self.assertRaises(ValueError):
                    OBS.read_aws(*args)

    def test_main_rejects_cloudshell_before_aws_or_directory_creation(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "new"
            with patch("sys.argv", ["observe-load.py", str(output), "--persistent-host"]), \
                    patch.dict(os.environ, {"AWS_EXECUTION_ENV": "CloudShell"}, clear=True), patch.object(OBS, "read_aws") as aws:
                with self.assertRaises(ValueError):
                    OBS.main()
                aws.assert_not_called()
            self.assertFalse(output.exists())

    def test_main_persists_expiration_but_never_exported_credentials(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "new"
            expiry = (datetime.now(timezone.utc) + timedelta(minutes=60)).isoformat()
            values = [{"Account": OBS.LOAD["CAP"]["ACCOUNT"]}, {"Expiration": expiry,
                      "AccessKeyId": "private-key", "SecretAccessKey": "private-secret", "SessionToken": "private-token"}]
            with patch("sys.argv", ["observe-load.py", str(output), "--persistent-host"]), \
                    patch.dict(os.environ, {}, clear=True), patch.object(OBS, "read_aws", side_effect=values), \
                    patch.object(OBS, "capture", return_value={"collectionComplete": True}), patch.object(OBS.signal, "signal"):
                OBS.main()
            saved = (output / "credential-window.json").read_text()
            self.assertEqual(json.loads(saved)["observedExpiration"], expiry)
            self.assertNotIn("private-", saved)

    def run_capture(self, fail_read=False, fail_finish=False, initial_failure=False, slow_read=False):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        directory = Path(temporary.name) / "result.with-dot"
        directory.mkdir()
        (directory / "preflight.json").write_text("{}")
        clock = [0.0]
        calls = []
        base = datetime.now(timezone.utc)
        class ClockDate:
            @staticmethod
            def now(_timezone):
                return base + timedelta(seconds=clock[0])
        def sleep(seconds):
            clock[0] += seconds
        def aws(*args):
            calls.append(args[:2])
            clock[0] += 50 if slow_read and len(calls) == 2 else 1
            if initial_failure or (fail_read and len(calls) == 2):
                raise ValueError("AWS read failed")
            return {"failures": [], "services": [{"name": "commerce-svc", "running": 1, "desired": 1, "pending": 0}]}
        def finish(*args, **kwargs):
            self.assertIn("read", kwargs)
            if fail_finish:
                raise ValueError("Metric permission denied")
        with patch.object(OBS.time, "monotonic", side_effect=lambda: clock[0]), patch.object(OBS.time, "sleep", side_effect=sleep), \
                patch.object(OBS, "datetime", ClockDate), \
                patch.object(OBS, "read_aws", side_effect=aws), patch.dict(OBS.LOAD, {"finish": finish}), \
                contextlib.redirect_stdout(io.StringIO()):
            result = OBS.capture(directory, seconds=31)
        with tarfile.open(Path(str(directory) + ".tar.gz")) as archive:
            self.assertTrue(any(item.name.endswith("/observer-result.json") for item in archive.getmembers()))
        self.assertTrue(set(calls).issubset(OBS.READS))
        self.assertEqual(json.loads((directory / "observer-heartbeat.json").read_text())["state"], "finished")
        self.assertFalse(result["restorationPerformed"])
        return result, directory

    def test_foreground_collection_records_flushes_and_archives_without_runtime_mutations(self):
        result, directory = self.run_capture()
        self.assertTrue(result["timerCompleted"])
        self.assertTrue(result["collectionComplete"])
        rows = (directory / "observations.jsonl").read_text().strip().splitlines()
        self.assertEqual(len(rows), result["samples"])

    def test_transient_read_failure_is_retained_not_replaced_by_fake_observation(self):
        result, directory = self.run_capture(fail_read=True)
        self.assertTrue(result["timerCompleted"])
        self.assertFalse(result["collectionComplete"])
        self.assertEqual(len(result["errors"]), 1)
        self.assertEqual(len((directory / "observations.jsonl").read_text().strip().splitlines()), result["samples"])

    def test_finish_failure_still_preserves_archive_and_marks_incomplete(self):
        result, _ = self.run_capture(fail_finish=True)
        self.assertFalse(result["collectionComplete"])
        self.assertEqual(result["errors"][-1]["error"], "ValueError")

    def test_gap_above_45_seconds_cannot_be_labelled_complete(self):
        result, _ = self.run_capture(slow_read=True)
        self.assertTrue(result["timerCompleted"])
        self.assertGreater(result["maximumSampleGapSeconds"], 45)
        self.assertFalse(result["collectionComplete"])

    def test_first_failed_read_never_publishes_recording_heartbeat(self):
        result, directory = self.run_capture(initial_failure=True)
        self.assertEqual(result["samples"], 0)
        self.assertFalse(result["collectionComplete"])
        self.assertFalse(result["timerCompleted"])


if __name__ == "__main__":
    unittest.main()
