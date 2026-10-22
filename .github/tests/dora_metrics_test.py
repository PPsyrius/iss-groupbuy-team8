from datetime import datetime, timezone
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("dora_metrics", Path(__file__).parents[1] / "scripts/dora-metrics.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

SINCE = datetime(2026, 10, 1, tzinfo=timezone.utc)
UNTIL = datetime(2026, 10, 15, tzinfo=timezone.utc)


def run(sha, *jobs):
    return {"head_sha": sha, "jobs": [
        {"name": name, "conclusion": conclusion, "completed_at": completed} for name, conclusion, completed in jobs
    ]}


def at(text):
    return datetime.fromisoformat(text).replace(tzinfo=timezone.utc)


class DoraMetricsTest(unittest.TestCase):
    def setUp(self):
        self.runs = [
            run("c3", ("deploy-existing-ec2", "success", "2026-10-03T10:00:00Z")),
            run("c1", ("package-dev", "success", "2026-10-01T09:00:00Z"),
                ("deploy-existing-ec2", "success", "2026-10-01T10:00:00Z")),
            run("c2", ("deploy-existing-ec2", "failure", "2026-10-02T10:00:00Z")),
            run("docs", ("deploy-existing-ec2", "skipped", "2026-10-02T11:00:00Z")),
            run("cancelled", ("deploy-existing-ec2", "cancelled", "2026-10-02T12:00:00Z")),
        ]
        self.deploys = module.deployments(self.runs, "deploy-existing-ec2")

    def test_only_completed_attempts_of_the_named_job_count_oldest_first(self):
        self.assertEqual([d["sha"] for d in self.deploys], ["c1", "c2", "c3"])
        self.assertEqual([d["ok"] for d in self.deploys], [True, False, True])

    def test_frequency_counts_successful_deployments_per_week(self):
        self.assertAlmostEqual(module.frequency_per_week(self.deploys, SINCE, UNTIL), 1.0)

    def test_a_failed_change_is_lead_timed_to_the_next_successful_deployment(self):
        first = {"c1": at("2026-10-01T06:00:00"), "c2": at("2026-10-02T04:00:00")}
        self.assertEqual(module.lead_times(self.deploys, first), [4.0, 30.0])

    def test_change_success_rate_counts_manual_rollbacks_as_failures(self):
        self.assertAlmostEqual(module.change_success_rate(self.deploys), 2 / 3)
        self.assertAlmostEqual(module.change_success_rate(self.deploys, rollbacks=1), 2 / 4)
        self.assertIsNone(module.change_success_rate([]))

    def test_recovery_runs_from_the_first_failure_to_the_next_success(self):
        self.assertEqual(module.recovery_times(self.deploys), [24.0])
        unrecovered = self.deploys[:2]
        self.assertEqual(module.recovery_times(unrecovered), [])

    def test_summary_reports_median_and_p90(self):
        self.assertEqual(module.summary([]), {"n": 0, "median": None, "p90": None})
        result = module.summary([1, 2, 3, 4, 10])
        self.assertEqual((result["n"], result["median"], result["p90"]), (5, 3, 10))

    def test_render_lists_every_metric_per_part(self):
        first = {"c1": at("2026-10-01T06:00:00")}
        table = module.render({"backend": module.metrics(self.deploys, first, SINCE, UNTIL)}, SINCE, UNTIL)
        for label in ["Deployment frequency", "Deployment lead time", "Change success rate",
                      "Mean time to recover", "2 / 3", "67 %"]:
            self.assertIn(label, table)


if __name__ == "__main__":
    unittest.main()
