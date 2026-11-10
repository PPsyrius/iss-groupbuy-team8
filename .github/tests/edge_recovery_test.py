"""Offline scope guards for the operator's one-off #757 edge recovery."""

import copy
from datetime import datetime, timedelta, timezone
import importlib.util
import io
import json
import stat
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[2] / "infra/swe5001/scripts/recover-edge.py"
SPEC = importlib.util.spec_from_file_location("edge_recovery", SCRIPT)
recovery = importlib.util.module_from_spec(SPEC)
with patch.object(sys, "dont_write_bytecode", True):
    SPEC.loader.exec_module(recovery)


class EdgeRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.policy = {"address": recovery.POLICY, "mode": "managed", "change": {
            "actions": ["update"],
            "before": {"id": recovery.POLICY_ID, "name": "blocbuy-swe5001-security-headers",
                       "security_headers_config": [], "custom_headers_config": []},
            "after": {"id": recovery.POLICY_ID, "name": "blocbuy-swe5001-security-headers",
                      "security_headers_config": [{"csp": "reviewed-source"}], "custom_headers_config": []},
        }}
        before = {"id": recovery.DISTRIBUTION_ID, "enabled": True, "origin": [
            {"origin_id": "s3-spa", "domain_name": "bucket.s3.amazonaws.com"},
            {"origin_id": "alb-runtime", "domain_name": "old-alb.elb.amazonaws.com",
             "custom_header": [{"name": "X-Origin-Secret", "value": "private-test-placeholder"}]},
        ]}
        after = copy.deepcopy(before)
        after["origin"][1]["domain_name"] = recovery.IDLE_ORIGIN
        self.distribution = {"address": recovery.DISTRIBUTION, "mode": "managed", "change": {
            "actions": ["update"], "before": before, "after": after,
        }}
        self.plan = {"complete": True, "resource_changes": [self.policy, self.distribution]}

    def test_expected_policy_and_idle_origin_updates(self):
        self.assertEqual(recovery.approved_changes(self.plan), [recovery.POLICY, recovery.DISTRIBUTION])

    def test_scope_summary_includes_rejected_address_without_values(self):
        self.policy["address"] = "aws_s3_bucket_policy.spa"
        summary = recovery.describe_plan_scope(self.plan)
        self.assertIn("update: aws_s3_bucket_policy.spa", summary)
        self.assertNotIn("private-test-placeholder", str(summary))
        self.assertNotIn("reviewed-source", str(summary))
        records = recovery.plan_scope_records(self.plan)
        self.assertEqual(records[0]["address"], "aws_s3_bucket_policy.spa")
        self.assertIn("security_headers_config", records[0]["changed_fields"])
        self.assertNotIn("private-test-placeholder", json.dumps(records))

    def test_default_workspace_is_stable_private_home_storage(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(recovery.Path, "home", return_value=Path(directory)), \
                patch.object(recovery.Path, "lstat", return_value=SimpleNamespace(st_mode=stat.S_IFDIR | 0o700, st_uid=42)), \
                patch.object(recovery.os, "getuid", return_value=42, create=True):
            first = recovery.persistent_work()
            second = recovery.persistent_work()
            self.assertEqual(first, second)
            self.assertEqual(first.parent, Path(directory) / ".swe5001-edge-recovery")

    def test_policy_only_update(self):
        self.assertEqual(recovery.approved_changes({"resource_changes": [self.policy]}), [recovery.POLICY])

    def add_bucket_dependency(self):
        policy = json.dumps(recovery.expected_bucket_policy())
        bucket = {"address": recovery.BUCKET_POLICY, "mode": "managed", "change": {
            "actions": ["update"], "before": {"id": recovery.BUCKET, "bucket": recovery.BUCKET, "policy": policy},
            "after": {"id": recovery.BUCKET, "bucket": recovery.BUCKET, "policy": None},
            "after_unknown": {"policy": True},
        }}
        data = {"address": "data.aws_iam_policy_document.spa_read", "mode": "data",
                "action_reason": "read_because_dependency_pending", "change": {"actions": ["read"]}}
        self.plan["resource_changes"].extend([bucket, data])
        return bucket, data

    def test_dependent_bucket_policy_is_verified_not_approved_for_writing(self):
        self.add_bucket_dependency()
        self.assertTrue(recovery.bucket_dependency_only(self.plan))
        with self.assertRaisesRegex(ValueError, "unreviewed resource"):
            recovery.approved_changes(self.plan)

    def test_unknown_policy_requires_computed_or_dependency_read(self):
        bucket, data = self.add_bucket_dependency()
        data["action_reason"] = "read_because_config_unknown"
        self.assertTrue(recovery.bucket_dependency_only(self.plan))
        data["action_reason"] = "unknown-reason"
        with self.assertRaisesRegex(ValueError, "pending distribution dependency"):
            recovery.bucket_dependency_only(self.plan)

    def test_bucket_policy_with_additional_protection_is_not_silently_replaced(self):
        bucket, data = self.add_bucket_dependency()
        policy = recovery.expected_bucket_policy()
        policy["Statement"].append({"Sid": "DenyInsecureTransport", "Effect": "Deny", "Action": "s3:*"})
        bucket["change"]["before"]["policy"] = json.dumps(policy)
        with self.assertRaisesRegex(ValueError, "exact expected OAC-only"):
            recovery.bucket_dependency_only(self.plan)

    def test_bucket_policy_grant_widening_is_rejected(self):
        bucket, data = self.add_bucket_dependency()
        policy = recovery.expected_bucket_policy()
        policy["Statement"][0]["Principal"] = "*"
        bucket["change"]["before"]["policy"] = json.dumps(policy)
        with self.assertRaisesRegex(ValueError, "exact expected OAC-only"):
            recovery.bucket_dependency_only(self.plan)

    def test_known_policy_must_be_semantically_unchanged(self):
        bucket, data = self.add_bucket_dependency()
        bucket["change"]["after_unknown"] = {}
        policy = recovery.expected_bucket_policy()
        policy["Statement"][0]["Action"] = ["s3:GetObject"]
        bucket["change"]["after"]["policy"] = json.dumps(policy)
        self.assertTrue(recovery.bucket_dependency_only(self.plan))
        policy["Statement"][0]["Action"] = "s3:*"
        bucket["change"]["after"]["policy"] = json.dumps(policy)
        with self.assertRaises(ValueError):
            recovery.bucket_dependency_only(self.plan)

    def test_bucket_replacement_or_other_bucket_identity_is_rejected(self):
        bucket, data = self.add_bucket_dependency()
        bucket["change"]["after"]["bucket"] = "other-bucket"
        with self.assertRaisesRegex(ValueError, "bucket identity"):
            recovery.bucket_dependency_only(self.plan)
        bucket["change"]["after"]["bucket"] = recovery.BUCKET
        bucket["change"]["actions"] = ["delete", "create"]
        with self.assertRaisesRegex(ValueError, "create/delete/replacement"):
            recovery.bucket_dependency_only(self.plan)

    def test_dependency_exception_does_not_hide_other_drift(self):
        self.add_bucket_dependency()
        extra = copy.deepcopy(self.policy)
        extra["address"] = "aws_cloudfront_function.unreviewed"
        self.plan["resource_changes"].append(extra)
        with self.assertRaisesRegex(ValueError, "unreviewed resource"):
            recovery.bucket_dependency_only(self.plan)

    def test_exceptional_targeted_plan_remains_narrow_and_cannot_be_deferred(self):
        self.plan["complete"] = False
        self.assertEqual(recovery.approved_changes(self.plan, targeted=True), [recovery.POLICY, recovery.DISTRIBUTION])
        self.plan["deferred_changes"] = [{}]
        with self.assertRaisesRegex(ValueError, "deferred changes"):
            recovery.approved_changes(self.plan, targeted=True)

    def test_noop_and_data_read_are_not_mutations(self):
        self.assertEqual(recovery.approved_changes({"resource_changes": [
            {"mode": "managed", "change": {"actions": ["no-op"]}},
            {"mode": "data", "change": {"actions": ["read"]}},
        ]}), [])

    def test_create_delete_and_replacement_rejected(self):
        for actions in (["create"], ["delete"], ["delete", "create"], ["create", "delete"]):
            with self.subTest(actions=actions):
                self.policy["change"]["actions"] = actions
                with self.assertRaises(ValueError):
                    recovery.approved_changes(self.plan)

    def test_iam_runtime_and_other_policy_updates_rejected(self):
        for address in ("aws_iam_role_policy.github_apply", "module.network.aws_subnet.data[0]",
                        "aws_cloudfront_response_headers_policy.other"):
            with self.subTest(address=address):
                self.policy["address"] = address
                with self.assertRaises(ValueError):
                    recovery.approved_changes(self.plan)

    def test_wrong_ids_rejected(self):
        for resource in (self.policy, self.distribution):
            before = resource["change"]["after"]["id"]
            resource["change"]["after"]["id"] = "another-id"
            with self.assertRaises(ValueError):
                recovery.approved_changes(self.plan)
            resource["change"]["after"]["id"] = before

    def test_secret_rotation_rejected_without_exposing_value(self):
        self.distribution["change"]["after"]["origin"][1]["custom_header"][0]["value"] = "new-private-value"
        with self.assertRaises(ValueError) as error:
            recovery.approved_changes(self.plan)
        self.assertNotIn("new-private-value", str(error.exception))

    def test_non_idle_origin_rejected(self):
        self.distribution["change"]["after"]["origin"][1]["domain_name"] = "another-alb.example"
        with self.assertRaises(ValueError):
            recovery.approved_changes(self.plan)

    def test_bucket_origin_change_rejected(self):
        self.distribution["change"]["after"]["origin"][0]["domain_name"] = "another-bucket.example"
        with self.assertRaises(ValueError):
            recovery.approved_changes(self.plan)

    def test_distribution_disable_rejected(self):
        self.distribution["change"]["after"]["enabled"] = False
        with self.assertRaises(ValueError):
            recovery.approved_changes(self.plan)

    def test_header_policy_rename_rejected(self):
        self.policy["change"]["after"]["name"] = "another-policy"
        with self.assertRaises(ValueError):
            recovery.approved_changes(self.plan)

    def test_deferred_incomplete_and_errored_plans_rejected(self):
        for key, value in (("deferred_changes", [{}]), ("complete", False), ("errored", True)):
            plan = copy.deepcopy(self.plan)
            plan[key] = value
            with self.assertRaises(ValueError):
                recovery.approved_changes(plan)

    def test_live_policy_must_match_source_regardless_of_header_order(self):
        canonical = {"SecurityHeadersConfig": {"content": "reviewed"}, "CustomHeadersConfig": {
            "Quantity": 2, "Items": [{"Header": "b", "Value": "two"}, {"Header": "a", "Value": "one"}]},
            "RemoveHeadersConfig": {"Quantity": 1, "Items": [{"Header": "Server"}]}}
        actual = copy.deepcopy(canonical)
        actual["CustomHeadersConfig"]["Items"].reverse()
        recovery.verify_headers(actual, canonical)
        actual["SecurityHeadersConfig"] = {}
        with self.assertRaises(ValueError):
            recovery.verify_headers(actual, canonical)

    def test_only_empty_unrequested_xss_block_is_normalised(self):
        canonical = {"SecurityHeadersConfig": {"ContentTypeOptions": {"Override": True}},
                     "CustomHeadersConfig": {"Quantity": 0, "Items": []},
                     "RemoveHeadersConfig": {"Quantity": 0, "Items": []}}
        actual = copy.deepcopy(canonical)
        actual["SecurityHeadersConfig"]["XSSProtection"] = {}
        recovery.verify_headers(actual, canonical)
        self.assertIn("XSSProtection", actual["SecurityHeadersConfig"])
        self.assertNotIn("XSSProtection", canonical["SecurityHeadersConfig"])
        for block in ({"Protection": True}, {"Protection": False}, {"Override": False}):
            actual["SecurityHeadersConfig"]["XSSProtection"] = block
            with self.subTest(block=block), self.assertRaises(ValueError):
                recovery.verify_headers(actual, canonical)

    def test_empty_other_block_or_missing_expected_xss_still_fails(self):
        canonical = {"SecurityHeadersConfig": {}, "CustomHeadersConfig": {"Quantity": 0, "Items": []},
                     "RemoveHeadersConfig": {"Quantity": 0, "Items": []}}
        actual = copy.deepcopy(canonical)
        actual["SecurityHeadersConfig"]["FrameOptions"] = {}
        with self.assertRaises(ValueError):
            recovery.verify_headers(actual, canonical)
        actual["SecurityHeadersConfig"] = {"XSSProtection": {}}
        canonical["SecurityHeadersConfig"]["XSSProtection"] = {"Protection": True, "Override": True}
        with self.assertRaises(ValueError):
            recovery.verify_headers(actual, canonical)

    def test_active_workflow_blocks_recovery(self):
        with patch.object(recovery, "read_url", side_effect=[
            b'{"object":{"sha":"' + recovery.SHA.encode() + b'"}}',
            b'{"workflow_runs":[{"status":"in_progress"}]}',
        ]):
            with self.assertRaisesRegex(ValueError, "workflow is active"):
                recovery.check_workflows()

    def test_new_prod_revision_blocks_recovery(self):
        with patch.object(recovery, "read_url", return_value=b'{"object":{"sha":"new-sha"}}'):
            with self.assertRaisesRegex(ValueError, "prod moved"):
                recovery.check_workflows()

    def test_runner_drops_ambient_terraform_overrides(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict("os.environ", {
            "TF_VAR_origin_secret": "private", "TF_CLI_ARGS_apply": "-target=other", "TF_WORKSPACE": "other",
            "TF_REATTACH_PROVIDERS": "override",
        }):
            runner = recovery.Runner(Path(directory))
            self.addCleanup(runner.close)
            for key in ("TF_VAR_origin_secret", "TF_CLI_ARGS_apply", "TF_WORKSPACE", "TF_REATTACH_PROVIDERS"):
                self.assertNotIn(key, runner.env)
            self.assertEqual(runner.env["AWS_REGION"], recovery.REGION)

    def exercise_recovery(self, apply, confirmation="APPLY SWE5001 EDGE", account=recovery.ACCOUNT, resume=False):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        work = Path(directory.name)
        calls = []
        self.calls = calls
        scope_reads = 0
        canonical = {"SecurityHeadersConfig": {}, "CustomHeadersConfig": {"Quantity": 0, "Items": []},
                     "RemoveHeadersConfig": {"Quantity": 0, "Items": []}}
        policy_file = work / "repo/deployment/frontend/cloudfront/response-headers-policy.json"
        policy_file.parent.mkdir(parents=True)
        policy_file.write_text(json.dumps(canonical), encoding="utf-8")

        def fake_run(phase, command, *args):
            nonlocal scope_reads
            calls.append(command)
            if "Checking source SHA" == phase:
                return SimpleNamespace(stdout=recovery.SHA + "\n", returncode=0)
            if "Checking existing private Terraform version" == phase:
                return SimpleNamespace(stdout=json.dumps({"terraform_version": recovery.VERSION}), returncode=0)
            if "Checking private plan scope" == phase:
                scope_reads += 1
                plan = self.plan
                if scope_reads > 1:
                    plan = dict(self.plan, complete=False, resource_changes=[resource for resource in self.plan["resource_changes"]
                                if resource["address"] in (recovery.POLICY, recovery.DISTRIBUTION)])
                return SimpleNamespace(stdout=json.dumps(plan), returncode=0)
            return SimpleNamespace(stdout="", returncode=0)

        with patch.object(recovery.shutil, "which", return_value="tool"), \
                patch.object(recovery, "check_workflows") as workflow_check, \
                patch.object(recovery, "install_terraform", return_value=work / "terraform") as install, \
                patch.object(recovery, "Runner") as runner_type, \
                patch("builtins.input", return_value=confirmation), patch("builtins.print"):
            runner = runner_type.return_value
            self.install = install
            self.workflow_check = workflow_check
            runner.run.side_effect = fake_run
            runner.aws.side_effect = [
                {"Account": account, "Arn": "arn:aws:sts::402178232802:assumed-role/operator/test"},
                {"Account": account, "Arn": "arn:aws:sts::402178232802:assumed-role/operator/test"},
                {"ResponseHeadersPolicy": {"ResponseHeadersPolicyConfig": canonical}},
                {"Policy": json.dumps(recovery.expected_bucket_policy())},
            ]
            recovery.recover(work, apply, resume=resume)
        return calls, work, workflow_check.call_count

    def test_plan_only_never_applies(self):
        calls, work, checks = self.exercise_recovery(False)
        self.assertFalse(any("apply" in command for command in calls))
        self.assertFalse((work / "receipt.json").exists())
        self.assertEqual(checks, 1)

    def test_apply_requires_explicit_confirmation(self):
        with self.assertRaisesRegex(ValueError, "Apply not confirmed"):
            self.exercise_recovery(True, "no")

    def test_wrong_account_stops_before_checkout_or_install(self):
        with self.assertRaisesRegex(ValueError, "Wrong AWS account"):
            self.exercise_recovery(True, account="000000000000")
        self.assertEqual(self.calls, [])
        self.install.assert_not_called()
        self.workflow_check.assert_not_called()

    def test_confirmed_apply_uses_saved_plan_then_verifies(self):
        calls, work, checks = self.exercise_recovery(True)
        applies = [command for command in calls if "apply" in command]
        self.assertEqual(len(applies), 1)
        self.assertEqual(Path(applies[0][-1]).parent, work)
        self.assertTrue(Path(applies[0][-1]).name.startswith("edge-"))
        self.assertTrue((work / "receipt.json").exists())
        self.assertEqual(checks, 2)
        self.assertTrue(any("-detailed-exitcode" in command for command in calls))
        scope = json.loads((work / "latest-plan-scope.json").read_text(encoding="utf-8"))
        self.assertEqual(scope["source"], recovery.SHA)
        self.assertNotIn("private-test-placeholder", json.dumps(scope))

    def test_resume_rechecks_source_without_cloning(self):
        calls, work, checks = self.exercise_recovery(True, resume=True)
        self.assertFalse(any("clone" in command or "checkout" in command for command in calls))
        self.assertTrue(any("--porcelain" in command for command in calls))
        self.assertTrue((work / "receipt.json").exists())

    def test_dependency_recovery_applies_cf_only_then_requires_full_convergence(self):
        self.add_bucket_dependency()
        calls, work, checks = self.exercise_recovery(True)
        targets = [command for command in calls if f"-target={recovery.POLICY}" in command]
        self.assertEqual(len(targets), 1)
        self.assertIn(f"-target={recovery.DISTRIBUTION}", targets[0])
        applies = [command for command in calls if "apply" in command]
        self.assertEqual(len(applies), 1)
        self.assertTrue(Path(applies[0][-1]).name.startswith("cloudfront-edge-"))
        convergence = [command for command in calls if "-detailed-exitcode" in command]
        self.assertEqual(len(convergence), 1)
        self.assertFalse(any(value.startswith("-target=") for value in convergence[0]))
        receipt = json.loads((work / "receipt.json").read_text(encoding="utf-8"))
        self.assertTrue(receipt["cloudfront_only_recovery"])
        self.assertNotIn(recovery.BUCKET_POLICY, receipt["updates"])
        self.assertEqual(receipt["bucket_policy"], "unchanged OAC grant verified")

    @staticmethod
    def temporary_credentials():
        return {"Version": 1, "AccessKeyId": "temporary-test-key", "SecretAccessKey": "private-test-secret",
                "SessionToken": "private-test-token", "Expiration": "2030-01-01T01:00:00Z"}

    def test_refresh_profile_avoids_direct_sdk_endpoint_and_restores_cli_chain(self):
        base = {"AWS_CONTAINER_CREDENTIALS_FULL_URI": "http://localhost/credentials",
                "AWS_CONTAINER_AUTHORIZATION_TOKEN": "private-endpoint-token", "AWS_PROFILE": "other",
                "AWS_ROLE_ARN": "role", "AWS_WEB_IDENTITY_TOKEN_FILE": "token-file", "AWS_REGION": recovery.REGION}
        env = recovery.credential_environment(base, "private-config", "empty-credentials")
        self.assertEqual(env["AWS_PROFILE"], "swe5001-edge-recovery")
        self.assertEqual(env["AWS_EC2_METADATA_DISABLED"], "true")
        self.assertFalse(any(key.startswith(("AWS_CONTAINER_", "AWS_ROLE_", "AWS_WEB_IDENTITY_")) for key in env))
        self.assertNotIn("AWS_SESSION_TOKEN", env)
        self.assertEqual(recovery.original_credential_environment(env), base)
        self.assertIn("AWS_PROFILE", base)

    def test_long_lived_credentials_rejected(self):
        credentials = self.temporary_credentials()
        del credentials["SessionToken"]
        with self.assertRaisesRegex(ValueError, "temporary credentials"):
            recovery.validate_credentials(credentials, datetime(2030, 1, 1, tzinfo=timezone.utc))

    def test_valid_short_lived_credentials_are_accepted(self):
        recovery.validate_credentials(self.temporary_credentials(), datetime(2030, 1, 1, 0, 50, tzinfo=timezone.utc))

    def test_expired_credentials_rejected(self):
        with self.assertRaisesRegex(ValueError, "expired"):
            recovery.validate_credentials(self.temporary_credentials(), datetime(2030, 1, 1, 1, tzinfo=timezone.utc))

    def test_credentials_are_not_written_or_printed(self):
        credentials = self.temporary_credentials()
        credentials["Expiration"] = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
        with tempfile.TemporaryDirectory() as directory, patch.object(recovery.subprocess, "run") as run, \
                patch("builtins.print") as output:
            work = Path(directory)
            runner = recovery.Runner(work)
            self.addCleanup(runner.close)
            run.return_value = SimpleNamespace(returncode=0, stdout=json.dumps(credentials))
            runner.configure_credentials()
            self.assertEqual(run.call_args.kwargs["stdout"], recovery.subprocess.PIPE)
            self.assertNotIn("AWS_SESSION_TOKEN", runner.env)
            self.assertIn("credential_process", (runner.logs / "aws-config").read_text(encoding="utf-8"))
            for file in work.rglob("*"):
                if file.is_file():
                    self.assertNotIn(credentials["SecretAccessKey"], file.read_text(encoding="utf-8"))
                    self.assertNotIn(credentials["SessionToken"], file.read_text(encoding="utf-8"))
            self.assertNotIn(credentials["SessionToken"], str(output.call_args_list))

    def test_credential_process_refreshes_without_recursion_or_terminal_logs(self):
        base = {"AWS_CONTAINER_CREDENTIALS_FULL_URI": "http://localhost/credentials", "AWS_PROFILE": "original"}
        env = recovery.credential_environment(base, "private-config", "empty-credentials")
        first = self.temporary_credentials()
        first["Expiration"] = (datetime.now(timezone.utc) + timedelta(minutes=10)).isoformat()
        second = dict(first, AccessKeyId="refreshed-test-key")
        with patch.dict(recovery.os.environ, env, clear=True), \
                patch.object(recovery.subprocess, "run", side_effect=[
                    SimpleNamespace(returncode=0, stdout=json.dumps(first)),
                    SimpleNamespace(returncode=0, stdout=json.dumps(second)),
                ]) as run, patch.object(sys, "stderr", new_callable=io.StringIO) as errors:
            for expected in (first, second):
                with patch.object(sys, "stdout", new_callable=io.StringIO) as protocol:
                    self.assertEqual(recovery.export_credential_process(), 0)
                    self.assertEqual(json.loads(protocol.getvalue()), expected)
                self.assertEqual(run.call_args.kwargs["env"], base)
            self.assertEqual(errors.getvalue(), "")

    def test_failed_credential_process_never_echoes_raw_cli_output(self):
        with patch.object(recovery.subprocess, "run", return_value=SimpleNamespace(
                returncode=1, stdout="private-raw-output", stderr="private-raw-error")), \
                patch.object(sys, "stdout", new_callable=io.StringIO) as output, \
                patch.object(sys, "stderr", new_callable=io.StringIO) as errors:
            self.assertEqual(recovery.export_credential_process(), 1)
            self.assertEqual(output.getvalue(), "")
            self.assertNotIn("private-raw", errors.getvalue())

    def test_failed_resume_keeps_previous_logs(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            original = work / "07.log"
            original.write_text("original diagnostic", encoding="utf-8")
            first, second = recovery.Runner(work), recovery.Runner(work)
            self.addCleanup(first.close)
            self.addCleanup(second.close)
            self.assertNotEqual(first.logs, second.logs)
            self.assertEqual(original.read_text(encoding="utf-8"), "original diagnostic")


if __name__ == "__main__":
    unittest.main()
