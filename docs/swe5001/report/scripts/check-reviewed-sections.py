"""Offline regression for the post-#801 report; no cloud calls or data rewrites.

Run from the repository root. PDF layout/URI checks and the standalone R4,
retention and diagram verifiers are separate verification levels.
"""
import json
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[4]
REPORT = ROOT / 'docs/swe5001/report'
BASE = '876aafe60eca9d06937b0119dcfb2ce30dc663cf'
APP = '3a893036d70d028b29ae13ebf820283571c1ae00'
EVIDENCE = '27530a4dac0b2f574ee5bef6a80c5b5bbdffeca6'
INSPECTED = 'b80debcd8b8cdeb1272219758789e1a9927b9d7d'


def git(*args):
    return subprocess.run(['git', '-C', str(ROOT), *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout


def text(name):
    return (REPORT / name).read_text(encoding='utf-8')


def snapshot(path, section, override):
    if override:
        return APP if override == 'freeze' else override.strip('"')
    if path.startswith('docs/swe5001/evidence/20261111-cost-explorer/'):
        return BASE
    if path.startswith('docs/swe5001/evidence/'):
        return EVIDENCE
    if path.startswith('docs/swe5001/report/'):
        return BASE
    if section == '04-3-security.typ' and path.startswith(('infra/', 'backend/')):
        return INSPECTED
    if section == '05-devops.typ' and (path.startswith(('infra/', '.github/')) or
                                     path in ('CONTRIBUTING.md', 'docs/agile/README.md')):
        return INSPECTED
    return APP


def main():
    checks = {}
    targets = set()
    for source in sorted((REPORT / 'sections').rglob('*.typ')):
        content = source.read_text(encoding='utf-8')
        assert '/blob/dev/' not in content, source
        assert not any(pin in content for pin in ('0405b1f0', 'a05c33fd')), source
        for match in re.finditer(r'#src\("([^"]+)"([^)]*)\)', content):
            path, rest = match.groups()
            override = re.search(r'rev:\s*("[0-9a-f]+"|freeze)', rest)
            targets.add((snapshot(path, source.name, override[1] if override else None), path.split('#')[0]))
    for revision, path in sorted(targets):
        git('cat-file', '-e', revision + ':' + path)
    checks['immutable-source-and-evidence-targets'] = len(targets)

    perf = text('sections/04-1-performance.typ')
    rows = re.findall(r'^\s*\[(\d+)\], \[([\d,]+)\], \[([\d.]+)\], \[(\d+)\],$', perf, re.M)
    calibration = json.loads((ROOT / 'docs/swe5001/evidence/20261111-load-reruns/v2/calibration.json').read_text())
    assert len(rows) == len(calibration['plateaus']) == 10
    for row, expected in zip(rows, calibration['plateaus']):
        assert int(row[0]) == expected['rps']
        assert int(row[1].replace(',', '')) == expected['completed']
        assert abs(float(row[2]) - expected['p95Ms']) < 0.001
        assert int(row[3]) == expected['errorRate'] == 0
    checks['v2-plateau-rows'] = len(rows)
    assert all(s in perf for s in ('725,999', '726,000', '360,000', '23:09–23:21', 'attributed', 'active-VU gauge'))
    assert 'No record covers scale-in' not in perf
    assert '75/181 and 130/181' in perf and 'pooled demand is 55' in perf
    assert perf.count('all-run p95 screen also failed') == 2
    checks['reviewed-performance-boundaries'] = True

    security = text('sections/04-3-security.typ')
    for commit in ('ea88b3ef1dbc1680571c1374924c4354ae12f6d7',
                   'e5d03121014bf0e0de0a3a173440b203eb2d6236',
                   '1f3f549e8bb8c7b67e34800913307a600f0184c9'):
        assert '/commit/' + commit in security
        git('merge-base', '--is-ancestor', commit, BASE)
    assert 'Service Control Policies (SCPs)' in security
    assert 'organisation-level trails remain unknown' in security
    assert 'default 90-day event history remains' in security
    checks['dated-governance-records-and-scope'] = True
    limits = text('sections/03-4-limitations.typ') + text('registers.md') + security
    assert not any(s in limits for s in ('Remove the key or move', 'A misuse would leave no organisation-wide record', 'Scan the rebuilt image before'))
    assert 'Accepted by design' in limits and 'no removal is planned' in limits
    ops = text('sections/05-devops.typ')
    assert 'Shift-left, fail-fast validation' in ops and 'avoiding duplicate post-merge checks saves' in ops
    assert 'non-delivery-gating Sonar' in ops and 'Trivy image-scan step' in ops
    delivery = text('diagrams/uml/src/d10b-delivery.mmd')
    assert 'SourceOk --> FG : [passed]' in delivery and 'SourceOk --> SourceFailed : [failed]' in delivery
    assert 'Down --> Ev : [any outcome]' in delivery
    checks['delivery-decisions-and-failure-paths'] = True

    protected = ('backend', 'frontend', 'infra', 'perf', '.github', 'contracts',
                 'docs/quality/nfr-register.md',
                 'docs/swe5001/evidence/20261111-load-reruns',
                 'docs/swe5001/evidence/20261111-v3-r4',
                 'docs/swe5001/evidence/20261112-audit-followup')
    assert not git('diff', '--name-only', BASE, '--', *protected).strip()
    for name in ('peer-report.patch', 'peer-base.json', 'facts.json', 'cleanup.json', 'verification.json'):
        assert not git('diff', '--name-only', BASE, '--', 'docs/swe5001/evidence/20261112-issue-789/' + name).strip()
    checks['raw-evidence-code-pipeline-and-nfr-preserved'] = True
    print(json.dumps({'scope': 'Current split report stacked on #801; offline source/target/data regression, not PDF or acceptance', 'base': BASE, 'checks': checks}, indent=2))


if __name__ == '__main__':
    main()
