"""Offline guards for the SWE5001 read-only subnet evidence collector."""

import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[2] / "infra/swe5001/scripts/inspect-subnets.py"
SPEC = importlib.util.spec_from_file_location("subnet_inspection", SCRIPT)
inspection = importlib.util.module_from_spec(SPEC)
with patch.object(sys, "dont_write_bytecode", True):
    SPEC.loader.exec_module(inspection)


class SubnetInspectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name) / "evidence"
        self.calls = []
        self.responses = {
            ("sts", "get-caller-identity"): inspection.ACCOUNT,
            ("ec2", "describe-vpcs"): [{"id": "vpc-001", "state": "available", "cidr": "10.50.0.0/16"}],
            ("ec2", "describe-subnets"): [],
            ("ec2", "describe-route-tables"): [],
            ("ec2", "describe-vpc-endpoints"): [],
            ("ec2", "describe-security-groups"): [
                {"name": f"{inspection.NAME}-{name}", "id": f"sg-00{index}"}
                for index, name in enumerate(("svc", "rds", "cache", "dbinit"))
            ],
            ("ecs", "describe-services"): {
                "failures": [], "services": [
                    {"name": name, "status": "ACTIVE", "desired": 1, "running": 1, "pending": 0}
                    for name in inspection.SERVICES
                ],
            },
            ("ecs", "describe-tasks"): {"failures": [], "tasks": [
                {"arn": self.arn(index), "status": "RUNNING", "attachments": [
                    {"type": "ElasticNetworkInterface", "details": [
                        {"name": "networkInterfaceId", "value": f"eni-00{index}"}
                    ]}
                ]} for index in range(3)
            ]},
            ("rds", "describe-db-instances"): [{"id": inspection.NAME}],
            ("elasticache", "describe-replication-groups"): [{"id": inspection.NAME, "members": ["cache-001"]}],
            ("elasticache", "describe-cache-clusters"): [{"id": "cache-001", "subnet_group": inspection.NAME}],
            ("elasticache", "describe-cache-subnet-groups"): [],
        }

    @staticmethod
    def arn(index):
        return f"arn:aws:ecs:{inspection.REGION}:{inspection.ACCOUNT}:task/{inspection.NAME}/{index:032x}"

    def fake_aws(self, *args):
        key = tuple(args[:2])
        self.calls.append(args)
        self.assertIn(key, inspection.READ_CALLS)
        self.assertIn("--query", args)
        if key == ("ecs", "list-tasks"):
            service = args[args.index("--service-name") + 1]
            return [self.arn(inspection.SERVICES.index(service))]
        if key == ("ec2", "describe-network-interfaces"):
            if "--network-interface-ids" in args:
                return [{"id": f"eni-00{i}", "vpc": "vpc-001"} for i in range(3)]
            return [{"groups": [{"GroupId": "sg-001"}]}, {"groups": [{"GroupId": "sg-002"}]}]
        result = self.responses[key]
        if isinstance(result, Exception):
            raise result
        return copy.deepcopy(result)

    def collect(self):
        inspection.collect(self.directory, call=self.fake_aws)

    def assert_incomplete(self):
        self.assertTrue((self.directory / "incomplete.json").exists())
        self.assertFalse((self.directory / "collection.json").exists())

    def test_collection_is_not_acceptance_or_connectivity(self):
        self.collect()
        result = json.loads((self.directory / "collection.json").read_text())
        self.assertEqual(result["metadata_collection"], "complete")
        self.assertEqual(result["acceptance"], "not assessed")
        self.assertEqual(result["connectivity"], "not tested")
        # Empty placement/routes fixtures can be captured, but never labelled a pass.
        self.assertEqual(json.loads((self.directory / "subnets.json").read_text()), [])
        self.assertEqual(len(self.calls), 17)

    def test_wrong_account_stops_before_resource_reads(self):
        self.responses[("sts", "get-caller-identity")] = "000000000000"
        with self.assertRaisesRegex(ValueError, "Expected account"):
            self.collect()
        self.assertEqual(len(self.calls), 1)
        self.assert_incomplete()

    def test_absent_or_ambiguous_runtime_is_not_success(self):
        for vpcs in ([], [{"id": "vpc-a", "state": "available"}] * 2):
            with self.subTest(vpcs=vpcs), tempfile.TemporaryDirectory() as temp:
                self.directory = Path(temp) / "evidence"
                self.responses[("ec2", "describe-vpcs")] = vpcs
                with self.assertRaisesRegex(ValueError, "exactly one"):
                    self.collect()
                self.assert_incomplete()

    def test_read_failure_retains_incomplete_receipt(self):
        self.responses[("ec2", "describe-subnets")] = subprocess.CalledProcessError(254, "aws")
        with self.assertRaises(subprocess.CalledProcessError):
            self.collect()
        self.assert_incomplete()

    def test_missing_security_group_is_identified_before_service_reads(self):
        self.responses[("ec2", "describe-security-groups")].pop()
        with self.assertRaisesRegex(ValueError, "missing=\\['blocbuy-swe5001-dbinit'\\]"):
            self.collect()
        self.assertFalse(any(args[0] == "ecs" for args in self.calls))
        self.assertTrue((self.directory / "security-groups.json").exists())
        self.assert_incomplete()

    def test_duplicate_security_groups_are_rejected(self):
        groups = self.responses[("ec2", "describe-security-groups")]
        groups.append(copy.deepcopy(groups[0]))
        with self.assertRaisesRegex(ValueError, "returned=5"):
            self.collect()
        self.assert_incomplete()

    def test_security_group_lookup_uses_exact_terraform_names_and_vpc(self):
        self.collect()
        lookup = next(args for args in self.calls if args[:2] == ("ec2", "describe-security-groups"))
        self.assertIn("Name=vpc-id,Values=vpc-001", lookup)
        self.assertIn("Name=group-name,Values=" + ",".join(
            f"{inspection.NAME}-{name}" for name in ("svc", "rds", "cache", "dbinit")), lookup)

    def test_missing_services_or_pending_tasks_are_rejected(self):
        for replacement in ({"failures": [{"reason": "MISSING"}], "services": []},
                            {"failures": [], "services": [dict(s, pending=1) for s in self.responses[("ecs", "describe-services")]["services"]]}):
            with self.subTest(replacement=replacement), tempfile.TemporaryDirectory() as temp:
                self.directory = Path(temp) / "evidence"
                self.responses[("ecs", "describe-services")] = replacement
                with self.assertRaises(ValueError):
                    self.collect()
                self.assert_incomplete()

    def test_stopped_task_is_not_placement_evidence(self):
        self.responses[("ecs", "describe-tasks")]["tasks"][0]["status"] = "STOPPED"
        with self.assertRaisesRegex(ValueError, "Tasks changed"):
            self.collect()
        self.assert_incomplete()

    def test_missing_rds_is_not_success(self):
        self.responses[("rds", "describe-db-instances")] = []
        with self.assertRaisesRegex(ValueError, "RDS instance"):
            self.collect()
        self.assert_incomplete()

    def test_existing_directory_is_never_overwritten(self):
        self.directory.mkdir()
        sentinel = self.directory / "keep.txt"
        sentinel.write_text("existing user evidence")
        with self.assertRaises(FileExistsError):
            self.collect()
        self.assertEqual(sentinel.read_text(), "existing user evidence")
        self.assertEqual(self.calls, [])

    def test_real_adapter_fixes_region_and_refuses_mutations(self):
        with patch.object(inspection.subprocess, "run") as run:
            run.return_value.stdout = json.dumps(inspection.ACCOUNT)
            self.assertEqual(inspection.aws("sts", "get-caller-identity", "--query", "Account"), inspection.ACCOUNT)
            command = run.call_args.args[0]
            self.assertEqual(command[command.index("--region") + 1], inspection.REGION)
            self.assertIn("--no-cli-pager", command)
            run.reset_mock()
            with self.assertRaisesRegex(ValueError, "read-only"):
                inspection.aws("ec2", "start-instances")
            run.assert_not_called()

    def test_collection_never_requests_secret_surfaces(self):
        self.collect()
        commands = json.dumps(self.calls)
        for forbidden in ("get-parameter", "--with-decryption", "describe-task-definition", "describe-rules", "get-log-events"):
            self.assertNotIn(forbidden, commands)


if __name__ == "__main__":
    unittest.main()
