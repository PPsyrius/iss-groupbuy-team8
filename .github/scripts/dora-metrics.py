#!/usr/bin/env python3
"""Manual, report-only dev delivery proxies from retained GitHub Actions history.

All windows are [SINCE, UNTIL), UTC, clipped to the frozen observation cutoff.
Deploy-job frequency and success rate count completed success/failure jobs (including
reruns), not unique changes or verified application health. Other outcomes are separate.
Lead time has one sample per head SHA whose first success/failure completion is in the
window: earliest author timestamp of its exact merged PR -> first successful deploy job
carrying that SHA (equality or recorded ancestry), before the window end. This is a
deployment-attempt cohort, not every merged PR; prior-window changes are not resampled.
Recovery is first failed job -> next successful job in a consecutive failure episode,
with open episodes censored at the window end and carried-in episodes shown separately.
Neither proxy establishes commit-to-production time, incident MTTR or service downtime.
Frontend smoke jobs match by run AND attempt; rollbacks never enter the success denominator.

Usage: dora-metrics.py OWNER/REPO SINCE UNTIL --snapshot FILE [--refresh] [--json]
Dates: YYYY-MM-DD. Existing snapshots replay offline; DORA_CACHE is a path alias.
Collection uses gh api, fails on incomplete listings/API errors, and retains only IDs,
timestamps, job outcomes, exact PR commit metadata and necessary ancestry comparisons.
"""

import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys
import tempfile
from urllib.parse import urlencode

SCHEMA = 2
PARTS = {
    "backend": ("ci-backend.yml", "deploy-existing-ec2"),
    "frontend": ("ci-frontend.yml", "deploy-s3-dev"),
    "rollback": ("rollback-backend.yml", "rollback-existing-ec2"),
}
SHA = re.compile(r"[0-9a-f]{40}")
HISTORY_SCOPE = "all retained runs created at or before observed_at; all job attempts"


class ReportError(ValueError):
    """Inputs are unavailable, inconsistent or incomplete; do not publish a report."""


def require(condition, message):
    if not condition:
        raise ReportError(message)


def parse_time(value):
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        require(result.utcoffset() is not None, "Timestamp needs an explicit timezone")
        return result.astimezone(timezone.utc)
    except (AttributeError, TypeError, ValueError) as error:
        raise ReportError(f"Invalid timestamp: {value!r}") from error


def iso(value):
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def job_times(job):
    created = parse_time(job["created_at"])
    start = parse_time(job["started_at"]) if job["started_at"] else created
    finish = parse_time(job["completed_at"]) if job["completed_at"] else None
    return created, start, finish


def ordered_times(job):
    created, start, finish = job_times(job)
    return created <= start and (finish is None or start <= finish)


def parse_date(value):
    require(bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", value)), "Dates must be YYYY-MM-DD")
    return parse_time(value + "T00:00:00Z")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def decode(text):
    def invalid_constant(value):
        raise ReportError(f"Non-finite JSON number: {value}")

    return json.loads(text, object_pairs_hook=unique_object, parse_constant=invalid_constant)


def gh_json(path, paginate=False):
    command = ["gh", "api", path]
    if paginate:
        command += ["--paginate", "--slurp"]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True,
                                encoding="utf-8", timeout=180)
        return decode(result.stdout)
    except subprocess.CalledProcessError as error:
        raise ReportError(f"GitHub request failed: {path}: {error.stderr.strip()}") from error
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError) as error:
        raise ReportError(f"Cannot read GitHub response: {path}: {error}") from error


def listing(path, key=None):
    """Check pagination totals and identities, including GitHub's 1,000-run search cap."""
    pages = gh_json(path, paginate=True)
    require(isinstance(pages, list) and pages, f"Missing pages: {path}")
    if key:
        require(all(isinstance(page, dict) for page in pages), f"Expected object pages: {path}")
        totals = [page.get("total_count") for page in pages]
        require(all(type(n) is int and n >= 0 for n in totals), f"Missing total_count: {path}")
        require(len(set(totals)) == 1, f"Listing changed during pagination: {path}")
        if key == "workflow_runs":
            require(totals[0] < 1000, "GitHub's 1,000-run search cap reached; collection aborted")
        require(all(isinstance(page.get(key), list) for page in pages), f"Missing {key}: {path}")
        rows = [row for page in pages for row in page[key]]
        require(len(rows) == totals[0], f"Truncated listing: {path}")
    else:
        require(all(isinstance(page, list) for page in pages), f"Expected array pages: {path}")
        rows = [row for page in pages for row in page]
    require(all(isinstance(row, dict) for row in rows), f"Expected objects in listing: {path}")
    ids = [row.get("id", row.get("sha")) for row in rows]
    require(None not in ids and len(ids) == len(set(ids)), f"Missing/duplicate identities: {path}")
    return rows


def fetch_part(repo, part, cutoff):
    workflow, job_name = PARTS[part]
    query = {"event": "workflow_dispatch" if part == "rollback" else "push",
             "created": "<=" + iso(cutoff), "per_page": 100}
    if part != "rollback":
        query["branch"] = "dev"
    # No lower creation bound: arbitrarily old runs may finish or be rerun in the window.
    raw_runs = listing(f"repos/{repo}/actions/workflows/{workflow}/runs?{urlencode(query)}", "workflow_runs")
    runs, job_count = [], 0
    for run in sorted(raw_runs, key=lambda r: r["id"]):
        require(run["event"] == query["event"] and
                (part == "rollback" or run["head_branch"] == "dev"), "Unexpected run scope")
        require(parse_time(run["created_at"]) <= cutoff, "Run created after observation cutoff")
        jobs = listing(f"repos/{repo}/actions/runs/{run['id']}/jobs?filter=all&per_page=100", "jobs")
        require(all(job["run_attempt"] <= run["run_attempt"] for job in jobs),
                f"Run {run['id']} was rerun during collection; collect again")
        job_count += len(jobs)
        names = {job_name, "e2e-dev"} if part == "frontend" else {job_name}
        selected = [{key: job[key] for key in ("id", "name", "run_attempt", "status", "conclusion",
                                               "created_at", "started_at", "completed_at")}
                    for job in jobs if job["name"] in names]
        runs.append({**{key: run[key] for key in ("id", "head_sha", "created_at", "run_attempt",
                                                 "event", "head_branch")},
                     "jobs": sorted(selected, key=lambda j: j["id"])})
    return {"workflow": workflow, "all_jobs_count": job_count, "runs_count": len(runs),
            "selected_jobs_count": sum(len(r["jobs"]) for r in runs), "runs": runs}


def fetch_changes(repo, shas, cutoff):
    changes = {}
    for sha in sorted(shas):
        prs = listing(f"repos/{repo}/commits/{sha}/pulls?per_page=100")
        matches = [pr for pr in prs if pr.get("merge_commit_sha") == sha and pr.get("merged_at")
                   and parse_time(pr["merged_at"]) < cutoff and pr["base"]["ref"] == "dev"
                   and pr["base"]["repo"]["full_name"].lower() == repo.lower()]
        if len(matches) != 1:
            changes[sha] = {"status": "no_matching_merged_pr" if not matches else "ambiguous_merged_pr"}
            continue
        number = matches[0]["number"]
        pr = gh_json(f"repos/{repo}/pulls/{number}")
        require(pr.get("number") == number and pr.get("merge_commit_sha") == sha and pr.get("merged_at")
                and pr["base"]["ref"] == "dev" and pr["base"]["repo"]["full_name"].lower() == repo.lower(),
                "PR identity changed")
        commits = listing(f"repos/{repo}/pulls/{number}/commits?per_page=100")
        require(len(commits) == pr["commits"] and commits,
                f"PR #{number} commit listing incomplete (GitHub limits it to 250)")
        changes[sha] = {"status": "matched", "number": number, "merged_at": pr["merged_at"], "commits_count": len(commits),
                        "commits": [{"sha": c["sha"], "author_date": c["commit"]["author"]["date"]}
                                    for c in commits]}
    return changes


def deployments(runs, job_name):
    found = []
    for run in runs:
        for job in run["jobs"]:
            if job["name"] == job_name and job["conclusion"] in ("success", "failure"):
                found.append({"sha": run["head_sha"], "run_id": run["id"], "job_id": job["id"],
                              "attempt": job["run_attempt"], "finished": parse_time(job["completed_at"]),
                              "ok": job["conclusion"] == "success"})
    return sorted(found, key=lambda d: (d["finished"], d["job_id"]))


def contains(base, head, ancestry):
    if base == head:
        return True
    key = base + "/" + head
    require(key in ancestry, f"Missing ancestry evidence: {key}")
    evidence = ancestry[key]
    return evidence["status"] in ("ahead", "identical") and evidence["merge_base_sha"] == base


def fetch_ancestry(repo, histories):
    ancestry = {}
    for deploys in histories:
        first = {}
        for deploy in deploys:
            first.setdefault(deploy["sha"], deploy["finished"])
        for sha, finished in sorted(first.items()):
            for candidate in (d for d in deploys if d["ok"] and d["finished"] >= finished):
                head = candidate["sha"]
                key = sha + "/" + head
                if sha != head and key not in ancestry:
                    comparison = gh_json(f"repos/{repo}/compare/{sha}...{head}?per_page=1")
                    require(comparison["base_commit"]["sha"] == sha, "Unexpected comparison base")
                    ancestry[key] = {"status": comparison["status"],
                                     "merge_base_sha": comparison["merge_base_commit"]["sha"]}
                if contains(sha, head, ancestry):
                    break
    return ancestry


def collect(repo, since, until, cutoff):
    started = datetime.now(timezone.utc)
    sources = {part: fetch_part(repo, part, cutoff) for part in PARTS}
    histories = [[d for d in deployments(sources[part]["runs"], PARTS[part][1]) if d["finished"] < cutoff]
                 for part in ("backend", "frontend")]
    changes = fetch_changes(repo, {d["sha"] for history in histories for d in history}, cutoff)
    ancestry = fetch_ancestry(repo, histories)
    return {"schema_version": SCHEMA, "repo": repo, "branch": "dev", "complete": True,
            "window": [iso(since), iso(until)], "observed_at": iso(cutoff),
            "collection_started_at": iso(started), "collection_completed_at": iso(datetime.now(timezone.utc)),
            "history_scope": HISTORY_SCOPE,
            "sources": sources, "changes": changes, "ancestry": ancestry}


def validate_snapshot(data):
    """Reject old caches and malformed/partial inputs rather than silently refetching."""
    try:
        require(type(data["schema_version"]) is int and data["schema_version"] == SCHEMA
                and data["complete"] is True, "Unsupported/incomplete snapshot")
        require(data["branch"] == "dev" and set(data["sources"]) == set(PARTS), "Invalid snapshot scope")
        require(data["history_scope"] == HISTORY_SCOPE, "Invalid snapshot history scope")
        require(bool(re.fullmatch(r"[\w.-]+/[\w.-]+", data["repo"])), "Invalid repository")
        require(isinstance(data["window"], list) and len(data["window"]) == 2, "Invalid declared window")
        since, until = map(parse_time, data["window"])
        cutoff = parse_time(data["observed_at"])
        started, completed = (parse_time(data[key]) for key in ("collection_started_at", "collection_completed_at"))
        require(since < until and since < cutoff <= started <= completed, "Invalid window/cutoff/collection dates")
        run_ids, job_ids, needed_shas = set(), set(), set()
        for part, (workflow, job_name) in PARTS.items():
            source = data["sources"][part]
            require(source["workflow"] == workflow and type(source["runs_count"]) is int
                    and source["runs_count"] == len(source["runs"]),
                    "Invalid source workflow/run count")
            selected = sum(len(r["jobs"]) for r in source["runs"])
            require(type(source["selected_jobs_count"]) is int and source["selected_jobs_count"] == selected,
                    "Incomplete selected-job records")
            require(type(source["all_jobs_count"]) is int and
                    source["all_jobs_count"] >= selected, "Invalid job count")
            for run in source["runs"]:
                require(type(run["id"]) is int and run["id"] > 0 and run["id"] not in run_ids, "Duplicate/invalid run ID")
                run_ids.add(run["id"])
                require(bool(SHA.fullmatch(run["head_sha"])), "Invalid head SHA")
                require(type(run["run_attempt"]) is int and run["run_attempt"] > 0, "Invalid run attempt")
                require(parse_time(run["created_at"]) <= cutoff, "Run after cutoff")
                require(run["event"] == ("workflow_dispatch" if part == "rollback" else "push") and
                        (part == "rollback" or run["head_branch"] == "dev"), "Invalid run event/branch")
                identities = set()
                for job in run["jobs"]:
                    require(type(job["id"]) is int and job["id"] > 0 and job["id"] not in job_ids, "Duplicate/invalid job ID")
                    job_ids.add(job["id"])
                    require(job["name"] in ({job_name, "e2e-dev"} if part == "frontend" else {job_name}), "Unexpected job")
                    require(type(job["run_attempt"]) is int and 0 < job["run_attempt"] <= run["run_attempt"], "Invalid job attempt")
                    identity = (job["name"], job["run_attempt"])
                    require(identity not in identities, "Ambiguous job name within a run attempt")
                    identities.add(identity)
                    created, start, finish = job_times(job)
                    # GitHub returns one-second inversions for some skipped/cancelled jobs.
                    # Preserve and count them, but never accept inversions in duration samples.
                    require(ordered_times(job) or (job["conclusion"] in ("skipped", "cancelled")
                            and created - start <= timedelta(seconds=1) and finish is not None
                            and start - finish <= timedelta(seconds=1)), "Invalid job timestamps")
                    require(isinstance(job["status"], str) and bool(job["status"]) and
                            (job["conclusion"] is None or isinstance(job["conclusion"], str)), "Invalid job status")
                    terminal = job["status"] == "completed"
                    require((terminal and bool(job["conclusion"]) and finish is not None) or
                            (not terminal and job["conclusion"] is None and finish is None), "Incomplete/inconsistent job state")
                    require(job["conclusion"] not in ("success", "failure") or bool(job["started_at"]),
                            "Missing start timestamp for success/failure job")
                    if part != "rollback" and job["name"] == job_name and job["conclusion"] in ("success", "failure") and finish < cutoff:
                        needed_shas.add(run["head_sha"])
        require(needed_shas == set(data["changes"]), "Missing/unexpected change metadata")
        for sha, change in data["changes"].items():
            require(bool(SHA.fullmatch(sha)), "Invalid change SHA")
            require(change["status"] in ("matched", "no_matching_merged_pr", "ambiguous_merged_pr"), "Invalid change status")
            if change["status"] == "matched":
                require(type(change["number"]) is int and change["number"] > 0 and change["commits"]
                        and type(change["commits_count"]) is int and change["commits_count"] == len(change["commits"]),
                        "Invalid/incomplete PR metadata")
                require(parse_time(change["merged_at"]) < cutoff, "PR merged after cutoff")
                ids = [commit["sha"] for commit in change["commits"]]
                require(len(ids) == len(set(ids)) and all(SHA.fullmatch(s) for s in ids), "Invalid PR commits")
                for commit in change["commits"]:
                    if commit["author_date"] is not None:
                        parse_time(commit["author_date"])
        for key, evidence in data["ancestry"].items():
            require(bool(re.fullmatch(r"[0-9a-f]{40}/[0-9a-f]{40}", key)), "Invalid ancestry identity")
            require(evidence["status"] in ("ahead", "behind", "identical", "diverged") and
                    bool(SHA.fullmatch(evidence["merge_base_sha"])), "Invalid ancestry evidence")
    except (KeyError, TypeError, AttributeError) as error:
        raise ReportError(f"Malformed snapshot: {error}") from error


def covers(data, repo, since, until, cutoff=None):
    return (data["schema_version"] == SCHEMA and data["repo"].lower() == repo.lower()
            and parse_time(data["window"][0]) <= since < until <= parse_time(data["window"][1])
            and (cutoff is None or cutoff == parse_time(data["observed_at"])))


def in_window(deploys, since, until):
    return [d for d in deploys if since <= d["finished"] < until]


def summary(values):
    if not values:
        return {"n": 0, "mean": None, "median": None, "p90": None}
    ordered = sorted(values)
    return {"n": len(values), "mean": statistics.mean(values), "median": statistics.median(values),
            "p90": ordered[math.ceil(0.9 * len(values)) - 1]}


def lead_times(deploys, changes, ancestry, since, until):
    first, samples, missing = {}, [], Counter()
    censored = invalid = 0
    for deploy in deploys:
        if deploy["finished"] < until:
            first.setdefault(deploy["sha"], deploy)
    cohort = [d for d in first.values() if d["finished"] >= since]
    for attempt in cohort:
        sha = attempt["sha"]
        change = changes[sha]
        if change["status"] != "matched":
            missing[change["status"]] += 1
            continue
        dates = [parse_time(c["author_date"]) for c in change["commits"] if c["author_date"] is not None]
        if len(dates) != len(change["commits"]):
            missing["missing_author_date"] += 1
            continue
        shipped = next((d for d in deploys if d["ok"] and attempt["finished"] <= d["finished"] < until
                        and contains(sha, d["sha"], ancestry)), None)
        if shipped is None:
            censored += 1
        elif shipped["finished"] < min(dates):
            invalid += 1
        else:
            samples.append({"sha": sha, "pr": change["number"], "run_id": shipped["run_id"],
                            "job_id": shipped["job_id"], "hours": (shipped["finished"] - min(dates)).total_seconds() / 3600})
    return {**summary([s["hours"] for s in samples]), "eligible": len(cohort), "censored": censored,
            "missing": dict(missing), "invalid_timestamp": invalid, "samples": samples}


def recovery_times(deploys, since, until):
    episodes, opened = [], None
    for deploy in (d for d in deploys if d["finished"] < until):
        if not deploy["ok"] and opened is None:
            opened = deploy
        elif deploy["ok"] and opened is not None:
            episodes.append((opened, deploy))
            opened = None
    if opened is not None:
        episodes.append((opened, None))
    eligible = [(a, b) for a, b in episodes if a["finished"] >= since]
    carried = [(a, b) for a, b in episodes if a["finished"] < since and (b is None or b["finished"] >= since)]
    return {**summary([(b["finished"] - a["finished"]).total_seconds() / 3600 for a, b in eligible if b]),
            "episodes": len(eligible), "censored": sum(b is None for _, b in eligible), "carried_in": len(carried)}


def window_outcome(job, since, until):
    """Completed outcomes use completion time; pending records use existence at the end."""
    finished = parse_time(job["completed_at"]) if job["completed_at"] else None
    if finished is not None and finished < until:
        return job["conclusion"] if finished >= since else None
    return "incomplete_at_end" if parse_time(job["created_at"]) < until else None


def outcome_counts(runs, name, since, until):
    excluded = Counter()
    for run in runs:
        for job in (j for j in run["jobs"] if j["name"] == name):
            outcome = window_outcome(job, since, until)
            if outcome is not None and outcome not in ("success", "failure"):
                excluded[outcome] += 1
    return dict(sorted(excluded.items()))


def smoke_outcomes(runs, successful, since, until):
    counts = Counter()
    jobs = {(r["id"], j["run_attempt"]): j for r in runs for j in r["jobs"] if j["name"] == "e2e-dev"}
    all_deploys = deployments(runs, PARTS["frontend"][1])
    paired = {(d["run_id"], d["attempt"]) for d in all_deploys if d["ok"] and d["finished"] < until}
    for deploy in successful:
        job = jobs.get((deploy["run_id"], deploy["attempt"]))
        if job is None:
            outcome = "missing"
        elif not job["completed_at"] or parse_time(job["completed_at"]) >= until:
            outcome = "incomplete" if parse_time(job["created_at"]) < until else "missing"
        elif job["conclusion"] in ("success", "failure") and job["started_at"] and parse_time(job["started_at"]) < deploy["finished"]:
            outcome = "out_of_order"
        else:
            outcome = job["conclusion"]
        counts[outcome] += 1
    unpaired = Counter()
    for key, job in jobs.items():
        if key not in paired:
            outcome = window_outcome(job, since, until)
            if outcome is not None:
                unpaired[outcome] += 1
    return {"outcomes": dict(sorted(counts.items())), "unpaired_smoke_jobs": sum(unpaired.values()),
            "unpaired_outcomes": dict(sorted(unpaired.items()))}


def analyze(data, since, until):
    end = min(until, parse_time(data["observed_at"]))
    require(since < end, "Window has no observed time")
    results = {}
    for part in ("backend", "frontend", "rollback"):
        runs = data["sources"][part]["runs"]
        all_deploys = deployments(runs, PARTS[part][1])
        deploys = in_window(all_deploys, since, end)
        successful = [d for d in deploys if d["ok"]]
        result = {"attempts": len(deploys), "successful": len(successful), "failed": len(deploys) - len(successful),
                  "other_outcomes": outcome_counts(runs, PARTS[part][1], since, end),
                  "timestamp_anomalies": sum(not ordered_times(j) and j["completed_at"] is not None
                      and since <= parse_time(j["completed_at"]) < end for r in runs for j in r["jobs"]),
                  "runs_without_job": sum(since <= parse_time(r["created_at"]) < end and not any(
                      j["name"] == PARTS[part][1] and parse_time(j["created_at"]) < end for j in r["jobs"]) for r in runs)}
        if part != "rollback":
            result.update(per_week=len(successful) * timedelta(weeks=1).total_seconds() / (end - since).total_seconds(),
                          success_rate=len(successful) / len(deploys) if deploys else None,
                          lead_time_h=lead_times(all_deploys, data["changes"], data["ancestry"], since, end),
                          recovery_h=recovery_times(all_deploys, since, end))
        if part == "frontend":
            result["smoke"] = smoke_outcomes(runs, successful, since, end)
        results[part] = result
    return {"repo": data["repo"], "window": [iso(since), iso(until)],
            "effective_until": iso(end), "observed_at": data["observed_at"],
            "observed_days": (end - since).total_seconds() / 86400, "results": results}


def render(report):
    def hours(value):
        return ("n/a (n=0)" if not value["n"] else
                f"{value['median']:.2f} h (mean {value['mean']:.2f}, p90 {value['p90']:.2f}, n={value['n']})")

    def counts(value):
        return ", ".join(f"{k}={v}" for k, v in value.items()) or "none"

    results = report["results"]
    parts = [results[p] for p in ("backend", "frontend")]
    rows = ["| Metric | Backend | Frontend |", "|---|---|---|"]
    metrics = [
        ("Deploy jobs (successful / success+failure)", lambda r: f"{r['successful']} / {r['attempts']}"),
        ("Deploy-job frequency (successes/week)", lambda r: f"{r['per_week']:.2f}"),
        ("Lead-time proxy (median)", lambda r: hours(r["lead_time_h"])),
        ("Lead cohort (eligible; censored; missing; invalid)", lambda r:
         f"{r['lead_time_h']['eligible']}; {r['lead_time_h']['censored']}; {sum(r['lead_time_h']['missing'].values())}; {r['lead_time_h']['invalid_timestamp']}"),
        ("Missing lead metadata", lambda r: counts(r["lead_time_h"]["missing"])),
        ("Deploy-job success rate (proxy)", lambda r: "n/a" if r["success_rate"] is None else f"{r['success_rate'] * 100:.2f} %"),
        ("Failure-to-next-success proxy (median)", lambda r: hours(r["recovery_h"])),
        ("Failure episodes (opened; censored; carried in)", lambda r:
         f"{r['recovery_h']['episodes']}; {r['recovery_h']['censored']}; {r['recovery_h']['carried_in']}"),
        ("Other deploy-job outcomes (outside rate)", lambda r: counts(r["other_outcomes"])),
        ("Selected-job timestamp anomalies (outside durations)", lambda r: str(r["timestamp_anomalies"])),
        ("Runs without a deploy-job record", lambda r: str(r["runs_without_job"])),
    ]
    rows += [f"| {name} | " + " | ".join(value(r) for r in parts) + " |" for name, value in metrics]
    rollback = results["rollback"]
    header = (f"Repository: {report['repo']}\n\n"
              f"Requested UTC window: [{report['window'][0]}, {report['window'][1]})\n\n"
              f"Observed through {report['effective_until']} (exclusive; {report['observed_days']:.6f} days). "
              f"Frozen cutoff: {report['observed_at']}.\n\n")
    footer = (f"\nFrontend post-deploy smoke: {counts(results['frontend']['smoke']['outcomes'])}; "
              f"unpaired smoke jobs={results['frontend']['smoke']['unpaired_smoke_jobs']} "
              f"({counts(results['frontend']['smoke']['unpaired_outcomes'])}).\n\n"
              f"Unattributed dev rollback jobs: success={rollback['successful']}, failure={rollback['failed']}; "
              f"other outcomes: {counts(rollback['other_outcomes'])}. Not added to deploy-job rates.\n")
    return header + "\n".join(rows) + "\n" + footer


def snapshot_bytes(data):
    return (json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def atomic_write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as output:
            temporary = Path(output.name)
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo")
    parser.add_argument("since")
    parser.add_argument("until")
    parser.add_argument("--snapshot", default=os.environ.get("DORA_CACHE"))
    parser.add_argument("--refresh", action="store_true", help="Explicitly collect/replace inputs (read-only GitHub requests)")
    parser.add_argument("--as-of", help="Timezone-aware observation cutoff; must match an existing snapshot")
    parser.add_argument("--json", action="store_true", help="Machine-readable results, including lead samples")
    args = parser.parse_args(argv)
    try:
        require(bool(re.fullmatch(r"[\w.-]+/[\w.-]+", args.repo)), "Repository must be OWNER/REPO")
        since, until = parse_date(args.since), parse_date(args.until)
        require(since < until, "SINCE must precede UNTIL")
        cutoff = parse_time(args.as_of) if args.as_of else None
        path = Path(args.snapshot) if args.snapshot else None
        previous = path.read_bytes() if path and path.exists() else None
        collecting = previous is None or args.refresh
        if not collecting:
            content = previous
            data = decode(content.decode("utf-8"))
            validate_snapshot(data)
            require(covers(data, args.repo, since, until, cutoff),
                    "Snapshot repository/window/cutoff mismatch; use a different file or explicit --refresh")
        else:
            cutoff = cutoff or datetime.now(timezone.utc).replace(microsecond=0)
            require(since < cutoff <= datetime.now(timezone.utc), "Cutoff must be after SINCE and not in the future")
            data = collect(args.repo, since, until, cutoff)
            validate_snapshot(data)
            content = snapshot_bytes(data)
        report = analyze(data, since, until)
        report["input_sha256"] = hashlib.sha256(content).hexdigest()
        output = (json.dumps(report, indent=2, allow_nan=False) if args.json else
                  render(report) + f"\nInput SHA-256: {report['input_sha256']}")
        # Replays never write, even if the input disappears while being analyzed.
        # Validate, compute and format before publication; recheck long-running collections.
        if path and collecting:
            current = path.read_bytes() if path.exists() else None
            require(current == previous, "Snapshot changed during collection; refusing to publish or overwrite it")
            atomic_write(path, content)
        print(output)
        if path is None:
            print("Warning: inputs were not retained; pass --snapshot FILE for offline replay.", file=sys.stderr)
        return 0
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        print(f"dora-metrics: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
