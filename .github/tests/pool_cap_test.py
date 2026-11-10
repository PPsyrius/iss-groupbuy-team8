"""Offline safety checks for the narrowly approved SWE5001 pool rollout."""

import copy
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("cap_pools", ROOT / "infra/swe5001/scripts/cap-pools.py")
CAP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CAP)


def definition(name="identity-svc"):
    return {
        "family": f"{CAP.CLUSTER}-{name}", "revision": 17,
        "taskDefinitionArn": "old-arn", "status": "ACTIVE", "registeredAt": "date",
        "cpu": "512", "memory": "1024", "taskRoleArn": "unchanged-role",
        "containerDefinitions": [{
            "name": name,
            "image": f"{CAP.ACCOUNT}.dkr.ecr.{CAP.REGION}.amazonaws.com/swe5001/{name}@{CAP.DIGESTS[name]}",
            "environment": [{"name": "JAVA_TOOL_OPTIONS", "value": "-XX:MaxRAMPercentage=75"}],
            "secrets": [{"name": "DB_PASSWORD", "valueFrom": "unchanged-ssm-reference"}],
        }],
    }


class PoolCapTests(unittest.TestCase):
    def test_cli_failure_exposes_only_safe_error_code(self):
        response = SimpleNamespace(returncode=1, stdout="", stderr="An error occurred (AccessDeniedException) when calling API: private-sensitive-value")
        with patch.object(CAP.subprocess, "run", return_value=response):
            with self.assertRaises(ValueError) as error:
                CAP.aws("logs", "filter-log-events")
        self.assertIn("AccessDeniedException", str(error.exception))
        self.assertNotIn("private-sensitive-value", str(error.exception))

    def test_wait_requires_rollout_completed_after_counts_stabilize(self):
        service = {"taskDefinition": "new", "desiredCount": 1, "runningCount": 1, "pendingCount": 0,
                   "deployments": [{"rolloutState": "IN_PROGRESS"}]}
        done = copy.deepcopy(service)
        done["deployments"][0]["rolloutState"] = "COMPLETED"
        with patch.object(CAP, "aws", side_effect=[{"services": [service]}, {"services": [done]}]), patch.object(CAP.time, "sleep") as sleep:
            CAP.wait_completed("identity-svc", "new")
            sleep.assert_called_once_with(10)

    def test_wait_refuses_rollback_failed_deployment_and_timeout(self):
        original = {"taskDefinition": "new", "desiredCount": 1, "runningCount": 1, "pendingCount": 0,
                    "deployments": [{"rolloutState": "IN_PROGRESS"}]}
        for field, value in [("taskDefinition", "old"), ("desiredCount", 2),
                             ("deployments", [{"rolloutState": "FAILED"}])]:
            service = {**original, field: value}
            with patch.object(CAP, "aws", return_value={"services": [service]}), self.assertRaises(ValueError):
                CAP.wait_completed("identity-svc", "new")
        with patch.object(CAP.time, "monotonic", side_effect=[0, 1201]), self.assertRaises(ValueError):
            CAP.wait_completed("identity-svc", "new")

    def test_resume_retains_identity_revision_and_updates_only_remaining_services(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            before = {item["serviceName"]: {**item, "taskDefinition": "old-" + item["serviceName"], "loadBalancers": []}
                      for item in self.stable_services()}
            definitions = {name: {"taskDefinition": definition(name)} for name in CAP.COUNTS}
            (directory / "before-private.json").write_text(json.dumps({"services": before, "definitions": definitions}))
            identity_arn = f"arn:aws:ecs:{CAP.REGION}:{CAP.ACCOUNT}:task-definition/{CAP.CLUSTER}-identity-svc:18"
            (directory / "identity-svc-registered.json").write_text(json.dumps({"old": before["identity-svc"]["taskDefinition"], "new": identity_arn}))
            current = copy.deepcopy(before)
            current["identity-svc"]["taskDefinition"] = identity_arn
            registrations, updates = [], []

            def aws(*args):
                operation = args[:2]
                if operation == ("sts", "get-caller-identity"):
                    return {"Account": CAP.ACCOUNT}
                if operation == ("ecs", "describe-task-definition"):
                    return {"taskDefinition": CAP.replacement(definition(), "identity-svc")}
                if operation == ("ecs", "describe-services"):
                    return {"failures": [], "services": [copy.deepcopy(current[args[-1]])]}
                if operation == ("ecs", "register-task-definition"):
                    payload = json.loads(Path(args[-1].removeprefix("file://")).read_text())
                    name = payload["containerDefinitions"][0]["name"]
                    registrations.append(name)
                    return {"taskDefinition": {"taskDefinitionArn": f"arn:aws:ecs:{CAP.REGION}:{CAP.ACCOUNT}:task-definition/{CAP.CLUSTER}-{name}:32"}}
                if operation == ("ecs", "update-service"):
                    name = args[args.index("--service") + 1]
                    updates.append(name)
                    current[name]["taskDefinition"] = args[-1]
                    return {}
                if operation == ("ecs", "list-tasks"):
                    name = args[-1]
                    return {"taskArns": [f"{name}/{index}" for index in range(CAP.COUNTS[name])]}
                if operation == ("ecs", "describe-tasks"):
                    name = args[-1].split("/")[0]
                    return {"failures": [], "tasks": [{"taskDefinitionArn": current[name]["taskDefinition"],
                        "containers": [{"name": name, "imageDigest": CAP.DIGESTS[name]}]} for _ in range(CAP.COUNTS[name])]}
                self.fail(f"Unexpected operation {operation}")

            with patch.object(CAP, "aws", side_effect=aws), patch.object(CAP, "services", side_effect=lambda: copy.deepcopy(current)), \
                    patch.object(CAP, "wait_completed"), patch("sys.argv", ["cap-pools.py", "--resume", str(directory)]), contextlib.redirect_stdout(io.StringIO()):
                CAP.main()
            self.assertEqual(registrations, ["commerce-svc", "notification-svc"])
            self.assertEqual(updates, registrations)
            self.assertEqual(current["identity-svc"]["taskDefinition"], identity_arn)
            self.assertTrue(all((directory / f"{name}-verified.json").exists() for name in CAP.COUNTS))

    def test_resume_reuses_matching_payload_and_preserves_conflicting_payload(self):
        for matching in (True, False):
            with self.subTest(matching=matching), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                before = {item["serviceName"]: {**item, "taskDefinition": "old-" + item["serviceName"]}
                          for item in self.stable_services()}
                definitions = {name: {"taskDefinition": definition(name)} for name in CAP.COUNTS}
                (directory / "before-private.json").write_text(json.dumps({"services": before, "definitions": definitions}))
                payload = {**CAP.replacement(definition(), "identity-svc"), "tags": []} if matching else {"unexpected": True}
                saved = directory / "identity-svc-register-private.json"
                original = json.dumps(payload, indent=3)
                saved.write_text(original)
                def aws(*args):
                    if args[:2] == ("sts", "get-caller-identity"):
                        return {"Account": CAP.ACCOUNT}
                    self.assertEqual(args[:2], ("ecs", "register-task-definition"))
                    raise ValueError("simulated registration failure")
                expected = "simulated registration failure" if matching else "Saved registration payload differs"
                with patch("sys.argv", ["cap-pools.py", "--resume", str(directory)]), \
                        patch.object(CAP, "services", return_value=before), patch.object(CAP, "aws", side_effect=aws) as call, \
                        contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, expected):
                    CAP.main()
                self.assertEqual(saved.read_text(), original)
                self.assertEqual(call.call_count, 2 if matching else 1)

    def test_resume_stops_actionably_before_retrying_unapplied_or_rolled_back_revision(self):
        for current_revision in ("old-identity-svc", "unreviewed-revision"):
            with self.subTest(revision=current_revision), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                before = {item["serviceName"]: {**item, "taskDefinition": "old-" + item["serviceName"]}
                          for item in self.stable_services()}
                definitions = {name: {"taskDefinition": definition(name)} for name in CAP.COUNTS}
                (directory / "before-private.json").write_text(json.dumps({"services": before, "definitions": definitions}))
                revision = f"arn:aws:ecs:{CAP.REGION}:{CAP.ACCOUNT}:task-definition/{CAP.CLUSTER}-identity-svc:18"
                receipt = directory / "identity-svc-registered.json"
                original = json.dumps({"old": "old-identity-svc", "new": revision})
                receipt.write_text(original)
                def aws(*args):
                    if args[:2] == ("sts", "get-caller-identity"):
                        return {"Account": CAP.ACCOUNT}
                    if args[:2] == ("ecs", "describe-task-definition"):
                        return {"taskDefinition": CAP.replacement(definition(), "identity-svc")}
                    self.assertEqual(args[:2], ("ecs", "describe-services"))
                    return {"failures": [], "services": [{"taskDefinition": current_revision}]}
                with patch("sys.argv", ["cap-pools.py", "--resume", str(directory)]), patch.object(CAP, "aws", side_effect=aws), \
                        patch.object(CAP, "wait_completed") as wait, contextlib.redirect_stdout(io.StringIO()), \
                        self.assertRaisesRegex(ValueError, "inspect update-service outcome and rollback/service events"):
                    CAP.main()
                wait.assert_not_called()
                self.assertEqual(receipt.read_text(), original)

    def test_only_pool_changes_for_each_service(self):
        for name in CAP.COUNTS:
            before = definition(name)
            original = copy.deepcopy(before)
            result = CAP.replacement(before, name)
            result["containerDefinitions"][0]["environment"].remove({"name": CAP.KEY, "value": "5"})
            self.assertEqual(result, {key: value for key, value in before.items() if key not in CAP.READ_ONLY})
            self.assertEqual(before, original)

    def test_rejects_family_image_container_overrides_and_duplicates(self):
        variants = []
        item = definition(); item["family"] = "other"; variants.append(item)
        item = definition(); item["containerDefinitions"][0]["image"] = "mutable:latest"; variants.append(item)
        item = definition(); item["containerDefinitions"].append({}); variants.append(item)
        for key, value in [(CAP.KEY, "5"), ("SPRING_APPLICATION_JSON", "{}"),
                           ("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "10"),
                           ("JAVA_TOOL_OPTIONS", "-Dother=true")]:
            item = definition(); item["containerDefinitions"][0]["environment"].append({"name": key, "value": value}); variants.append(item)
        item = definition(); item["containerDefinitions"][0]["secrets"].append({"name": CAP.KEY, "valueFrom": "secret"}); variants.append(item)
        for item in variants:
            with self.assertRaises(ValueError):
                CAP.replacement(item, "identity-svc")

    def stable_services(self):
        return [{"serviceName": name, "status": "ACTIVE", "desiredCount": count,
                  "runningCount": count, "pendingCount": 0,
                  "deployments": [{"rolloutState": "COMPLETED"}],
                  "deploymentConfiguration": {"minimumHealthyPercent": 100, "maximumPercent": 200,
                                              "deploymentCircuitBreaker": {"enable": True, "rollback": True}}}
                 for name, count in CAP.COUNTS.items()]

    def test_accepts_observed_aws_circuit_breaker_metadata(self):
        items = self.stable_services()
        for item in items:
            config = item["deploymentConfiguration"]
            config.update({"strategy": "ROLLING", "bakeTimeInMinutes": 0})
            config["deploymentCircuitBreaker"].update({
                "resetOnHealthyTask": True,
                "thresholdConfiguration": {"type": "BOUNDED_PERCENT", "value": 50},
            })
        before = copy.deepcopy(items)
        with patch.object(CAP, "aws", return_value={"services": items}):
            self.assertEqual(set(CAP.services()), set(CAP.COUNTS))
        self.assertEqual(items, before)
        for key in ("enable", "rollback"):
            changed = copy.deepcopy(items)
            changed[0]["deploymentConfiguration"]["deploymentCircuitBreaker"][key] = False
            with patch.object(CAP, "aws", return_value={"services": changed}), self.assertRaises(ValueError):
                CAP.services()

    def test_refuses_unstable_counts_rollout_and_policy(self):
        items = self.stable_services()
        with patch.object(CAP, "aws", return_value={"services": items}):
            self.assertEqual(set(CAP.services()), set(CAP.COUNTS))
        for field, value in [("desiredCount", 6), ("runningCount", 0), ("pendingCount", 1),
                             ("deployments", [{"rolloutState": "IN_PROGRESS"}]),
                             ("deploymentConfiguration", {"minimumHealthyPercent": 0, "maximumPercent": 200}),
                             ("deploymentConfiguration", {"minimumHealthyPercent": 100, "maximumPercent": 150}),
                             ("deploymentConfiguration", {"minimumHealthyPercent": 100, "maximumPercent": 200,
                                                          "strategy": "BLUE_GREEN", "deploymentCircuitBreaker": {"enable": True, "rollback": True}}),
                             ("deploymentConfiguration", {"minimumHealthyPercent": 100, "maximumPercent": 200})]:
            changed = copy.deepcopy(items); changed[0][field] = value
            with patch.object(CAP, "aws", return_value={"services": changed}), self.assertRaises(ValueError):
                CAP.services()


if __name__ == "__main__":
    unittest.main()
