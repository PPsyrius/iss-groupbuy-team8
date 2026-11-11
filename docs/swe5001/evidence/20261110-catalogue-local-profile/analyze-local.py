"""Summarize retained local diagnostic samples; never infer AWS acceptance."""
from collections import Counter, defaultdict
from datetime import datetime
import json
from pathlib import Path
import re

ROOT = Path('/results')
def save(name, data):
    (ROOT / name).write_text(json.dumps(data, indent=2))
def percentile(values, p):
    if not values: return None
    values = sorted(values)
    index = (len(values) - 1) * p
    low = int(index)
    return values[low] + (values[min(low + 1, len(values) - 1)] - values[low]) * (index - low)
def time(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()

report = json.loads((ROOT / 'local-summary.json').read_text())
metrics = report['summary']['metrics']
rows = {}
for p in report['plan']:
    name = p['name']
    def metric(key): return metrics.get(f'{key}{{phase:{name}}}', {}).get('values', {})
    rows[name] = {'nominalRequests': p['rate'] * p['seconds'], 'requests': metric('browse_requests').get('count'),
                  'errors': metric('browse_errors'), 'latencyMs': metric('browse_latency'),
                  'drops': metrics.get(f'dropped_iterations{{scenario:{name}}}', {}).get('values', {}).get('count')}
raw = defaultdict(lambda: {'requests': 0, 'errors': 0, 'list': 0, 'detail': 0, 'latency': [], 'first': None, 'last': None})
for line in (ROOT / 'local-samples.jsonl').open():
    item = json.loads(line)
    if item['type'] != 'Point': continue
    data = item['data']; phase = data['tags'].get('phase')
    if phase is None: continue
    r = raw[phase]
    if item['metric'] == 'browse_requests':
        r['requests'] += int(data['value'])
        r['first'] = data['time'] if r['first'] is None else min(r['first'], data['time'])
        r['last'] = data['time'] if r['last'] is None else max(r['last'], data['time'])
    if item['metric'] == 'browse_errors': r['errors'] += int(data['value'])
    if item['metric'] == 'browse_latency': r['latency'].append(data['value'])
    if item['metric'] == 'http_reqs': r['detail' if data['tags']['name'] == 'product detail' else 'list'] += int(data['value'])
for name, r in raw.items():
    rows[name]['rawAudit'] = {k: v for k, v in r.items() if k != 'latency'}
    rows[name]['rawAudit']['p95Ms'] = percentile(r['latency'], .95)
    if name != 'warmup': assert rows[name]['requests'] == r['requests']

observations = [json.loads(line) for line in (ROOT / 'local-monitor.jsonl').read_text().splitlines()]
for name in rows:
    if name not in raw or not raw[name]['first']: continue
    start, end = time(raw[name]['first']), time(raw[name]['last'])
    selected = [row for row in observations if start <= time(row['at']) <= end]
    resources = {}
    for resource in ('commerce', 'postgres', 'redis', 'generator'):
        samples = [r for row in selected for r in row['resources'] if r['name'] == f'blocbuy-profile-{resource}-20261110' and 'error' not in r]
        cpus = [r['cpuPercentOneCore'] for r in samples if r['cpuPercentOneCore'] is not None]
        resources[resource] = {'samples': len(samples), 'cpuPercentOneCoreMedian': percentile(cpus, .5),
            'cpuPercentOneCoreMax': max(cpus) if cpus else None,
            'memoryBytesMax': max((r['memoryBytes'] for r in samples), default=None)}
        if len(samples) >= 2:
            resources[resource]['throttledPeriodsDelta'] = samples[-1]['throttling']['throttled_periods'] - samples[0]['throttling']['throttled_periods']
    pools = defaultdict(list)
    for row in selected:
        for line in (ROOT / f"prometheus-{row['sample']:03}.txt").read_text().splitlines():
            match = re.match(r'^(hikaricp_[a-z_]+)(?:\{[^}]+\})?\s+([0-9.eE+-]+)', line)
            if match: pools[match[1]].append(float(match[2]))
    rows[name]['resources'] = resources
    rows[name]['poolMetrics'] = {key: {'first': values[0], 'last': values[-1], 'max': max(values)} for key, values in pools.items()}
save('local-analysis.json', {'evidenceKind': report['evidenceKind'], 'phases': rows, 'monitorSamples': len(observations),
    'prometheusFailures': sum(not r['prometheusAvailable'] for r in observations),
    'limits': 'Local x86 Docker CPU quotas, warmup/schedule and JFR overhead differ from AWS; not capacity/linearity acceptance.'})
(ROOT / 'phase-windows.txt').write_text(''.join(f"{name}|{r['first']}|{r['last']}\n" for name, r in raw.items() if r['first']))
for name, row in rows.items():
    print(name, 'requests', row['rawAudit']['requests'], 'errors', row['rawAudit']['errors'], 'p95ms', row['rawAudit']['p95Ms'])
