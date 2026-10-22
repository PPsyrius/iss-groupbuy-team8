#!/usr/bin/env python3
"""Measure the four DORA metrics of the dev environment from GitHub Actions history.

Definitions (SWE5006 DevSecOps 16, Business Metrics, p. 3), applied to this repository:

- Deployment: a deploy job that ran to success or failure on a push to dev. Backend:
  `deploy-existing-ec2` in ci-backend.yml (EC2 via SSM, with automatic rollback on a failed
  health check). Frontend: `deploy-s3-dev` in ci-frontend.yml.
- Deployment frequency: successful deployments per week.
- Deployment lead time: first commit of the merged pull request to the end of the first successful
  deployment of that part at or after the pull request's own deployment attempt.
- Change success rate: successful deployments over all deployments; manual `rollback-backend`
  runs count as failed backend changes.
- Mean time to recover: from a failed deployment to the next successful deployment of that part.

Reads with `gh api` (needs `gh` signed in, or GH_TOKEN in Actions); the computation is pure and
covered by .github/tests/dora_metrics_test.py.

Usage: dora-metrics.py OWNER/REPO SINCE UNTIL   (dates as YYYY-MM-DD, UTC, UNTIL exclusive)
"""

from datetime import datetime, timedelta, timezone
import json
import os
import statistics
import subprocess
import sys

PARTS = {
    "backend": ("ci-backend.yml", "deploy-existing-ec2"),
    "frontend": ("ci-frontend.yml", "deploy-s3-dev"),
}


def parse_time(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def deployments(runs, job_name):
    """Deployment attempts from push runs with their jobs: [{sha, finished, ok}], oldest first."""
    found = []
    for run in runs:
        for job in run.get("jobs", []):
            if job.get("name") != job_name or job.get("conclusion") not in ("success", "failure"):
                continue
            found.append({
                "sha": run["head_sha"],
                "finished": parse_time(job["completed_at"]),
                "ok": job["conclusion"] == "success",
            })
    return sorted(found, key=lambda d: d["finished"])


def frequency_per_week(deploys, since, until):
    weeks = max((until - since).total_seconds() / timedelta(weeks=1).total_seconds(), 1 / 7)
    return sum(1 for d in deploys if d["ok"]) / weeks


def lead_times(deploys, first_commit_at):
    """Hours from a change's first commit to the first successful deployment that carries it.

    `first_commit_at` maps the merge commit of a pull request to the time of its first commit.
    """
    hours = []
    for attempt in deploys:
        started = first_commit_at.get(attempt["sha"])
        if started is None:
            continue
        shipped = next((d for d in deploys if d["ok"] and d["finished"] >= attempt["finished"]), None)
        if shipped is not None:
            hours.append((shipped["finished"] - started).total_seconds() / 3600)
    return hours


def change_success_rate(deploys, rollbacks=0):
    attempts = len(deploys) + rollbacks
    if attempts == 0:
        return None
    return sum(1 for d in deploys if d["ok"]) / attempts


def recovery_times(deploys):
    """Hours from each failed deployment to the next successful one; unrecovered failures skipped."""
    hours, failed_at = [], None
    for d in deploys:
        if not d["ok"] and failed_at is None:
            failed_at = d["finished"]
        elif d["ok"] and failed_at is not None:
            hours.append((d["finished"] - failed_at).total_seconds() / 3600)
            failed_at = None
    return hours


def summary(values):
    if not values:
        return {"n": 0, "median": None, "p90": None}
    ordered = sorted(values)
    p90 = ordered[min(len(ordered) - 1, round(0.9 * (len(ordered) - 1)))]
    return {"n": len(values), "median": statistics.median(ordered), "p90": p90}


def metrics(deploys, first_commit_at, since, until, rollbacks=0):
    return {
        "deployments": len(deploys),
        "successful": sum(1 for d in deploys if d["ok"]),
        "per_week": frequency_per_week(deploys, since, until),
        "lead_time_h": summary(lead_times(deploys, first_commit_at)),
        "success_rate": change_success_rate(deploys, rollbacks),
        "recovery_h": summary(recovery_times(deploys)),
    }


def gh_lines(path, jq):
    out = subprocess.run(["gh", "api", "--paginate", path, "--jq", jq],
                         check=True, capture_output=True, text=True, encoding="utf-8").stdout
    return [line for line in out.splitlines() if line.strip()]


def fetch_part(repo, workflow, since, until):
    runs = []
    for line in gh_lines(f"repos/{repo}/actions/workflows/{workflow}/runs?branch=dev&event=push&per_page=100"
                         f"&created={since:%Y-%m-%d}..{until - timedelta(days=1):%Y-%m-%d}",
                         ".workflow_runs[] | tojson"):
        run = json.loads(line)
        run["jobs"] = [json.loads(j) for j in gh_lines(f"repos/{repo}/actions/runs/{run['id']}/jobs?per_page=100",
                                                         ".jobs[] | tojson")]
        runs.append(run)
    return runs


def fetch_first_commits(repo, shas):
    """Merge commit -> first commit time of its pull request; commits without one are skipped."""
    first = {}
    for sha in shas:
        try:
            prs = [json.loads(p) for p in gh_lines(f"repos/{repo}/commits/{sha}/pulls", ".[] | tojson")]
        except subprocess.CalledProcessError:
            continue  # e.g. a direct push or a commit GitHub no longer associates with a pull request
        merged = [p for p in prs if p.get("merged_at")]
        if not merged:
            continue
        dates = gh_lines(f"repos/{repo}/pulls/{merged[0]['number']}/commits?per_page=100",
                         ".[].commit.author.date")
        if dates:
            first[sha] = min(parse_time(d) for d in dates)
    return first


def render(results, since, until):
    def hours(s):
        return "n/a" if s["median"] is None else f"{s['median']:.1f} h (p90 {s['p90']:.1f} h, n={s['n']})"

    rows = ["| Metric | " + " | ".join(results) + " |", "|---|" + "---|" * len(results)]
    rows.append("| Deployments (successful / all) | "
                + " | ".join(f"{r['successful']} / {r['deployments']}" for r in results.values()) + " |")
    rows.append("| Deployment frequency | " + " | ".join(f"{r['per_week']:.1f} per week" for r in results.values()) + " |")
    rows.append("| Deployment lead time (median) | " + " | ".join(hours(r["lead_time_h"]) for r in results.values()) + " |")
    rows.append("| Change success rate | " + " | ".join(
        "n/a" if r["success_rate"] is None else f"{100 * r['success_rate']:.0f} %" for r in results.values()) + " |")
    rows.append("| Mean time to recover (median) | " + " | ".join(hours(r["recovery_h"]) for r in results.values()) + " |")
    return f"Window: {since:%Y-%m-%d} to {until - timedelta(days=1):%Y-%m-%d} (UTC)\n\n" + "\n".join(rows) + "\n"


def main(repo, since_text, until_text):
    since = datetime.fromisoformat(since_text).replace(tzinfo=timezone.utc)
    until = datetime.fromisoformat(until_text).replace(tzinfo=timezone.utc)
    rollbacks = len(gh_lines(f"repos/{repo}/actions/workflows/rollback-backend.yml/runs?per_page=100"
                             f"&created={since:%Y-%m-%d}..{until - timedelta(days=1):%Y-%m-%d}",
                             ".workflow_runs[] | select(.conclusion == \"success\") | .id"))
    # DORA_CACHE=<file> keeps the fetched history, so a re-run does not repeat hundreds of API calls.
    cache_path = os.environ.get("DORA_CACHE")
    cache = json.load(open(cache_path, encoding="utf-8")) if cache_path and os.path.exists(cache_path) else {}
    results = {}
    for part, (workflow, job) in PARTS.items():
        if part not in cache:
            runs = fetch_part(repo, workflow, since, until)
            deploys = deployments(runs, job)
            first = fetch_first_commits(repo, {d["sha"] for d in deploys})
            cache[part] = {"runs": runs, "first": {sha: t.isoformat() for sha, t in first.items()}}
        deploys = deployments(cache[part]["runs"], job)
        first = {sha: parse_time(t) for sha, t in cache[part]["first"].items()}
        results[part] = metrics(deploys, first, since, until, rollbacks if part == "backend" else 0)
    if cache_path:
        json.dump(cache, open(cache_path, "w", encoding="utf-8"))
    print(render(results, since, until))
    print(f"Manual backend rollbacks in the window: {rollbacks}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
