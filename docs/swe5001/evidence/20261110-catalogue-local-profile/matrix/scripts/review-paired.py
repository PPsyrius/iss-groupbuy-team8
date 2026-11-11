"""Safe, local-only paired diagnosis; no raw logs, query text or process metadata exported."""
from collections import Counter
from datetime import datetime
import json
from pathlib import Path

ROOT = Path('/results/matrix-v2')
index = json.loads((ROOT/'paired-index.json').read_text())
audit = json.loads((ROOT/'paired-analysis.json').read_text())
def stamp(value): return datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()
output = {'kind': 'paired-diagnostic-review-not-AWS-acceptance', 'cases': [], 'health': {}}
for run, case in zip(index['runs'], audit['cases'], strict=True):
    label = run['label']
    gauges = case['gauges']
    app = case['resources'][run['app']]
    row = {'label': label, 'start': run['startedAt'], 'end': run['finishedAt'],
           'cpuMedianPercentOneCore': app['cpuPercentOneCoreMedian'],
           'cpuMaxPercentOneCore': app['cpuPercentOneCoreMax'],
           'memoryMaxBytes': app['memoryBytesMax'], 'gauges': gauges,
           'hostMemoryAtLaunch': run.get('hostMemory'), 'metricReadFailures': case['metricReadFailures']}
    output['cases'].append(row)
    print(json.dumps({'label': label, 'start': row['start'], 'end': row['end'],
        'cpuMedian': row['cpuMedianPercentOneCore'], 'memory': row['memoryMaxBytes'],
        'pendingMax': gauges['hikaricp_connections_pending']['max'],
        'timeoutsFirst': gauges['hikaricp_connections_timeout_total']['first'],
        'timeoutsLast': gauges['hikaricp_connections_timeout_total']['last']}))
for prefix in ('paired-base', 'paired-bulkhead'):
    rows = [json.loads(line) for line in (ROOT/f'{prefix}-health.jsonl').read_text().splitlines()]
    health = []
    for run in index['runs']:
        if not run['label'].startswith(prefix): continue
        samples = [row for row in rows if stamp(run['startedAt']) <= stamp(row['at']) <= stamp(run['finishedAt'])]
        health.append({'label': run['label'], 'samples': len(samples),
            'outcomes': dict(Counter(str(row.get('httpStatus', 'request-error')) + '/' + row.get('status', 'unknown') for row in samples)),
            'maxDurationMs': max((row['durationMs'] for row in samples), default=None)})
    output['health'][prefix] = health
    recording = json.loads((ROOT/f'{prefix}-jfr-analysis.json').read_text())
    output[prefix+'-jfr'] = {key: recording[key] for key in ('cpuSamplesWithinWorkload', 'gc', 'compilation', 'phaseInclusiveMethods')}
    print(prefix, 'GC', recording['gc'], 'compilation', recording['compilation'])
output['limits'] = ['One sequential sample per arm; colder candidate outcome is adverse evidence, not isolated causality.',
    'Full Maven verification ran 06:54–07:00 UTC on the same Docker host with a 2-CPU / 4-GiB builder; Testcontainers child containers have separate limits. Retain overlap as a confound, not proof of harmlessness.',
    'Redis health remains in the existing health endpoint. Health probes do not distinguish optional display-cache fallback from authentication-store availability.',
    'JFR inclusive counts overlap; Java execution samples are not measured CPU percentages. No profiler demonstrates that holding permits through JSON caused cold overload.',
    'Candidate holds slots during JSON decoding/serialization as well as Redis I/O. That is an unisolated fallback-amplification risk under CPU-bound startup; do not deploy or claim startup non-regression.']
(ROOT/'paired-diagnosis.json').write_text(json.dumps(output, indent=2))
