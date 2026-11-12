"""Private additive correction; never read raw samples or rewrite frozen evidence."""
import ast
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
METRIC = "jvm_gc_pause_seconds_max"
OUTPUT = ROOT / "backoff-v3-gc-max-correction.json"
CORRECTED_ANALYZER = ROOT / "analyze-backoff-v3-corrected.py"
if OUTPUT.exists() or CORRECTED_ANALYZER.exists():
    raise SystemExit("Correction output already exists; refusing replacement or blind repeat")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def aggregate(metrics, name, corrected):
    values = [value for key, value in metrics.items()
              if key == name or key.startswith(name + "{")]
    if not values:
        return None
    return max(values) if corrected and name.endswith("_max") else sum(values)


def bracket(rows, corrected):
    metrics = [row["metrics"] for row in rows if "metrics" in row]
    assert metrics, "No successful metric scrapes"
    values = [aggregate(row, METRIC, corrected) for row in metrics]
    return {"first": values[0], "last": values[-1],
            "max": max((value for value in values if value is not None), default=None)}


index_path = ROOT / "backoff-v3-index.json"
analysis_path = ROOT / "backoff-v3-analysis.json"
analyzer_path = ROOT / "analyze-backoff-v3.py"
index = json.loads(index_path.read_text())
analysis = json.loads(analysis_path.read_text())
by_label = {case["label"]: case for case in analysis["cases"]}
assert len(by_label) == len(index["runs"]) == 16
inputs = [index_path, analysis_path, analyzer_path]
for run in index["runs"]:
    folder = ROOT / run["label"]
    inputs.extend(folder / name for name in
                  ("monitor.jsonl", "audit.json", "summary.json", "exit.txt"))
before = {str(path.relative_to(ROOT)): sha(path) for path in inputs}
cases = []
for run in index["runs"]:
    label = run["label"]
    folder = ROOT / label
    case = by_label[label]
    audit = json.loads((folder / "audit.json").read_text())
    report = json.loads((folder / "summary.json").read_text())
    metrics = report["summary"]["metrics"]
    rows = [json.loads(line) for line in (folder / "monitor.jsonl").read_text().splitlines()]
    old = bracket(rows, False)
    corrected = bracket(rows, True)
    assert old == case["gauges"][METRIC] == audit["gauges"][METRIC], label
    assert audit == case, label
    counts = case["rawCounts"]
    assert counts["browse_requests"] == metrics["browse_requests"]["values"]["count"], label
    assert counts.get("dropped_iterations", 0) == metrics["dropped_iterations"]["values"]["count"], label
    assert abs(counts["browse_errors"] - metrics["browse_errors"]["values"]["rate"] * counts["browse_requests"]) < 1e-5, label
    for stat, value in case["latencyMs"].items():
        assert value == metrics["browse_latency"]["values"][stat], (label, stat)
    exit_code = int((folder / "exit.txt").read_text())
    threshold_results = {name: {rule: result["ok"] for rule, result in metric.get("thresholds", {}).items()}
                         for name, metric in metrics.items() if metric.get("thresholds")}
    assert threshold_results, label
    passed = all(ok for rules in threshold_results.values() for ok in rules.values())
    assert exit_code == run["exitCode"] == case["exitCode"], label
    assert passed == case["allDiagnosticScreensPassed"] == (exit_code == 0), label
    amended = copy.deepcopy(case)
    amended["gauges"][METRIC] = corrected
    original_without_metric = copy.deepcopy(case)
    amended_without_metric = copy.deepcopy(amended)
    del original_without_metric["gauges"][METRIC]
    del amended_without_metric["gauges"][METRIC]
    assert original_without_metric == amended_without_metric, label
    cases.append({"label": label, "monitorSha256": before[f"{label}/monitor.jsonl"],
                  "monitorSamples": len(rows),
                  "successfulMetricScrapes": sum("metrics" in row for row in rows),
                  "oldReportedSeconds": old, "correctedSeconds": corrected,
                  "httpEvidenceUnchanged": {"rawCounts": counts, "latencyMs": case["latencyMs"],
                                            "exitCode": exit_code, "allDiagnosticScreensPassed": passed,
                                            "thresholdResults": threshold_results}})

# Create a future analyzer copy, but do not execute it or rescan samples.jsonl.
source = analyzer_path.read_text()
old_line = "    return sum(values) if values else None"
new_line = "    return (max(values) if name.endswith('_max') else sum(values)) if values else None"
assert source.count(old_line) == 1
future = source.replace(old_line, new_line)
old_case_output = "    (folder/'audit.json').write_text(json.dumps(result,indent=2));results.append(result)"
new_case_output = "    with (folder/'audit-gc-max-corrected.json').open('x') as output_file: output_file.write(json.dumps(result,indent=2))\n    results.append(result)"
old_analysis_output = "(ROOT/f'{kind}-analysis.json').write_text(json.dumps(output,indent=2))"
new_analysis_output = "with (ROOT/f'{kind}-analysis-gc-max-corrected.json').open('x') as output_file: output_file.write(json.dumps(output,indent=2))"
assert source.count(old_case_output) == source.count(old_analysis_output) == 1
future = future.replace(old_case_output, new_case_output).replace(old_analysis_output, new_analysis_output)
future = ("# PRIVATE COPY: *_max series aggregate by max; all other series still sum.\n"
          "# Not executed by the correction receipt. Outputs are separate, exclusive-create files.\n" + future)
tree = ast.parse(future)
function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "gauge")
namespace = {}
exec(compile(ast.Module(body=[function], type_ignores=[]), str(CORRECTED_ANALYZER), "exec"), namespace)
gauge = namespace["gauge"]
assert gauge({'test_max{label="a"}': 2, 'test_max{label="b"}': 3}, "test_max") == 3
assert gauge({'test_sum{label="a"}': 2, 'test_sum{label="b"}': 3}, "test_sum") == 5
assert gauge({"test_max": 2}, "test_max") == 2
assert gauge({}, "test_max") is None
assert gauge({"test_max_other": 9}, "test_max") is None
with CORRECTED_ANALYZER.open("x", encoding="utf-8", newline="\n") as stream:
    stream.write(future)

after = {str(path.relative_to(ROOT)): sha(path) for path in inputs}
assert before == after, "Frozen input changed during correction"
receipt = {
    "kind": "private-additive-backoff-v3-gc-max-correction",
    "evidenceKind": "local-diagnostic-not-AWS-acceptance",
    "createdAt": datetime.now(timezone.utc).isoformat(),
    "metric": METRIC, "unit": "seconds",
    "correction": "At each successful scrape, take max across matching label series, not sum; then preserve the original first/last successful-scrape boundaries and take max across those scrape aggregates.",
    "limits": "These are sampled, potentially rolling/expiring timer-max gauges, not exact individual GC-pause durations or complete phase maxima. Scrape boundaries, missing readings and pre-phase retained maxima remain unchanged. No GC-log or JFR reinterpretation; no raw HTTP stream rescan.",
    "sourceAnalysis": {"path": analysis_path.name, "sha256Before": before[analysis_path.name],
                       "sha256After": after[analysis_path.name], "byteIdentical": True},
    "originalInputsSha256": before,
    "assertions": {"allFrozenInputsByteIdentical": True, "onlyGcMaxFieldDiffersInAmendedCases": True,
                   "httpCountsLatenciesAndGatesMatchExistingSummaryAuditAndAnalysis": True,
                   "noRawSamplesRescanned": True, "noOriginalEvidenceOverwritten": True,
                   "futureAnalyzerParsedAndFiveFocusedGaugeAssertionsPassed": True,
                   "futureAnalyzerNotExecuted": True},
    "futureAnalyzer": {"path": CORRECTED_ANALYZER.name, "sha256": sha(CORRECTED_ANALYZER),
                       "behaviorChange": "Use max only for names ending in _max; retain sum for every other series.",
                       "outputSafety": "Separate filenames with exclusive creation; original audit.json and analysis.json are never written."},
    "caseCount": len(cases),
    "changedCaseCount": sum(case["oldReportedSeconds"] != case["correctedSeconds"] for case in cases),
    "cases": cases,
}
with OUTPUT.open("x", encoding="utf-8", newline="\n") as stream:
    json.dump(receipt, stream, indent=2)
    stream.write("\n")
print(json.dumps({"receipt": OUTPUT.name, "receiptSha256": sha(OUTPUT),
                  "caseCount": len(cases), "changedCaseCount": receipt["changedCaseCount"],
                  "originalAnalysisSha256": before[analysis_path.name], "allAssertionsPassed": True}))
for case in cases:
    print(case["label"], "old", case["oldReportedSeconds"], "corrected", case["correctedSeconds"])
