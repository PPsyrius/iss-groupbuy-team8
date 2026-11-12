"""Read-only Actions runner-time baseline; writes a dated, generated JSON receipt."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from math import ceil
from pathlib import Path
import statistics
import subprocess

REPO = 'PPsyrius/iss-groupbuy-team8'
RATE = 0.006


def api(path, *args):
    for _ in range(3):
        result = subprocess.run(['gh', 'api', '--cache', '1h', f'repos/{REPO}/{path}', *args],
                                capture_output=True, text=True, encoding='utf-8')
        if result.returncode == 0:
            return json.loads(result.stdout)
    raise RuntimeError(f'{path}: {result.stderr}')


def seconds(start, end):
    return max(0, (datetime.fromisoformat(end.replace('Z', '+00:00')) -
                   datetime.fromisoformat(start.replace('Z', '+00:00'))).total_seconds())


def inspect(run):
    jobs = []
    page = 1
    while True:
        batch = api(f"actions/runs/{run['id']}/jobs?per_page=100&page={page}")['jobs']
        jobs.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    if not jobs:
        jobs = api(f"actions/runs/{run['id']}/jobs?per_page=100")['jobs']
    success = {j['name'] for j in jobs if j['conclusion'] == 'success'}
    names = {j['name'] for j in jobs}
    kind = ('image-publication' if 'publish-ecr-dev' in success else
            'legacy-docker-and-ec2' if 'publish-ecr-dev' not in names and
            {'docker', 'deploy-existing-ec2'} <= success else None)
    timed = []
    for job in jobs:
        if job['conclusion'] == 'skipped' or not job['runner_name']:
            continue
        if not job['started_at'] or not job['completed_at']:
            raise ValueError(f"Unfinished job {job['id']}")
        duration = seconds(job['started_at'], job['completed_at'])
        timed.append({'id': job['id'], 'name': job['name'],
                      'conclusion': job['conclusion'], 'labels': job['labels'],
                      'started_at': job['started_at'], 'completed_at': job['completed_at'],
                      'seconds': duration, 'rounded_minutes': ceil(duration / 60)})
    return {'id': run['id'], 'created_at': run['created_at'],
            'url': f"https://github.com/{REPO}/actions/runs/{run['id']}",
            'classification': kind, 'jobs': timed}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    runs = []
    page = 1
    while True:
        batch = api('actions/workflows/ci-backend.yml/runs', '--method', 'GET',
                    '-f', 'event=push', '-f', 'branch=dev', '-f', 'per_page=100',
                    '-f', f'page={page}')['workflow_runs']
        runs.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    with ThreadPoolExecutor(max_workers=4) as pool:
        records = sorted(pool.map(inspect, runs), key=lambda r: (r['created_at'], r['id']))
    eligible = [r for r in records if r['classification']]
    totals = [sum(j['seconds'] for j in r['jobs']) for r in eligible]
    rounded = sum(j['rounded_minutes'] for r in eligible for j in r['jobs'])
    labels = sorted({label for r in eligible for j in r['jobs'] for label in j['labels']})
    if labels != ['ubuntu-latest']:
        raise ValueError(f'Check runner rates before costing mixed labels: {labels}')
    per_job = defaultdict(lambda: {'count': 0, 'seconds': 0, 'rounded_minutes': 0})
    per_month = defaultdict(lambda: {'eligible_runs': 0, 'seconds': 0, 'rounded_minutes': 0})
    for run in eligible:
        month = per_month[run['created_at'][:7]]
        month['eligible_runs'] += 1
        for job in run['jobs']:
            row = per_job[job['name']]
            row['count'] += 1
            for key in ('seconds', 'rounded_minutes'):
                row[key] += job[key]
                month[key] += job[key]
    summary = {
        'inspected_runs': len(records), 'eligible_runs': len(eligible),
        'classifications': dict(Counter(r['classification'] for r in eligible)),
        'oldest_run': records[0]['created_at'], 'latest_run': records[-1]['created_at'],
        'runner_labels': labels, 'total_runner_seconds': sum(totals),
        'total_runner_minutes': sum(totals) / 60,
        'mean_runner_minutes_per_run': statistics.mean(totals) / 60,
        'median_runner_minutes_per_run': statistics.median(totals) / 60,
        'rounded_job_minutes': rounded,
        'linux_usd_per_minute': RATE,
        'rounded_minutes_cost_equivalent_usd': rounded * RATE,
        'by_month': dict(sorted(per_month.items())),
        'by_job': dict(sorted(per_job.items())),
    }
    receipt = {'observed_at_utc': datetime.now(timezone.utc).isoformat(),
               'repository': REPO,
               'scope': 'Latest-attempt job runner time on qualifying dev-push deliveries only; not savings or an invoice; parallel jobs summed separately.',
               'summary': summary, 'runs': records}
    args.output.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
