"""Post-run retention only; never launches load or changes existing containers."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, json, re, sys

ROOT = Path('/results/matrix-v2')
DEST = Path('/evidence')
PREFIX = re.compile(r'codec-(?:base|candidate)-[ab]')
BASE = '37c7c7332b5d40e5c940241e75a180d9cfb2b6df020663b233008e61ec7e2960'
CANDIDATE = '0cdf2416f7784331542f21d977d80d9c9208863965d4948716ab2b9814090bff'

def load(path):
    return json.loads(path.read_text())

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x') as output:
        output.write(json.dumps(value, indent=2) + '\n')

def copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open('xb') as output:
        output.write(source.read_bytes())

def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

index = load(ROOT / 'codec-startup-index.json')
prefixes = set()
for run in index.get('runs', []):
    name = run.get('app', '').removeprefix('blocbuy-matrix2-')
    if not PREFIX.fullmatch(name):
        raise ValueError('Unexpected app prefix in index')
    prefixes.add(name)
for key in index:
    if key.endswith('Startup') and PREFIX.fullmatch(key[:-7]):
        prefixes.add(key[:-7])
order = ['codec-base-a', 'codec-candidate-a', 'codec-candidate-b', 'codec-base-b']
prefixes = [name for name in order if name in prefixes]
assert index.get('artifactHashes', {}).get('commerce-baseline.jar') == BASE, 'Baseline attribution mismatch'
assert index.get('artifactHashes', {}).get('commerce-cache-codec.jar') == CANDIDATE, 'Candidate attribution mismatch'
assert index.get('expectedCandidateHash') == CANDIDATE, 'Expected candidate receipt mismatch'

if sys.argv[1] == 'prepare':
    outputs = [ROOT / 'codec-startup-analysis.json']
    outputs += [ROOT / run['label'] / 'audit.json' for run in index.get('runs', [])]
    outputs += [ROOT / f'{prefix}{suffix}' for prefix in prefixes for suffix in ('-windows.txt', '-jfr-analysis.json', '-cold-jfr-analysis.json')]
    if any(path.exists() for path in outputs):
        raise FileExistsError('Post-run receipt/analysis already exists; refusing overwrite')
    original = (ROOT / 'JfrColdSummary.java').read_text()
    adapted = (ROOT / 'JfrCodecSummary.java').read_text()
    assert adapted == original.replace('backoff-(base|v3)-[ab]', 'codec-(base|candidate)-[ab]'), 'Codec helper changed beyond accepted prefix regex'
    issues = []
    ready = []
    for prefix in prefixes:
        runs = [run for run in index.get('runs', []) if run.get('app') == f'blocbuy-matrix2-{prefix}']
        windows = []
        for run in runs:
            if not run.get('startedAt') or not run.get('finishedAt'):
                issues.append({'label': run['label'], 'problem': 'Missing actual phase timestamps'})
                continue
            windows.append('|'.join((run['label'], run['startedAt'], run['finishedAt'])))
        with (ROOT / f'{prefix}-windows.txt').open('x') as output:
            output.write('\n'.join(windows) + ('\n' if windows else ''))
        recording = ROOT / f'{prefix}.jfr'
        if windows and recording.exists() and recording.stat().st_size:
            ready.append(prefix)
        else:
            issues.append({'prefix': prefix, 'problem': 'No usable phase windows or JFR recording; not zero contention'})
    with (ROOT / 'codec-startup-jfr-prefixes.txt').open('x') as output:
        output.write('\n'.join(ready) + ('\n' if ready else ''))
    write(ROOT / 'codec-startup-postrun-preparation.json', {'kind': 'codec-startup', 'prefixes': prefixes, 'jfrReady': ready, 'issues': issues})
    print(json.dumps({'prefixes': prefixes, 'jfrReady': ready, 'issues': issues}))
    sys.exit(0)

if sys.argv[1] != 'retain':
    raise ValueError('Expected prepare or retain')
if DEST.exists() and any(DEST.iterdir()):
    raise FileExistsError('Evidence directory is not new/empty; refusing overwrite')
DEST.mkdir(parents=True, exist_ok=True)
issues = list(load(ROOT / 'codec-startup-postrun-preparation.json')['issues'])
postrun_path = ROOT / 'codec-startup-postrun-exits.json'
if postrun_path.exists():
    postrun = load(postrun_path)
    copy(postrun_path, DEST / 'postrun-exits.json')
    if postrun.get('analyzer') != 0:
        issues.append({'problem': 'Independent analyzer exited unsuccessfully', 'exitCode': postrun.get('analyzer')})
    issues.extend({'problem': 'JFR helper exited unsuccessfully', **item} for item in postrun.get('jfr', []) if item.get('exitCode') != 0)
else:
    issues.append({'problem': 'Missing post-run process exit receipt'})
analysis_file = ROOT / 'codec-startup-analysis.json'
analysis = load(analysis_file) if analysis_file.exists() else None
if analysis:
    copy(analysis_file, DEST / 'analysis.json')
else:
    issues.append({'problem': 'Independent audit did not produce analysis; no successful audit inferred'})
receipts = {key: index.get(key) for key in ('kind', 'evidenceKind', 'startedAt', 'finishedAt', 'completed', 'conditions', 'artifactHashes', 'expectedCandidateHash', 'refusedFaultCases', 'problems')}
receipts['startup'] = {key: value for key, value in index.items() if key.endswith('Startup')}
receipts['cases'] = [{key: run.get(key) for key in ('label', 'app', 'rate', 'seconds', 'startedAt', 'finishedAt', 'exitCode', 'result', 'actualLimits', 'actions', 'problem')} for run in index.get('runs', [])]
write(DEST / 'receipts.json', receipts)
for name in ('run-codec-startup.mjs', 'analyze-codec-startup.py', 'driver-resources.js', 'JfrFailureSummary.java', 'JfrCodecSummary.java', 'retain-codec-startup.py', 'post-codec-startup.ps1'):
    copy(ROOT / name, DEST / 'scripts' / name)
case_paths = sorted(path for path in ROOT.glob('codec-*') if path.is_dir() and re.fullmatch(r'codec-(?:base|candidate)-[ab]-(?:warmup|control|flush|slow|down|recovery)', path.name))
for folder in case_paths:
    for name in ('summary.json', 'exit.txt', 'actions.jsonl', 'audit.json'):
        path = folder / name
        if path.exists():
            copy(path, DEST / 'cases' / folder.name / name)
        elif name != 'actions.jsonl':
            issues.append({'label': folder.name, 'problem': f'Missing {name}; retained as partial'})
    path = folder / 'context.json'
    if path.exists():
        context = load(path)
        write(DEST / 'cases' / folder.name / 'context.json', {key: context[key] for key in ('label', 'app', 'rate', 'seconds', 'port', 'startedAt', 'actions', 'actualLimits', 'hostMemory') if key in context})
    else:
        issues.append({'label': folder.name, 'problem': 'Missing context.json'})
jfr = {}
for prefix in prefixes:
    for suffix in ('-windows.txt', '-jfr-analysis.json', '-cold-jfr-analysis.json'):
        path = ROOT / f'{prefix}{suffix}'
        if path.exists():
            copy(path, DEST / path.name)
        else:
            issues.append({'prefix': prefix, 'problem': f'Missing {suffix}; not interpreted as zero'})
    path = ROOT / f'{prefix}-cold-jfr-analysis.json'
    if path.exists():
        phases = load(path).get('phases', {})
        first = phases.get('cold-00to05')
        if first is None:
            issues.append({'prefix': prefix, 'problem': 'No first-five-second JFR bracket; not zero contention'})
        jfr[prefix] = {
            'first5SecondsPresent': first is not None,
            'first5SecondsExecutionSamples': first.get('executionSamples') if first else None,
            'first5SecondsJacksonInclusiveMonitorDurationNanos': first.get('monitorInclusiveCategoryDurationNanos', {}).get('json', 0) if first else None,
            'first5SecondsTopMonitorStacks': first.get('monitorDurationNanosByStack') if first else None,
            'coldPhases': {name: {key: value.get(key) for key in ('executionSamples', 'monitorInclusiveCategoryDurationNanos', 'cpuInclusiveCategories', 'compilation', 'gc')} for name, value in phases.items() if name.startswith('cold-')},
        }
raw_paths = {ROOT / 'codec-startup-index.json', ROOT / 'codec-startup-postrun-preparation.json', ROOT / 'codec-startup-postrun-exits.json', ROOT.parent / 'commerce-baseline.jar', ROOT.parent / 'commerce-cache-codec.jar', ROOT.parent / 'cache-codec-artifact-sha256.txt'}
for prefix in prefixes:
    raw_paths.update(path for path in ROOT.glob(f'{prefix}*') if path.is_file())
for folder in case_paths:
    raw_paths.update(path for path in folder.iterdir() if path.is_file())
private = []
for path in sorted(raw_paths):
    if path.exists():
        private.append({'path': '/results/' + str(path.relative_to(ROOT.parent)), 'bytes': path.stat().st_size, 'sha256': digest(path), 'rawBodiesCopied': False})
write(DEST / 'private-artifacts.json', {'kind': 'external-private-artifact-hashes', 'files': private, 'limits': 'Hashes identify retained private sources; raw samples, recordings, logs, properties, environment files and TLS keys are not copied.'})
outcomes = []
by_label = {item['label']: item for item in (analysis or {}).get('cases', [])}
for run in index.get('runs', []):
    result = run.get('result') or {}
    audited = by_label.get(run['label'])
    outcomes.append({'label': run['label'], 'exitCode': run.get('exitCode'), 'requests': result.get('requests'), 'errorRate': result.get('errors', {}).get('rate'), 'drops': result.get('drops'), 'p95Ms': result.get('latency', {}).get('p(95)'), 'auditPresent': audited is not None, 'usableMeasurement': audited.get('usableMeasurement') if audited else False})
expected_core = {f'{prefix}-{phase}' for prefix in order for phase in ('warmup', 'control')}
seen = {run['label'] for run in index.get('runs', [])}
missing_core = sorted(expected_core - seen)
if missing_core:
    issues.append({'problem': 'Missing planned core cases', 'labels': missing_core})
complete = index.get('completed') is True and bool(analysis) and analysis.get('collectionComplete') is True and not issues and len(prefixes) == 4
write(DEST / 'outcomes.json', {'kind': 'codec-startup', 'evidenceKind': 'local-diagnostic-not-AWS-acceptance', 'completeAuditAndJfrCollection': complete, 'cases': outcomes, 'coldJfrComparison': jfr, 'issues': issues, 'limits': 'Local diagnostics only, not acceptance. Missing events in an existing sampled bracket mean no recorded matching events, not proven absence. Cumulative monitor durations overlap across threads and are attributed by event start, not clipped to five seconds or CPU time. ABBA alone does not isolate causality.'})
table = ['| Case | Exit | Requests | Errors % | Drops | p95 ms |', '|---|---:|---:|---:|---:|---:|']
for row in outcomes:
    error = f"{row['errorRate'] * 100:.4f}" if row['errorRate'] is not None else 'missing'
    table.append(f"| {row['label']} | {row['exitCode']} | {row['requests']} | {error} | {row['drops']} | {row['p95Ms']} |")
readme = '# Cache codec/startup local diagnostic evidence\n\nThese are local diagnostics, not AWS acceptance, capacity, or a production-readiness claim.\n\nThe released baseline JAR is compared in ABBA order with the separately hashed eager cache codec/immutable startup profile plus reviewed v3 backoff; candidate attribution is not baseline source or a previous measured candidate JAR.\n\n'
readme += '\n'.join(table) + '\n\n'
readme += f"Controller completed: `{index.get('completed') is True}`. Complete independent audit and JFR collection: `{complete}`. Missing or partial data are listed in `outcomes.json`, never treated as successful or zero-valued measurements. Failed threshold cases remain failures.\n\n"
readme += '`scripts/run-codec-startup.mjs` and `scripts/driver-resources.js` are the original frozen runtime scripts as used; `scripts/analyze-codec-startup.py` is the audit script as used, retaining corrected maximum aggregation for `*_max` while sums/counts are unchanged. `JfrCodecSummary.java` differs from the original cold helper only in its accepted codec-prefix regex; its package-private class name is intentionally unchanged for Java source-file launch.\n\n'
readme += '`receipts.json` is condensed and excludes SQL/query text and proxy configuration. Per-case contexts omit proxy bodies; summaries, exit codes, actions and independent audits are retained when present. JFR outputs contain aggregated symbols/durations only. `private-artifacts.json` hashes external raw files and recordings; no raw samples, JFR binaries, log bodies, properties, local.env, TLS keys or credentials are copied.\n\n'
readme += 'JFR first-five-second comparisons are in `outcomes.json`. Monitor event durations are inclusive, overlap across threads, and are assigned by event start without clipping; they are not five-second wall time or CPU usage. A present bracket without matching events is not proof that contention is impossible. No failed arm was rerun. Full application verification and living documentation are outside this evidence-only task.\n'
with (DEST / 'README.md').open('x') as output:
    output.write(readme)
retained = [{'path': str(path.relative_to(DEST)), 'bytes': path.stat().st_size, 'sha256': digest(path)} for path in sorted(DEST.rglob('*')) if path.is_file()]
write(DEST / 'retained-files.json', {'kind': 'codec-startup-retained-files', 'createdAt': datetime.now(timezone.utc).isoformat(), 'excludesSelf': True, 'files': retained})
print(json.dumps({'completeAuditAndJfrCollection': complete, 'cases': outcomes, 'coldJfrComparison': jfr, 'issues': issues}, indent=2))
