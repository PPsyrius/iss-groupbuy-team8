"""Safety and data-minimisation checks for historical read-only diagnosis."""

import contextlib
import copy
import importlib.util
import io
import json
import subprocess
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("load_causes", ROOT / "infra/swe5001/scripts/inspect-load-causes.py")
CAUSES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAUSES)


class LoadCausesTests(unittest.TestCase):
    def tracking_policy(self):
        return {"PolicyName": "blocbuy-swe5001-commerce-requests-per-target", "PolicyType": "TargetTrackingScaling",
                "ResourceId": "service/blocbuy-swe5001/commerce-svc",
                "Alarms": [{"AlarmName": "TargetTracking-service/blocbuy-swe5001/commerce-svc-" + name}
                           for name in ("AlarmHigh", "AlarmLow")]}

    def test_selects_tracking_alarms_independently_of_cpu_policy_order(self):
        tracking = self.tracking_policy()
        cpu = {"PolicyName": "blocbuy-swe5001-commerce-cpu-headroom", "PolicyType": "StepScaling", "Alarms": []}
        for policies in ([tracking, cpu], [cpu, tracking], [tracking]):
            self.assertEqual(CAUSES.tracking_alarms(policies), tracking["Alarms"])

    def test_refuses_missing_duplicate_or_mismatched_tracking_policy_and_alarms(self):
        tracking = self.tracking_policy()
        invalid = [[], [tracking, tracking]]
        for key, value in (("PolicyName", "other"), ("PolicyType", "StepScaling"), ("ResourceId", "other"),
                           ("Alarms", []), ("Alarms", [tracking["Alarms"][0]] * 2),
                           ("Alarms", [{"AlarmName": "unreviewed"}] * 2)):
            changed = copy.deepcopy(tracking)
            changed[key] = value
            invalid.append([changed])
        for policies in invalid:
            with self.subTest(policies=policies), self.assertRaises(ValueError):
                CAUSES.tracking_alarms(policies)

    def test_refuses_mutations_before_calling_cli(self):
        with patch.dict(CAUSES.CAP, {"aws": lambda *args: self.fail("CLI must not be called")}):
            for command in [("ecs", "update-service"), ("ecs", "run-task"), ("application-autoscaling", "register-scalable-target"), ("ecs", "delete-service")]:
                with self.assertRaises(ValueError):
                    CAUSES.aws(*command)

    def test_classifies_without_exposing_messages(self):
        entries = [{"timestamp": 1, "eventId": "one", "logStreamName": "stream", "message": "private-value: Connection is not available"},
                   {"timestamp": 2, "eventId": "two", "logStreamName": "stream", "message": "private-value: remaining connection slots; too many clients"}]
        result = CAUSES.classify(entries)
        self.assertEqual(result[0]["categories"], ["pool_connection_wait"])
        self.assertEqual(result[1]["categories"], ["instance_connection_limit"])
        self.assertNotIn("private-value", json.dumps(result))
        self.assertTrue(all("message" not in item for item in result))

    def test_metrics_keep_declared_dimensions_and_unique_ids(self):
        queries = CAUSES.metric_queries("alb", "group")
        self.assertEqual(len(queries), 16)
        self.assertEqual(len({item["Id"] for item in queries}), len(queries))
        for item in queries:
            self.assertEqual(item["MetricStat"]["Period"], 60)
            metric = item["MetricStat"]["Metric"]
            if metric["MetricName"].startswith("HTTPCode_ELB"):
                self.assertEqual(metric["Dimensions"], [{"Name": "LoadBalancer", "Value": "alb"}])
            elif metric["MetricName"] == "HealthyHostCount":
                self.assertEqual(len(metric["Dimensions"]), 2)

    def test_main_archives_read_only_results_and_records_partial_read_failure(self):
        for failure in (None, "denied", "timeout"):
            with tempfile.TemporaryDirectory() as temporary:
                home = Path(temporary)
                original = home / "swe5001-v3-b84uhoj0"
                original.mkdir()
                prefix = f"arn:aws:elasticloadbalancing:{CAUSES.CAP['REGION']}:{CAUSES.CAP['ACCOUNT']}:"
                preflight = {"account": CAUSES.CAP["ACCOUNT"], "region": CAUSES.CAP["REGION"],
                    "routing": {"targetGroups": [{"arn": prefix + "targetgroup/blocbuy-swe5001-commerce-svc/group",
                                                 "loadBalancerArns": [prefix + "loadbalancer/app/blocbuy-swe5001/alb"]}]},
                    "policies": {"ScalingPolicies": [self.tracking_policy()]}}
                (original / "preflight.json").write_text(json.dumps(preflight))
                calls = []

                def aws(*args, failure=failure):
                    calls.append(args[:2])
                    if args[:2] == ("sts", "get-caller-identity"):
                        return {"Account": CAUSES.CAP["ACCOUNT"]}
                    if args[:2] == ("cloudwatch", "get-metric-data"):
                        if failure == "denied":
                            raise ValueError("Read permission denied")
                        if failure == "timeout":
                            raise subprocess.TimeoutExpired(["aws", "private-value"], 900, output="private-value", stderr="private-value")
                    if args[:2] == ("logs", "filter-log-events"):
                        return {"events": [{"timestamp": 1, "eventId": "one", "logStreamName": "stream", "message": "private-value: Connection is not available"}]}
                    return {"fixture": True}

                with patch.object(Path, "home", return_value=home), patch.dict(CAUSES.CAP, {"aws": aws}), contextlib.redirect_stdout(io.StringIO()):
                    CAUSES.main()
                self.assertTrue(set(calls).issubset(CAUSES.ALLOWED))
                archives = list(home.glob("swe5001-v3-causes-*.tar.gz"))
                self.assertEqual(len(archives), 1)
                with tarfile.open(archives[0]) as archive:
                    contexts = [item for item in archive.getmembers() if item.name.endswith("/context.json")]
                    context = json.load(archive.extractfile(contexts[0]))
                    self.assertEqual(len(context["problems"]), int(failure is not None))
                    if failure == "timeout":
                        self.assertEqual(context["problems"][0]["error"], "AWS CLI read timed out")
                    for member in archive.getmembers():
                        if member.isfile():
                            self.assertNotIn(b"private-value", archive.extractfile(member).read())
                self.assertEqual(json.loads((original / "preflight.json").read_text()), preflight)


if __name__ == "__main__":
    unittest.main()
