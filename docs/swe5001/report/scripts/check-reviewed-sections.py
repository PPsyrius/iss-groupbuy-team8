"""Offline regression for the post-#801 report; no cloud calls or data rewrites.

Run from the repository root. PDF layout/URI checks and the standalone R4,
retention and diagram verifiers are separate verification levels.
"""
import json
from decimal import Decimal, ROUND_HALF_UP
from math import ceil
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parents[4]
REPORT = ROOT / 'docs/swe5001/report'
BASE = '81a8d0fb8ae128dc424d0e9d630083c39c691805'
APP = '3a893036d70d028b29ae13ebf820283571c1ae00'
EVIDENCE = '27530a4dac0b2f574ee5bef6a80c5b5bbdffeca6'
INSPECTED = 'b80debcd8b8cdeb1272219758789e1a9927b9d7d'


def git(*args):
    return subprocess.run(['git', '-C', str(ROOT), *args], check=True,
                          capture_output=True, text=True, encoding='utf-8').stdout


def text(name):
    return (REPORT / name).read_text(encoding='utf-8')


def snapshot(path, section, override):
    # Sections 4.3 and 5 shadow lib.typ's src with local inspected-source
    # wrappers. Keep their historical pins; lib.typ alone is not the caller.
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


def check_ci_baseline(ops):
    """Compare the cited paragraph with recorded jobs, not hard-coded totals."""
    path = 'docs/swe5001/evidence/20261113-ci-check-cost-estimate/development-baseline.json'
    paragraphs = [p for p in ops.split('\n\n') if path in p]
    assert len(paragraphs) == 1, 'CI baseline needs one cited report paragraph'
    paragraph = paragraphs[0]
    receipt = json.loads((ROOT / path).read_text())
    eligible = [r for r in receipt['runs'] if r['classification']]
    minutes = sum(ceil(j['seconds'] / 60) for r in eligible for j in r['jobs'])
    cost = (Decimal(minutes) * Decimal(str(receipt['summary']['linux_usd_per_minute']))).quantize(
        Decimal('0.01'), rounding=ROUND_HALF_UP)
    count = re.search(r'([\d,]+)\s+qualifying backend delivery runs', paragraph)
    duration = re.search(r'([\d,]+)\s+runner-minutes', paragraph)
    amount = re.search(r'US\\\$([\d,]+\.\d{2})(?!\d)', paragraph)
    rate = re.search(r'US\\\$([\d.]+)/min', paragraph)
    assert count and int(count[1].replace(',', '')) == len(eligible)
    assert duration and int(duration[1].replace(',', '')) == minutes
    assert amount and Decimal(amount[1].replace(',', '')) == cost
    assert rate and Decimal(rate[1]) == Decimal(str(receipt['summary']['linux_usd_per_minute']))
    assert not re.search(r'successful (?:backend )?deliver', paragraph, re.I)
    assert re.search(r'list[- ]price equivalent', paragraph, re.I), 'CI estimate is a list-price equivalent, not a charge'
    billing = [sentence for sentence in re.split(r'(?<=[.!;])\s+', paragraph)
               if re.search(r'public', sentence, re.I) and re.search(r'standard', sentence, re.I)]
    assert any(re.search(r'\bfree\b|\bno (?:runner )?charge\b|\bzero (?:runner )?charge\b', sentence, re.I)
               and not re.search(r'\bnot free\b|\bnot (?:a )?zero\b|\b(?:incur|incurs|incurred) (?:a )?charge\b', sentence, re.I)
               for sentence in billing), 'Standard public-repository runners have no charge'


def check_account_read_boundary(security):
    """Protect the specific account-read qualification, not words elsewhere."""
    paragraph = next(p for p in security.split('\n\n') if re.search(
        r'Lim reports[^.\n]*11 November[^.\n]*CloudTrail', p))
    assert re.search(r'(?:no|without)[^.\n]*output[^.\n]*(?:retained|verify)', paragraph, re.I)
    assert not re.search(r'(?:AWS|command) output[^.\n]*independently verif', paragraph, re.I)


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
    assert all(s in perf for s in ('725,999', '726,000', '360,000'))
    assert 'No record covers scale-in' not in perf
    assert all(s in perf for s in ('75/181', '130/181'))
    assert re.search(r'(?:not|unproven|incomplete|missing)[^.\n]*(?:acceptance|observations|analyser)|(?:acceptance|observations|analyser)[^.\n]*(?:not|unproven|incomplete|missing)', perf, re.I)
    checks['reviewed-performance-boundaries'] = True

    security = text('sections/04-3-security.typ')
    # Governance prose commits are not retained AWS command output. Do not
    # require links to them or treat ancestry as proof of the claimed controls.
    assert 'Service Control Policies (SCPs)' in security
    assert re.search(r'organisation[^.\n]*(?:unknown|unverified|not independently)', security, re.I)
    assert re.search(r'90.day[^.\n]*event history|event history[^.\n]*90.day', security, re.I)
    assert re.search(r'(?:governance|readback|command output|AWS output)[^.\n]*(?:not retained|unverified|unavailable|not independently)|(?:not retained|unverified|unavailable|not independently)[^.\n]*(?:governance|readback|command output|AWS output)', security, re.I)
    checks['governance-evidence-boundaries'] = True
    check_account_read_boundary(security)
    limits = text('sections/03-4-limitations.typ') + text('registers.md') + security
    assert not any(s in limits for s in ('Remove the key or move', 'A misuse would leave no organisation-wide record', 'Scan the rebuilt image before'))
    assert re.search(r'accepted[^.\n]*(?:exception|design)', limits, re.I)
    assert re.search(r'no (?:removal|retirement)[^.\n]*(?:planned|approved)|no removal is planned', limits, re.I)
    ops = text('sections/05-devops.typ')
    assert re.search(r'before merge', ops, re.I) and re.search(r'after merge', ops, re.I)
    # Estimates must stay labelled; counts/rates belong to the dated evidence,
    # not a checker tied to one sample or one sentence of report prose.
    assert re.search(r'estimat|counterfactual', ops, re.I)
    assert re.search(r'not[^.\n]*(?:invoice|billing|observed|measured)', ops, re.I)
    check_ci_baseline(ops)
    checks['reported-ci-baseline-matches-recorded-jobs'] = True
    audit_row = next(line for line in security.splitlines() if '[Auditability:' in line)
    assert 'Singapore' in audit_row and re.search(r'unknown|unverified|unproven', audit_row, re.I)
    assert 'non-delivery-gating Sonar' in ops and 'Trivy image-scan step' in ops
    delivery = text('diagrams/uml/src/d10b-delivery.mmd')
    assert 'SourceOk --> FG : [passed]' in delivery and 'SourceOk --> SourceFailed : [failed]' in delivery
    assert 'Down --> Ev : [any outcome]' in delivery
    checks['delivery-decisions-and-failure-paths'] = True

    # Scope preservation to this review's frozen receipts. A later dev change
    # to unrelated application, infrastructure or pipeline files is not a
    # regression in the report and must not fail this checker.
    for name in ('peer-report.patch', 'peer-base.json', 'facts.json', 'cleanup.json', 'verification.json'):
        assert not git('diff', '--name-only', BASE, '--', 'docs/swe5001/evidence/20261112-issue-789/' + name).strip()
    checks['scoped-historical-closure-receipts-preserved'] = True
    print(json.dumps({'scope': 'Current split report targeting dev after merged #801; offline source/target/data regression, not PDF or acceptance', 'base': BASE, 'checks': checks}, indent=2))


if __name__ == '__main__':
    main()
