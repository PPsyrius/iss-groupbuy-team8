"""Offline route-redaction and observation contracts for the operator capture."""

import copy
import importlib.util
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("load_capture", ROOT / "infra/swe5001/scripts/load-capture.py")
LOAD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LOAD)


class LoadCaptureTests(unittest.TestCase):
    def test_collection_classifies_connection_failures_without_saving_bodies(self):
        events = {"events": [{"timestamp": 1, "eventId": "one", "logStreamName": "stream", "message": "private-value Connection is not available"},
                             {"timestamp": 2, "eventId": "two", "logStreamName": "stream", "message": "private-value too many clients"}]}
        result = LOAD.connection_events(events)
        self.assertEqual(result["matches"][0]["categories"], ["pool_connection_wait"])
        self.assertEqual(result["matches"][1]["categories"], ["instance_connection_limit"])
        self.assertNotIn("private-value", json.dumps(result))
        self.assertNotIn("message", result["matches"][0])

    def test_legacy_detached_capture_and_prepare_without_observer_refuse_before_aws(self):
        with patch.object(LOAD, "aws") as aws:
            with self.assertRaises(ValueError):
                LOAD.prepare()
            with self.assertRaises(ValueError):
                LOAD.observe(Path("unused"))
            aws.assert_not_called()

    def test_observer_identity_freshness_and_remaining_coverage_are_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            now = datetime.now(timezone.utc)
            context = {"session": "one", "account": LOAD.CAP["ACCOUNT"], "region": LOAD.CAP["REGION"],
                       "cluster": LOAD.CLUSTER, "persistentHostConfirmed": True, "startedAt": now.isoformat(), "seconds": 2400}
            heartbeat = {"session": "one", "state": "recording", "samples": 1, "at": now.isoformat()}
            with self.assertRaises(ValueError):
                LOAD.require_observer(directory)
            def save(c, h):
                (directory / "observer-context.json").write_text(json.dumps(c), encoding="utf-8")
                (directory / "observer-heartbeat.json").write_text(json.dumps(h), encoding="utf-8")
            save(context, heartbeat)
            self.assertEqual(LOAD.require_observer(directory), directory)
            for key, value in [("state", "finished"), ("session", "other"), ("samples", 0),
                               ("at", (now - timedelta(seconds=31)).isoformat())]:
                save(context, {**heartbeat, key: value})
                with self.assertRaises(ValueError):
                    LOAD.require_observer(directory)
            save({**context, "startedAt": (now - timedelta(minutes=5)).isoformat()}, heartbeat)
            with self.assertRaises(ValueError):
                LOAD.require_observer(directory)
            save({**context, "startedAt": (now - timedelta(minutes=5)).isoformat(), "seconds": 3600}, heartbeat)
            self.assertEqual(LOAD.require_observer(directory), directory)

    def test_lost_observer_during_prepare_restores_floor_and_never_publishes_preflight(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            services = {name: {"taskDefinition": name} for name in LOAD.SERVICES}
            target = {"ScalableTargets": [{"MinCapacity": 2, "MaxCapacity": 6, "SuspendedState": {
                "DynamicScalingInSuspended": False, "DynamicScalingOutSuspended": False, "ScheduledScalingSuspended": False}}]}
            def aws(*args):
                if args[:2] == ("sts", "get-caller-identity"):
                    return {"Account": LOAD.CAP["ACCOUNT"]}
                if args[:2] == ("ecs", "describe-task-definition"):
                    name = args[-1]
                    return {"taskDefinition": {"containerDefinitions": [{"name": name,
                        "image": "reviewed@" + LOAD.CAP["DIGESTS"][name], "environment": [{"name": LOAD.CAP["KEY"], "value": "5"}]}]}}
                return {}
            with patch.object(LOAD, "require_observer", side_effect=[directory, ValueError("Observer lost")]), \
                    patch.dict(LOAD.os.environ, {"TEST_SHA": "a" * 40}), \
                    patch.dict(LOAD.CAP, {"services": lambda: services}), patch.object(LOAD, "aws", side_effect=aws), \
                    patch.object(LOAD, "scaling", return_value=target), patch.object(LOAD, "set_floor") as floor, \
                    patch.object(LOAD, "restore_floor", return_value={"verified": True}) as restore, \
                    patch.object(LOAD, "preflight") as preflight:
                with self.assertRaises(ValueError):
                    LOAD.prepare(directory)
            floor.assert_called_once_with(1)
            restore.assert_called_once()
            preflight.assert_not_called()
            self.assertFalse((directory / "preflight.json").exists())
            self.assertTrue((directory / "prepare-failure-restoration.json").exists())

    def test_restore_refuses_changed_maximum_without_mutation(self):
        with patch.object(LOAD, "aws", return_value={"Account": LOAD.CAP["ACCOUNT"]}), \
                patch.object(LOAD, "scaling", return_value={"ScalableTargets": [{"MaxCapacity": 4}]}), \
                patch.object(LOAD, "set_floor") as floor:
            with self.assertRaises(ValueError):
                LOAD.restore_floor()
            floor.assert_not_called()

    def test_header_comparison_never_saves_secret(self):
        edge = {"origins": [{"id": "alb-runtime", "headers": [{"HeaderName": "X-Origin-Secret", "HeaderValue": "private-value"}]}]}
        rules = [{"priority": "30", "isDefault": False, "actions": [{"type": "forward"}], "transforms": None,
                  "conditions": [{"field": "path-pattern", "pathPatterns": ["/api/*"], "pathRegex": None},
                                 {"field": "http-header", "header": {"HttpHeaderName": "X-Origin-Secret", "Values": ["private-value"]}}]}]
        for matches in (True, False):
            current = copy.deepcopy(edge)
            raw = copy.deepcopy(rules)
            if not matches:
                raw[0]["conditions"][1]["header"]["Values"] = ["other-private-value"]
            result = LOAD.sanitize_rules(raw, current)
            self.assertEqual(result[0]["originHeaderMatches"], matches)
            self.assertEqual(result[0]["pathPatterns"], ["/api/*"])
            self.assertFalse(result[0]["hasTransforms"])
            self.assertFalse(result[0]["hasPathRegex"])
            self.assertNotIn("private-value", json.dumps({"edge": current, "rules": result}))

    def test_unknown_transforms_regex_and_extra_origins_remain_rejectable(self):
        edge = {"origins": [{"id": "alb-runtime", "headers": []}]}
        rules = [{"priority": "30", "isDefault": False, "actions": [], "transforms": ["url-rewrite"],
                  "conditions": [{"field": "path-pattern", "pathPatterns": [], "pathRegex": [".*"]}]}]
        result = LOAD.sanitize_rules(rules, edge)
        self.assertTrue(result[0]["hasTransforms"])
        self.assertTrue(result[0]["hasPathRegex"])
        self.assertFalse(result[0]["originHeaderMatches"])
        with self.assertRaises(ValueError):
            LOAD.sanitize_rules([], {"origins": []})

    def test_observation_matches_existing_analyzer_shape_and_fails_closed(self):
        response = {"services": [{"name": "commerce-svc", "desired": 1, "running": 1}], "failures": []}
        with patch.object(LOAD, "aws", return_value=response):
            result = LOAD.observation()
            self.assertEqual(result["services"], response["services"])
            self.assertTrue(result["observedAt"].endswith("Z"))
        for invalid in ({"services": [], "failures": []}, {"services": [{}], "failures": [{"reason": "MISSING"}]}):
            with patch.object(LOAD, "aws", return_value=invalid), self.assertRaises(ValueError):
                LOAD.observation()

    def test_floor_update_preserves_maximum_and_does_not_set_suspensions(self):
        with patch.object(LOAD, "aws") as aws:
            LOAD.set_floor(1)
            args = aws.call_args.args
            self.assertEqual(args[-4:], ("--min-capacity", "1", "--max-capacity", "6"))
            self.assertNotIn("--suspended-state", args)
            self.assertNotIn("delete", " ".join(args))


if __name__ == "__main__":
    unittest.main()
