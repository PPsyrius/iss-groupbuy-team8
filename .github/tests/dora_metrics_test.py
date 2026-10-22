from contextlib import redirect_stderr, redirect_stdout
from copy import deepcopy
from datetime import timedelta
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

SCRIPT = Path(__file__).parents[1] / "scripts/dora-metrics.py"
spec = importlib.util.spec_from_file_location("dora_metrics", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

REPO = "owner/repo"
SINCE = module.parse_date("2026-10-01")
UNTIL = module.parse_date("2026-10-15")
CUTOFF = module.parse_date("2026-10-16")


def at(value):
    return module.parse_time(value if "T" in value else value + "T00:00:00Z")


def sha(number):
    return f"{number:040x}"


def job(number, conclusion="success", finished="2026-10-02T10:00:00Z", *,
        name="deploy-existing-ec2", attempt=1, created=None):
    end = at(finished) if finished else None
    created = at(created) if created else (end - timedelta(minutes=2) if end else SINCE)
    return {"id": number, "name": name, "run_attempt": attempt,
            "status": "completed" if end else "in_progress", "conclusion": conclusion,
            "created_at": module.iso(created), "started_at": module.iso(created + timedelta(seconds=1)),
            "completed_at": module.iso(end) if end else None}


def run(number, *jobs, head=None, created="2026-09-30T00:00:00Z", event="push", branch="dev"):
    return {"id": number, "head_sha": head or sha(number), "created_at": created,
            "run_attempt": max((j["run_attempt"] for j in jobs), default=1),
            "event": event, "head_branch": branch, "jobs": list(jobs)}


def change(number, first="2026-10-01T00:00:00Z"):
    return {"status": "matched", "number": number, "merged_at": "2026-10-01T01:00:00Z",
            "commits_count": 1,
            "commits": [{"sha": sha(number + 1000), "author_date": first}]}


def snapshot(backend=(), frontend=(), rollback=()):
    sources = {}
    changes = {}
    for part, runs in (("backend", backend), ("frontend", frontend), ("rollback", rollback)):
        sources[part] = {"workflow": module.PARTS[part][0], "runs_count": len(runs),
                         "all_jobs_count": sum(len(r["jobs"]) for r in runs),
                         "selected_jobs_count": sum(len(r["jobs"]) for r in runs), "runs": deepcopy(list(runs))}
        if part != "rollback":
            for deploy in module.deployments(runs, module.PARTS[part][1]):
                if deploy["finished"] < CUTOFF:
                    changes[deploy["sha"]] = change(int(deploy["sha"], 16))
    return {"schema_version": module.SCHEMA, "repo": REPO, "branch": "dev", "complete": True,
            "window": [module.iso(SINCE), module.iso(UNTIL)], "observed_at": module.iso(CUTOFF),
            "collection_started_at": module.iso(CUTOFF), "collection_completed_at": module.iso(CUTOFF),
            "history_scope": "all retained runs created at or before observed_at; all job attempts",
            "sources": sources, "changes": changes, "ancestry": {}}


def proof(base, head, status="ahead", merge_base=None):
    return {base + "/" + head: {"status": status, "merge_base_sha": merge_base or base}}


class CalculationTest(unittest.TestCase):
    def setUp(self):
        self.runs = [
            run(3, job(30, finished="2026-10-03T10:00:00Z")),
            run(1, job(10, finished="2026-10-01T10:00:00Z")),
            run(2, job(20, "failure"), job(21, finished="2026-10-02T12:00:00Z", attempt=2)),
        ]
        self.data = snapshot(self.runs)

    def backend(self, data=None, since=SINCE, until=UNTIL):
        return module.analyze(data or self.data, since, until)["results"]["backend"]

    def test_all_attempts_have_stable_ids_and_chronological_order(self):
        deploys = module.deployments(self.runs, "deploy-existing-ec2")
        self.assertEqual([d["job_id"] for d in deploys], [10, 20, 21, 30])
        self.assertEqual([d["attempt"] for d in deploys], [1, 1, 2, 1])
        self.assertEqual([d["ok"] for d in deploys], [True, False, True, True])

    def test_frequency_and_rate_count_attempts_but_lead_counts_unique_changes(self):
        result = self.backend()
        self.assertEqual((result["successful"], result["attempts"]), (3, 4))
        self.assertEqual(result["success_rate"], 0.75)
        self.assertEqual(result["per_week"], 1.5)
        self.assertEqual(result["lead_time_h"]["n"], 3)
        self.assertEqual([s["hours"] for s in result["lead_time_h"]["samples"]], [10, 36, 58])

    def test_multiple_successful_reruns_do_not_resample_a_change(self):
        data = snapshot([run(1, job(10), job(11, attempt=2, finished="2026-10-03T10:00:00Z"))])
        result = self.backend(data)
        self.assertEqual(result["successful"], 2)
        self.assertEqual(result["lead_time_h"]["n"], 1)
        self.assertEqual(result["lead_time_h"]["samples"][0]["job_id"], 10)

    def test_lead_time_requires_recorded_containment_not_a_later_success(self):
        data = snapshot([run(1, job(10, "failure")), run(2, job(20, finished="2026-10-03T10:00:00Z"))])
        data["ancestry"] = proof(sha(1), sha(2), "diverged", sha(99))
        lead = self.backend(data)["lead_time_h"]
        self.assertEqual((lead["n"], lead["censored"]), (1, 1))
        data["ancestry"] = proof(sha(1), sha(2))
        self.assertEqual(self.backend(data)["lead_time_h"]["n"], 2)

    def test_inconsistent_merge_base_does_not_establish_containment(self):
        self.assertFalse(module.contains(sha(1), sha(2), proof(sha(1), sha(2), merge_base=sha(99))))
        self.assertTrue(module.contains(sha(1), sha(1), {}))

    def test_missing_ancestry_evidence_is_an_error_not_a_negative_answer(self):
        with self.assertRaisesRegex(module.ReportError, "Missing ancestry"):
            module.contains(sha(1), sha(2), {})

    def test_lead_uses_earliest_author_date_across_pr_commits(self):
        self.data["changes"][sha(1)]["commits"].append({"sha": sha(9999), "author_date": "2026-09-30T10:00:00Z"})
        self.assertEqual(self.backend()["lead_time_h"]["samples"][0]["hours"], 24)

    def test_missing_pr_and_missing_author_dates_have_explicit_coverage(self):
        self.data["changes"][sha(1)] = {"status": "no_matching_merged_pr"}
        self.data["changes"][sha(2)]["commits"][0]["author_date"] = None
        lead = self.backend()["lead_time_h"]
        self.assertEqual((lead["eligible"], lead["n"]), (3, 1))
        self.assertEqual(lead["missing"], {"no_matching_merged_pr": 1, "missing_author_date": 1})

    def test_negative_lead_time_is_explicitly_invalid(self):
        self.data["changes"][sha(1)]["commits"][0]["author_date"] = "2026-10-02T00:00:00Z"
        self.assertEqual(self.backend()["lead_time_h"]["invalid_timestamp"], 1)

    def test_prior_window_changes_are_not_resampled_when_retried(self):
        result = self.backend(since=at("2026-10-02T11:00:00Z"))
        self.assertEqual((result["successful"], result["lead_time_h"]["eligible"]), (2, 1))

    def test_window_is_completion_based_and_end_exclusive(self):
        result = self.backend(since=at("2026-10-02T10:00:00Z"), until=at("2026-10-02T12:00:00Z"))
        self.assertEqual((result["attempts"], result["successful"]), (1, 0))
        self.assertEqual(result["lead_time_h"]["censored"], 1)
        self.assertEqual(result["recovery_h"]["censored"], 1)

    def test_recovery_groups_consecutive_failures_and_reports_mean(self):
        data = snapshot([run(1, job(10, "failure"), job(11, "failure", attempt=2,
                       finished="2026-10-02T11:00:00Z"), job(12, attempt=3, finished="2026-10-02T13:00:00Z"))])
        recovery = self.backend(data)["recovery_h"]
        self.assertEqual((recovery["n"], recovery["episodes"], recovery["mean"]), (1, 1, 3))

    def test_recovery_after_window_end_is_censored_not_omitted(self):
        result = self.backend(until=at("2026-10-02T11:00:00Z"))
        self.assertEqual((result["recovery_h"]["n"], result["recovery_h"]["episodes"], result["recovery_h"]["censored"]), (0, 1, 1))

    def test_recovery_episode_started_before_window_is_carried_in(self):
        recovery = self.backend(since=at("2026-10-02T11:00:00Z"))["recovery_h"]
        self.assertEqual((recovery["n"], recovery["episodes"], recovery["carried_in"]), (0, 0, 1))

    def test_future_declared_end_uses_observed_duration_not_unobserved_days(self):
        data = snapshot([run(1, job(10))])
        data["observed_at"] = "2026-10-08T00:00:00Z"
        report = module.analyze(data, SINCE, UNTIL)
        self.assertEqual(report["observed_days"], 7)
        self.assertEqual(report["results"]["backend"]["per_week"], 1)

    def test_subday_window_uses_exact_duration_without_a_one_day_floor(self):
        result = self.backend(since=at("2026-10-01T09:00:00Z"), until=at("2026-10-01T11:00:00Z"))
        self.assertEqual(result["per_week"], 84)

    def test_unknown_and_pending_outcomes_are_outside_rate_and_visible(self):
        data = snapshot([run(1, job(10), job(11, "cancelled", attempt=2), job(12, "timed_out", attempt=3),
                        job(13, "skipped", attempt=4), job(14, None, None, attempt=5))])
        result = self.backend(data)
        self.assertEqual((result["successful"], result["attempts"]), (1, 1))
        self.assertEqual(result["other_outcomes"], {"cancelled": 1, "timed_out": 1, "skipped": 1, "incomplete_at_end": 1})

    def test_one_second_skipped_clock_skew_is_preserved_counted_and_not_timed(self):
        skipped = job(10, "skipped", finished="2026-10-02T00:00:00Z")
        skipped["created_at"] = skipped["started_at"] = "2026-10-02T00:00:01Z"
        data = snapshot([run(1, skipped)])
        module.validate_snapshot(data)
        result = self.backend(data)
        self.assertEqual((result["attempts"], result["timestamp_anomalies"]), (0, 1))
        self.assertEqual(result["other_outcomes"], {"skipped": 1})
        end_boundary = self.backend(data, until=at("2026-10-02T00:00:01Z"))
        self.assertEqual(end_boundary["other_outcomes"], {"skipped": 1})
        skipped["started_at"] = "2026-10-02T00:00:02Z"
        with self.assertRaisesRegex(module.ReportError, "timestamps"):
            module.validate_snapshot(snapshot([run(1, skipped)]))

    def test_completion_after_cutoff_is_pending_even_if_currently_successful(self):
        data = snapshot([run(1, job(10, finished="2026-10-10T00:00:00Z", created="2026-10-07T00:00:00Z"))])
        data["observed_at"] = "2026-10-08T00:00:00Z"
        result = self.backend(data)
        self.assertEqual(result["attempts"], 0)
        self.assertEqual(result["other_outcomes"], {"incomplete_at_end": 1})

    def test_job_created_after_window_end_is_not_counted_pending(self):
        result = self.backend(until=at("2026-10-01T11:00:00Z"))
        self.assertEqual(result["other_outcomes"], {})

    def test_no_job_record_is_not_silently_labelled_skipped(self):
        data = snapshot([run(1, created="2026-10-02T00:00:00Z")])
        self.assertEqual(self.backend(data)["runs_without_job"], 1)

    def test_unattributed_dev_rollbacks_do_not_change_deployment_rate(self):
        self.data["sources"]["rollback"] = snapshot(rollback=[run(99, job(990, name="rollback-existing-ec2"),
                         event="workflow_dispatch")])["sources"]["rollback"]
        report = module.analyze(self.data, SINCE, UNTIL)
        self.assertEqual(report["results"]["backend"]["success_rate"], 0.75)
        self.assertEqual(report["results"]["rollback"]["successful"], 1)

    def test_empty_report_has_no_success_or_recovery_claim(self):
        result = self.backend(snapshot())
        self.assertIsNone(result["success_rate"])
        self.assertIsNone(result["recovery_h"]["mean"])
        self.assertEqual(result["lead_time_h"]["eligible"], 0)

    def test_percentile_is_nearest_rank_not_python_rounding(self):
        result = module.summary(list(range(1, 11)))
        self.assertEqual(result, {"n": 10, "mean": 5.5, "median": 5.5, "p90": 9})
        self.assertEqual(module.summary([]), {"n": 0, "mean": None, "median": None, "p90": None})

    def test_render_labels_proxies_coverage_cutoff_smoke_and_rollbacks(self):
        text = module.render(module.analyze(self.data, SINCE, UNTIL))
        for value in ("3 / 4", "75.00 %", "proxy", "censored", "Frozen cutoff", "post-deploy smoke", "Unattributed dev rollback"):
            self.assertIn(value, text)


class FrontendSmokeTest(unittest.TestCase):
    def report(self, *jobs):
        data = snapshot(frontend=[run(1, *jobs)])
        return module.analyze(data, SINCE, UNTIL)["results"]["frontend"]

    def deploy(self, number=10, attempt=1):
        return job(number, name="deploy-s3-dev", attempt=attempt)

    def smoke(self, conclusion="failure", number=11, attempt=1, finished="2026-10-02T10:05:00Z"):
        return job(number, conclusion, finished, name="e2e-dev", attempt=attempt,
                   created="2026-10-02T10:01:00Z")

    def test_successful_publish_and_failed_smoke_are_separate(self):
        result = self.report(self.deploy(), self.smoke())
        self.assertEqual(result["success_rate"], 1)
        self.assertEqual(result["smoke"]["outcomes"], {"failure": 1})

    def test_smoke_only_rerun_does_not_overwrite_original_failed_smoke(self):
        result = self.report(self.deploy(), self.smoke(), self.smoke("success", number=12, attempt=2))
        self.assertEqual(result["smoke"], {"outcomes": {"failure": 1}, "unpaired_smoke_jobs": 1,
                                           "unpaired_outcomes": {"success": 1}})

    def test_pending_and_boundary_crossing_smoke_only_reruns_stay_visible(self):
        reruns = [self.smoke(None, 12, 2, finished=None),
                  self.smoke("success", 12, 2, finished="2026-10-16T00:00:00Z")]
        for rerun in reruns:
            with self.subTest(finished=rerun["completed_at"]):
                result = self.report(self.deploy(), self.smoke(), rerun)
                self.assertEqual(result["smoke"], {"outcomes": {"failure": 1}, "unpaired_smoke_jobs": 1,
                                                   "unpaired_outcomes": {"incomplete_at_end": 1}})

    def test_unpaired_smoke_outside_the_window_is_not_counted(self):
        future = self.smoke(None, 12, 2, finished=None)
        future["created_at"] = "2026-10-16T00:00:00Z"
        future["started_at"] = None
        past = self.smoke("success", 13, 3, finished="2026-09-30T12:00:00Z")
        past["created_at"] = past["started_at"] = "2026-09-30T11:00:00Z"
        result = self.report(self.deploy(), self.smoke(), future, past)
        self.assertEqual(result["smoke"]["unpaired_outcomes"], {})
        self.assertEqual(result["smoke"]["unpaired_smoke_jobs"], 0)

    def test_smoke_must_match_run_as_well_as_attempt(self):
        data = snapshot(frontend=[run(1, self.deploy()), run(2, self.smoke("success", number=20))])
        result = module.analyze(data, SINCE, UNTIL)["results"]["frontend"]["smoke"]
        self.assertEqual(result, {"outcomes": {"missing": 1}, "unpaired_smoke_jobs": 1,
                                  "unpaired_outcomes": {"success": 1}})

    def test_each_successful_publish_attempt_gets_its_own_smoke(self):
        result = self.report(self.deploy(), self.smoke(), self.deploy(12, 2), self.smoke("success", 13, 2))
        self.assertEqual(result["smoke"]["outcomes"], {"failure": 1, "success": 1})

    def test_absent_skipped_and_unfinished_smoke_are_not_successes(self):
        for jobs, expected in [([self.deploy()], "missing"),
                               ([self.deploy(), self.smoke("skipped")], "skipped"),
                               ([self.deploy(), self.smoke(None, finished=None)], "incomplete")]:
            with self.subTest(expected=expected):
                self.assertEqual(self.report(*jobs)["smoke"]["outcomes"], {expected: 1})

    def test_smoke_finishing_after_window_end_is_incomplete(self):
        data = snapshot(frontend=[run(1, self.deploy(), self.smoke("success"))])
        report = module.analyze(data, SINCE, at("2026-10-02T10:03:00Z"))
        self.assertEqual(report["results"]["frontend"]["smoke"]["outcomes"], {"incomplete": 1})

    def test_out_of_order_smoke_is_not_accepted_as_verification(self):
        smoke = self.smoke("success")
        smoke["created_at"] = "2026-10-02T09:59:00Z"
        smoke["started_at"] = "2026-10-02T09:59:59Z"
        self.assertEqual(self.report(self.deploy(), smoke)["smoke"]["outcomes"], {"out_of_order": 1})


class CollectorTest(unittest.TestCase):
    def test_api_failures_are_not_swallowed_as_missing_pr_metadata(self):
        for message in ("HTTP 403: rate limit exceeded", "unexpected end of JSON input"):
            error = subprocess.CalledProcessError(1, ["gh", "api"], stderr=message)
            with self.subTest(message=message), patch.object(module.subprocess, "run", side_effect=error):
                with self.assertRaisesRegex(module.ReportError, message):
                    module.fetch_changes(REPO, {sha(1)}, CUTOFF)

    def test_gh_timeout_and_bad_json_fail_collection(self):
        for response in (subprocess.TimeoutExpired(["gh"], 180), subprocess.CompletedProcess([], 0, "not json")):
            with self.subTest(response=response):
                with patch.object(module.subprocess, "run") as api:
                    if isinstance(response, Exception):
                        api.side_effect = response
                    else:
                        api.return_value = response
                    with self.assertRaises(module.ReportError):
                        module.gh_json("path")

    def test_listing_checks_total_across_pages(self):
        pages = [{"total_count": 2, "jobs": [{"id": 1}]}, {"total_count": 2, "jobs": [{"id": 2}]}]
        with patch.object(module, "gh_json", return_value=pages) as api:
            self.assertEqual(len(module.listing("path", "jobs")), 2)
            api.assert_called_once_with("path", paginate=True)

    def test_truncated_changing_duplicate_or_missing_listings_fail_closed(self):
        cases = [[], [None], [[1]], [{"jobs": []}], [{"total_count": 1, "jobs": [None]}],
                 [{"total_count": 2, "jobs": [{"id": 1}]}],
                 [{"total_count": 1, "jobs": [{"id": 1}]}, {"total_count": 2, "jobs": []}],
                 [{"total_count": 2, "jobs": [{"id": 1}, {"id": 1}]}]]
        for pages in cases:
            with self.subTest(pages=pages), patch.object(module, "gh_json", return_value=pages):
                with self.assertRaises(module.ReportError):
                    module.listing("path", "jobs")

    def test_run_search_cap_is_an_explicit_error(self):
        with patch.object(module, "gh_json", return_value=[{"total_count": 1000, "workflow_runs": []}]):
            with self.assertRaisesRegex(module.ReportError, "1,000-run"):
                module.listing("path", "workflow_runs")

    def test_array_pagination_and_duplicate_commit_detection(self):
        with patch.object(module, "gh_json", return_value=[[{"sha": sha(1)}], [{"sha": sha(2)}]]):
            self.assertEqual(len(module.listing("path")), 2)
        with patch.object(module, "gh_json", return_value=[[{"sha": sha(1)}], [{"sha": sha(1)}]]):
            with self.assertRaises(module.ReportError):
                module.listing("path")

    def test_boundary_crossing_runs_and_all_reruns_are_collected_without_lower_bound(self):
        raw = run(1, job(10, "failure"), job(11, attempt=2))
        jobs = raw.pop("jobs")
        def api(path, paginate=False):
            if "/jobs?" in path:
                self.assertEqual(parse_qs(urlsplit(path).query)["filter"], ["all"])
                return [{"total_count": len(jobs), "jobs": jobs}]
            query = parse_qs(urlsplit(path).query)
            self.assertEqual(query["created"], ["<=" + module.iso(CUTOFF)])
            self.assertNotIn("..", query["created"][0])
            return [{"total_count": 1, "workflow_runs": [raw]}]
        with patch.object(module, "gh_json", side_effect=api):
            source = module.fetch_part(REPO, "backend", CUTOFF)
        self.assertEqual([j["id"] for j in source["runs"][0]["jobs"]], [10, 11])
        wide = snapshot(source["runs"])
        fresh = snapshot(source["runs"])
        fresh["window"][0] = "2026-10-02T00:00:00Z"
        self.assertEqual(module.analyze(wide, at("2026-10-02"), UNTIL), module.analyze(fresh, at("2026-10-02"), UNTIL))

    def test_collection_rejects_rerun_started_during_fetch(self):
        raw = run(1)
        with patch.object(module, "listing", side_effect=[[raw], [job(10, attempt=2)]]):
            with self.assertRaisesRegex(module.ReportError, "rerun during collection"):
                module.fetch_part(REPO, "backend", CUTOFF)

    def test_only_dev_rollback_job_is_retained_even_for_dispatch_on_dev_branch(self):
        raw = run(1, event="workflow_dispatch")
        jobs = [job(10, "skipped", name="rollback-existing-ec2"), job(11, name="rollback-existing-ec2-prod")]
        with patch.object(module, "listing", side_effect=[[raw], jobs]):
            source = module.fetch_part(REPO, "rollback", CUTOFF)
        self.assertEqual([j["id"] for j in source["runs"][0]["jobs"]], [10])
        self.assertEqual(module.deployments(source["runs"], "rollback-existing-ec2"), [])

    def test_unrelated_jobs_and_sensitive_run_fields_are_not_frozen(self):
        raw = dict(run(1), actor={"login": "private-person"}, display_title="private message")
        jobs = [dict(job(10), steps=[{"name": "private step"}]), job(11, name="package-dev")]
        with patch.object(module, "listing", side_effect=[[raw], jobs]):
            source = module.fetch_part(REPO, "backend", CUTOFF)
        text = json.dumps(source)
        for forbidden in ("private-person", "private message", "private step", "package-dev"):
            self.assertNotIn(forbidden, text)

    def pr(self, number=1, merge_sha=None, repo=REPO, branch="dev"):
        return {"id": number, "number": number, "merge_commit_sha": merge_sha or sha(1),
                "merged_at": "2026-10-01T00:00:00Z", "base": {"ref": branch, "repo": {"full_name": repo}}}

    def test_associated_but_not_exact_pr_is_not_attributed(self):
        prs = [self.pr(merge_sha=sha(2)), self.pr(2, repo="other/repo"), self.pr(3, branch="main")]
        with patch.object(module, "listing", return_value=prs), patch.object(module, "gh_json") as detail:
            result = module.fetch_changes(REPO, {sha(1)}, CUTOFF)
        self.assertEqual(result[sha(1)], {"status": "no_matching_merged_pr"})
        detail.assert_not_called()

    def test_ambiguous_exact_merged_prs_have_explicit_missing_reason(self):
        with patch.object(module, "listing", return_value=[self.pr(), self.pr(2)]):
            result = module.fetch_changes(REPO, {sha(1)}, CUTOFF)
        self.assertEqual(result[sha(1)]["status"], "ambiguous_merged_pr")

    def test_successful_empty_pr_lookup_is_not_an_api_error(self):
        with patch.object(module, "listing", return_value=[]):
            self.assertEqual(module.fetch_changes(REPO, {sha(1)}, CUTOFF)[sha(1)]["status"], "no_matching_merged_pr")

    def test_exact_pr_commit_inputs_are_retained_and_truncation_is_rejected(self):
        commits = [{"sha": sha(2), "commit": {"author": {"date": "2026-10-01T00:00:00Z"}}}]
        for count in (1, 2):
            with self.subTest(count=count), patch.object(module, "listing", side_effect=[[self.pr()], commits]), \
                    patch.object(module, "gh_json", return_value=dict(self.pr(), commits=count)):
                if count == 1:
                    result = module.fetch_changes(REPO, {sha(1)}, CUTOFF)
                    self.assertEqual(result[sha(1)]["commits"], [{"sha": sha(2), "author_date": "2026-10-01T00:00:00Z"}])
                else:
                    with self.assertRaisesRegex(module.ReportError, "incomplete"):
                        module.fetch_changes(REPO, {sha(1)}, CUTOFF)

    def test_ancestry_collection_keeps_negative_proof_and_earliest_containing_success(self):
        history = module.deployments([run(1, job(10, "failure")),
                   run(2, job(20, finished="2026-10-03T10:00:00Z")),
                   run(3, job(30, finished="2026-10-04T10:00:00Z"))], "deploy-existing-ec2")
        comparisons = [{"base_commit": {"sha": sha(1)}, "status": "diverged", "merge_base_commit": {"sha": sha(99)}},
                       {"base_commit": {"sha": sha(1)}, "status": "ahead", "merge_base_commit": {"sha": sha(1)}}]
        with patch.object(module, "gh_json", side_effect=comparisons) as api:
            ancestry = module.fetch_ancestry(REPO, [history])
        self.assertEqual(api.call_count, 2)
        self.assertFalse(module.contains(sha(1), sha(2), ancestry))
        self.assertTrue(module.contains(sha(1), sha(3), ancestry))

    def test_collection_binds_all_three_sources_and_cutoff_into_one_input(self):
        data = snapshot([run(1, job(10))])
        with patch.object(module, "fetch_part", side_effect=[data["sources"][p] for p in module.PARTS]) as parts, \
                patch.object(module, "fetch_changes", return_value=data["changes"]) as changes, \
                patch.object(module, "fetch_ancestry", return_value={}) as ancestry, \
                patch.object(module, "datetime", wraps=module.datetime) as clock:
            clock.now.return_value = at("2026-10-22")
            result = module.collect(REPO, SINCE, UNTIL, CUTOFF)
        module.validate_snapshot(result)
        self.assertEqual(parts.call_count, 3)
        changes.assert_called_once_with(REPO, {sha(1)}, CUTOFF)
        ancestry.assert_called_once()
        self.assertEqual(result["observed_at"], module.iso(CUTOFF))


class FrozenEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = SCRIPT.parents[2]
        cls.content = (cls.root / "docs/ci/evidence/dora-metrics-20261022-inputs.json").read_bytes()
        cls.data = module.decode(cls.content.decode("utf-8"))
        module.validate_snapshot(cls.data)

    def test_published_table_and_hash_reproduce_from_frozen_inputs(self):
        doc = (self.root / "docs/ci/dora-metrics-20261022.md").read_text(encoding="utf-8")
        report = module.analyze(self.data, at("2026-09-01"), at("2026-10-23"))
        self.assertIn(module.render(report).strip(), doc)
        self.assertIn(hashlib.sha256(self.content).hexdigest(), doc)
        parts = [report["results"][p] for p in ("backend", "frontend")]
        for label, unit, value in [
            ("Deployment frequency", "successful jobs/week", lambda r: r["per_week"]),
            ("Deployment lead time", "hours, median", lambda r: r["lead_time_h"]["median"]),
            ("Change success rate", "% success/failure jobs", lambda r: r["success_rate"] * 100),
        ]:
            self.assertIn(f"| {label} | {unit} | " + " / ".join(f"{value(r):.2f}" for r in parts) + " | not agreed |", doc)
        self.assertIn(f"next-success proxy {parts[0]['recovery_h']['mean']:.2f} (n={parts[0]['recovery_h']['n']}) / unavailable (n=0)", doc)

    def test_published_backend_subwindow_rows_reproduce_with_boundary_censoring(self):
        doc = (self.root / "docs/ci/dora-metrics-20261022.md").read_text(encoding="utf-8")
        windows = [("2026-09-05", "2026-09-18"), ("2026-09-18", "2026-10-23")]
        reports = [module.analyze(self.data, at(a), at(b)) for a, b in windows]
        parts = [r["results"]["backend"] for r in reports]
        rows = [
            ("Deploy jobs (successful / success+failure)", lambda r: f"{r['successful']} / {r['attempts']}"),
            ("Deploy-job frequency (successes/week)", lambda r: f"{r['per_week']:.2f}"),
            ("Lead-time proxy (median; p90; n)", lambda r:
             f"{r['lead_time_h']['median']:.2f} h; {r['lead_time_h']['p90']:.2f} h; {r['lead_time_h']['n']}"),
            ("Lead cohort (eligible; censored; missing; invalid)", lambda r:
             f"{r['lead_time_h']['eligible']}; {r['lead_time_h']['censored']}; {sum(r['lead_time_h']['missing'].values())}; {r['lead_time_h']['invalid_timestamp']}"),
            ("Deploy-job success rate (proxy)", lambda r: f"{100 * r['success_rate']:.2f} %"),
            ("Failure-to-next-success proxy (median; mean; n)", lambda r: "n/a (n=0)" if not r["recovery_h"]["n"] else
             f"{r['recovery_h']['median']:.2f} h; {r['recovery_h']['mean']:.2f} h; {r['recovery_h']['n']}"),
            ("Failure episodes (opened; censored; carried in)", lambda r:
             f"{r['recovery_h']['episodes']}; {r['recovery_h']['censored']}; {r['recovery_h']['carried_in']}"),
        ]
        for label, value in rows:
            self.assertIn(f"| {label} | " + " | ".join(value(r) for r in parts) + " |", doc)
        self.assertIn("| Observed days | " + " | ".join(f"{r['observed_days']:.6f}" for r in reports) + " |", doc)

    def test_historical_failed_and_successful_reruns_are_retained(self):
        runs = {r["id"]: r for r in self.data["sources"]["backend"]["runs"]}
        jobs = [j for j in runs[33947729656]["jobs"] if j["name"] == "deploy-existing-ec2"]
        self.assertEqual([j["id"] for j in jobs], [101257463620, 101261306004, 101263177027, 101264897763])
        self.assertEqual([j["conclusion"] for j in jobs], ["failure", "failure", "failure", "success"])
        self.assertEqual([j["run_attempt"] for j in runs[34134151091]["jobs"] if j["conclusion"] == "success"], [1, 2])

    def test_historical_publish_success_does_not_hide_failed_smoke(self):
        runs = [r for r in self.data["sources"]["frontend"]["runs"] if r["id"] == 37182996807]
        deploys = module.deployments(runs, "deploy-s3-dev")
        self.assertEqual([d["job_id"] for d in deploys if d["ok"]], [111379262779])
        self.assertEqual(module.smoke_outcomes(runs, deploys, at("2026-09-01"), at("2026-10-23"))["outcomes"], {"failure": 1})

    def test_deployment_counts_partition_but_lead_samples_do_not_duplicate(self):
        whole = module.analyze(self.data, at("2026-09-01"), at("2026-10-23"))["results"]
        early = module.analyze(self.data, at("2026-09-01"), at("2026-09-18"))["results"]
        late = module.analyze(self.data, at("2026-09-18"), at("2026-10-23"))["results"]
        for part in ("backend", "frontend"):
            for key in ("attempts", "successful", "failed"):
                self.assertEqual(whole[part][key], early[part][key] + late[part][key])
            samples = early[part]["lead_time_h"]["samples"] + late[part]["lead_time_h"]["samples"]
            self.assertEqual(len(samples), len({s["sha"] for s in samples}))

    def test_only_sanitized_source_fields_and_dev_rollback_jobs_are_retained(self):
        run_keys = {"id", "head_sha", "created_at", "run_attempt", "event", "head_branch", "jobs"}
        job_keys = {"id", "name", "run_attempt", "status", "conclusion", "created_at", "started_at", "completed_at"}
        for part, source in self.data["sources"].items():
            for run in source["runs"]:
                self.assertEqual(set(run), run_keys)
                for job in run["jobs"]:
                    self.assertEqual(set(job), job_keys)
                    if part == "rollback":
                        self.assertEqual(job["name"], "rollback-existing-ec2")
        for change in self.data["changes"].values():
            self.assertLessEqual(set(change), {"status", "number", "merged_at", "commits_count", "commits"})
            for commit in change.get("commits", []):
                self.assertEqual(set(commit), {"sha", "author_date"})

    def test_byte_hash_is_protected_from_checkout_line_ending_conversion(self):
        self.assertNotIn(b"\r", self.content)
        attributes = (self.root / ".gitattributes").read_text(encoding="utf-8")
        self.assertIn("/docs/ci/evidence/dora-metrics-*-inputs.json text eol=lf", attributes)

    def test_source_clock_anomalies_remain_visible_without_rewriting_raw_times(self):
        result = module.analyze(self.data, at("2026-09-01"), at("2026-10-23"))["results"]
        self.assertEqual(result["backend"]["timestamp_anomalies"], 2)
        self.assertEqual(result["frontend"]["timestamp_anomalies"], 11)
        for source in self.data["sources"].values():
            for run in source["runs"]:
                for job in run["jobs"]:
                    if not module.ordered_times(job):
                        self.assertIn(job["conclusion"], ("skipped", "cancelled"))


class SnapshotAndCliTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "inputs.json"
        self.data = snapshot([run(1, job(10))])
        self.path.write_bytes(module.snapshot_bytes(self.data))

    def cli(self, *extra, repo=REPO, since="2026-10-01", until="2026-10-15"):
        output, error = io.StringIO(), io.StringIO()
        with redirect_stdout(output), redirect_stderr(error), patch.object(module, "datetime", wraps=module.datetime) as clock:
            clock.now.return_value = at("2026-10-22")
            code = module.main([repo, since, until, "--snapshot", str(self.path), *extra])
        return code, output.getvalue(), error.getvalue()

    def test_valid_snapshot_replays_with_zero_github_calls_and_without_rewriting(self):
        before = self.path.read_bytes()
        with patch.object(module, "gh_json", side_effect=AssertionError("must be offline")), \
                patch.object(module, "collect", side_effect=AssertionError("must not refetch")):
            code, text, error = self.cli("--json")
        self.assertEqual((code, error), (0, ""))
        self.assertEqual(json.loads(text)["input_sha256"], hashlib.sha256(before).hexdigest())
        self.assertEqual(before, self.path.read_bytes())

    def test_real_cli_subprocess_replays_without_gh_on_path(self):
        environment = dict(os.environ, PATH="", PYTHONDONTWRITEBYTECODE="1")
        result = subprocess.run([sys.executable, "-B", str(SCRIPT), REPO, "2026-10-01", "2026-10-15",
                                 "--snapshot", str(self.path), "--json"], capture_output=True,
                                text=True, encoding="utf-8", env=environment)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["results"]["backend"]["successful"], 1)

    def test_cache_identity_includes_repository_schema_window_and_cutoff(self):
        for kwargs, extra in [({"repo": "foreign/repo"}, ()), ({"since": "2026-09-30"}, ()),
                              ({"until": "2026-10-17"}, ()), ({}, ("--as-of", "2026-10-15T00:00:00Z"))]:
            with self.subTest(kwargs=kwargs, extra=extra), patch.object(module, "collect") as collect:
                code, output, error = self.cli(*extra, **kwargs)
                self.assertEqual((code, output), (1, ""))
                self.assertIn("mismatch", error)
                collect.assert_not_called()
        self.data["schema_version"] = 1
        with self.assertRaisesRegex(module.ReportError, "Unsupported"):
            module.validate_snapshot(self.data)

    def test_wide_snapshot_covers_subwindow_but_not_foreign_repo(self):
        self.assertTrue(module.covers(self.data, REPO.upper(), at("2026-10-02"), UNTIL))
        self.assertFalse(module.covers(self.data, "other/repo", SINCE, UNTIL))

    def test_legacy_corrupt_or_incomplete_cache_fails_without_network_or_output(self):
        for content in (b"{bad", b'{"window": []}', b'{"schema_version": 2, "schema_version": 2}',
                        module.snapshot_bytes(dict(self.data, complete=False))):
            with self.subTest(content=content[:40]):
                self.path.write_bytes(content)
                with patch.object(module, "collect") as collect:
                    code, output, error = self.cli()
                self.assertEqual((code, output), (1, ""))
                self.assertTrue(error)
                collect.assert_not_called()
                self.assertEqual(self.path.read_bytes(), content)

    def test_invalid_dates_and_naive_cutoffs_fail_before_any_network(self):
        cases = [({"since": "2026-10-15"}, ()), ({"since": "2026-10-16"}, ()),
                 ({"since": "2026-1-01"}, ()), ({"since": "2026-02-30"}, ()),
                 ({"since": "2026-10-01T01:00:00"}, ()), ({}, ("--as-of", "2026-10-16T00:00:00"))]
        for kwargs, extra in cases:
            with self.subTest(kwargs=kwargs, extra=extra), patch.object(module, "collect") as collect:
                code, output, _ = self.cli(*extra, **kwargs)
                self.assertEqual((code, output), (1, ""))
                collect.assert_not_called()

    def test_future_cutoff_is_rejected_before_collection(self):
        with patch.object(module, "collect") as collect:
            code, output, error = self.cli("--refresh", "--as-of", "3000-10-16T00:00:00Z")
        self.assertEqual((code, output), (1, ""))
        self.assertIn("not in the future", error)
        collect.assert_not_called()

    def test_missing_snapshot_is_collected_and_atomically_created(self):
        self.path.unlink()
        with patch.object(module, "collect", return_value=self.data) as collect:
            code, text, error = self.cli("--as-of", "2026-10-16T00:00:00Z")
        self.assertEqual((code, error), (0, ""))
        self.assertEqual(self.path.read_bytes(), module.snapshot_bytes(self.data))
        self.assertIn("Input SHA-256:", text)
        collect.assert_called_once()

    def test_failed_refresh_preserves_original_snapshot_atomically(self):
        before = self.path.read_bytes()
        with patch.object(module, "collect", side_effect=module.ReportError("HTTP 403")):
            code, output, error = self.cli("--refresh", "--as-of", "2026-10-16T00:00:00Z")
        self.assertEqual((code, output), (1, ""))
        self.assertIn("HTTP 403", error)
        self.assertEqual(self.path.read_bytes(), before)

    def test_invalid_collected_inputs_are_not_written(self):
        before = self.path.read_bytes()
        with patch.object(module, "collect", return_value=dict(self.data, complete=False)):
            code, output, _ = self.cli("--refresh", "--as-of", "2026-10-16T00:00:00Z")
        self.assertEqual((code, output), (1, ""))
        self.assertEqual(self.path.read_bytes(), before)

    def test_missing_ancestry_during_analysis_does_not_replace_inputs(self):
        before = self.path.read_bytes()
        data = snapshot([run(1, job(10, "failure")), run(2, job(20, finished="2026-10-03T00:00:00Z"))])
        with patch.object(module, "collect", return_value=data):
            code, output, error = self.cli("--refresh", "--as-of", "2026-10-16T00:00:00Z")
        self.assertEqual((code, output), (1, ""))
        self.assertIn("Missing ancestry", error)
        self.assertEqual(self.path.read_bytes(), before)

    def test_successful_refresh_is_validated_and_frozen_before_output(self):
        with patch.object(module, "collect", return_value=self.data):
            code, text, error = self.cli("--refresh", "--as-of", "2026-10-16T00:00:00Z")
        self.assertEqual((code, error), (0, ""))
        self.assertIn(hashlib.sha256(self.path.read_bytes()).hexdigest(), text)
        module.validate_snapshot(module.decode(self.path.read_text(encoding="utf-8")))

    def test_new_snapshot_appearing_during_collection_is_not_mistaken_for_saved_inputs(self):
        self.path.unlink()
        other_content = b"created by another writer"
        def collect(*args):
            self.path.write_bytes(other_content)
            return self.data
        with patch.object(module, "collect", side_effect=collect):
            code, output, error = self.cli("--as-of", "2026-10-16T00:00:00Z", "--json")
        self.assertEqual((code, output), (1, ""))
        self.assertIn("changed during collection", error)
        self.assertEqual(self.path.read_bytes(), other_content)

    def test_refresh_rechecks_existing_input_bytes_before_publication(self):
        for concurrent in (b"edited by another writer", None):
            with self.subTest(concurrent=concurrent):
                self.path.write_bytes(module.snapshot_bytes(self.data))
                def collect(*args):
                    if concurrent is None:
                        self.path.unlink()
                    else:
                        self.path.write_bytes(concurrent)
                    return self.data
                with patch.object(module, "collect", side_effect=collect):
                    code, output, error = self.cli("--refresh", "--as-of", "2026-10-16T00:00:00Z")
                self.assertEqual((code, output), (1, ""))
                self.assertIn("changed during collection", error)
                self.assertEqual(self.path.read_bytes() if self.path.exists() else None, concurrent)

    def test_replay_does_not_recreate_an_input_deleted_during_analysis(self):
        analyze = module.analyze
        def remove_and_analyze(*args):
            self.path.unlink()
            return analyze(*args)
        with patch.object(module, "analyze", side_effect=remove_and_analyze), patch.object(module, "atomic_write") as write:
            code, output, error = self.cli("--json")
        self.assertEqual((code, error), (0, ""))
        self.assertEqual(json.loads(output)["results"]["backend"]["successful"], 1)
        self.assertFalse(self.path.exists())
        write.assert_not_called()

    def test_report_serialization_failure_does_not_replace_a_snapshot(self):
        before = self.path.read_bytes()
        collected = snapshot([run(2, job(20))])
        report = module.analyze(collected, SINCE, UNTIL)
        report["invalid"] = float("nan")
        with patch.object(module, "collect", return_value=collected), patch.object(module, "analyze", return_value=report):
            code, output, error = self.cli("--refresh", "--as-of", "2026-10-16T00:00:00Z", "--json")
        self.assertEqual((code, output), (1, ""))
        self.assertIn("Out of range float", error)
        self.assertEqual(self.path.read_bytes(), before)

    def test_atomic_replace_failure_cleans_temporary_and_keeps_old_file(self):
        before = self.path.read_bytes()
        with patch.object(module.os, "replace", side_effect=OSError("write denied")):
            with self.assertRaises(OSError):
                module.atomic_write(self.path, b"replacement")
        self.assertEqual(self.path.read_bytes(), before)
        self.assertEqual(list(self.path.parent.iterdir()), [self.path])

    def test_dora_cache_environment_alias_replays_offline(self):
        output = io.StringIO()
        with patch.dict(os.environ, {"DORA_CACHE": str(self.path)}), redirect_stdout(output), \
                patch.object(module, "collect", side_effect=AssertionError("offline")):
            self.assertEqual(module.main([REPO, "2026-10-01", "2026-10-15"]), 0)

    def test_snapshot_validation_rejects_duplicate_ids_and_ambiguous_attempt_jobs(self):
        cases = [lambda d: d["sources"]["backend"]["runs"].append(deepcopy(d["sources"]["backend"]["runs"][0])),
                 lambda d: d["sources"]["backend"]["runs"][0]["jobs"].append(job(10)),
                 lambda d: d["sources"]["backend"]["runs"][0]["jobs"].append(job(11)),
                 lambda d: d["changes"].clear()]
        for mutate in cases:
            with self.subTest(mutate=mutate):
                data = deepcopy(self.data)
                mutate(data)
                source = data["sources"]["backend"]
                source["runs_count"] = len(source["runs"])
                source["all_jobs_count"] = sum(len(r["jobs"]) for r in source["runs"])
                source["selected_jobs_count"] = source["all_jobs_count"]
                with self.assertRaises(module.ReportError):
                    module.validate_snapshot(data)

    def test_snapshot_validation_rejects_job_scope_timestamps_and_state_corruption(self):
        for key, value in (("name", "rollback-existing-ec2-prod"), ("completed_at", "2026-09-01T00:00:00Z"),
                           ("status", "in_progress"), ("run_attempt", 2), ("id", 0)):
            with self.subTest(key=key):
                data = deepcopy(self.data)
                data["sources"]["backend"]["runs"][0]["jobs"][0][key] = value
                with self.assertRaises(module.ReportError):
                    module.validate_snapshot(data)

    def test_timezone_offsets_are_normalized_not_relabelled(self):
        self.assertEqual(module.parse_time("2026-10-01T08:00:00+08:00"), SINCE)

    def test_non_finite_json_and_partial_frozen_pr_commits_are_rejected(self):
        with self.assertRaisesRegex(module.ReportError, "Non-finite"):
            module.decode('{"value": NaN}')
        self.data["changes"][sha(1)]["commits_count"] = 2
        with self.assertRaisesRegex(module.ReportError, "incomplete PR"):
            module.validate_snapshot(self.data)

    def test_missing_frozen_job_record_is_not_an_empty_history(self):
        self.data["sources"]["backend"]["runs"][0]["jobs"].clear()
        with self.assertRaisesRegex(module.ReportError, "Incomplete selected-job"):
            module.validate_snapshot(self.data)

    def test_completed_success_and_failure_jobs_require_start_timestamps(self):
        for name in ("deploy-existing-ec2", "e2e-dev"):
            for conclusion in ("success", "failure"):
                for missing in (None, ""):
                    with self.subTest(name=name, conclusion=conclusion, missing=missing):
                        selected = job(10, conclusion, name=name)
                        selected["started_at"] = missing
                        data = snapshot([run(1, selected)]) if name == "deploy-existing-ec2" else snapshot(frontend=[run(1, selected)])
                        with self.assertRaisesRegex(module.ReportError, "Missing start timestamp"):
                            module.validate_snapshot(data)

    def test_snapshot_history_scope_must_describe_all_attempts(self):
        for scope in (None, "latest attempt only"):
            with self.subTest(scope=scope):
                self.data["history_scope"] = scope
                with self.assertRaisesRegex(module.ReportError, "history scope"):
                    module.validate_snapshot(self.data)


if __name__ == "__main__":
    unittest.main()
