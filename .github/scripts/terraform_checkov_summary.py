"""Summarize advisory Terraform findings without accepting a broken/empty scan."""

import json
from pathlib import Path
import sys


def markdown(value):
    return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def summarize(report):
    if not isinstance(report, dict) or report.get("check_type") != "terraform":
        raise ValueError("Expected one Terraform Checkov report")
    summary = report["summary"]
    results = report["results"]
    if summary["parsing_errors"] or results.get("parsing_errors"):
        raise ValueError("Checkov reported Terraform parsing errors")
    if summary["resource_count"] <= 0:
        raise ValueError("Checkov did not scan any resources")
    for status in ("passed", "failed", "skipped"):
        checks = results[f"{status}_checks"]
        if not isinstance(checks, list) or summary[status] != len(checks):
            raise ValueError(f"Invalid Checkov {status} checks/count")
    if sum(summary[status] for status in ("passed", "failed", "skipped")) == 0:
        raise ValueError("Checkov did not evaluate any policies")
    lines = [
        "## Terraform Checkov (advisory policy findings)",
        "",
        f"Resources: {summary['resource_count']}; passed: {summary['passed']}; "
        f"failed: {summary['failed']}; skipped: {summary['skipped']}.",
        "",
        "Policy findings do not block this job. Scanner/report failures do. "
        "See infra/terraform/README.md for the dated disposition inventory.",
        "",
        "| Status | Check | Resource | Finding / suppression reason |",
        "|---|---|---|---|",
    ]
    for status in ("failed", "skipped"):
        for check in results[f"{status}_checks"]:
            reason = check["check_name"]
            if status == "skipped":
                reason += ": " + check.get("suppress_comment", "No reason supplied")
            lines.append(
                "| " + " | ".join(map(markdown, (
                    status, check["check_id"], check["resource"], reason
                ))) + " |"
            )
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    try:
        print(summarize(json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))), end="")
    except (ValueError, KeyError, TypeError, OSError, IndexError) as error:
        sys.exit(f"Invalid Checkov report: {error}")
