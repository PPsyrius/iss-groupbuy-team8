"""Read-only #789 regression checks; optional exact-base corrected peer fixture."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[4])
    parser.add_argument('--peer-root', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    root = args.repo.resolve()
    package = root / 'docs/swe5001/evidence/20261112-issue-789'
    report = root / 'docs/swe5001/report'
    facts = json.loads((package / 'facts.json').read_text(encoding='utf-8'))
    cleanup = json.loads((package / 'cleanup.json').read_text(encoding='utf-8'))
    manifest = json.loads((package / 'peer-base.json').read_text(encoding='utf-8'))
    checks = {}

    def check(name, condition):
        checks[name] = bool(condition)

    def text(path):
        return path.read_text(encoding='utf-8')

    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    perf = text(report / 'sections/04-quality-attributes.typ')
    ops = text(report / 'sections/05-devops.typ')
    check('original-1-cleanup-separate-from-failed-nightly', cleanup['runtime']['normalEnvStatusExitCode'] == 0 and cleanup['generator']['state'] == 'terminated' and not facts['nightlyCleanup']['runtimeDestructionProven'])
    check('original-2-postload-observations-attributed', all(s in perf for s in ('Lim reports', '23:09–23:21', '22:53:48', 'attributed readback')))
    check('original-3-hard-refusal-and-separate-wrapper', all(s in perf for s in ('not merely a warning', 'without the original runner', 'not a clean launcher pass')))
    check('original-4-same-client-not-same-network', facts['ownerConfirmations']['tenNovAndR1R2R3SamePersonalLaptopViaDockerDesktop'] and not facts['ownerConfirmations']['allEarlierWifiConditionsConfirmedIdentical'])
    check('original-5-gate-provenance-and-rationale-boundary', all(s in perf for s in ('first committed by Liu Jin', 'fe4e9803', 'No retained decision')))
    check('original-6-tag-and-specific-scan-failure', all(s in ops for s in ('41763057192', 'Trivy image-scan step', 'signer trust was not independently', 'were skipped')))
    effort = facts['effortReconstruction']
    check('original-7-reconstruction-not-timesheet', sum(effort['assumedWorkPackageHours']) == 48 and effort['baseHours'] / effort['hoursPerIdealDay'] == 8 and not effort['ownerConfirmedEightDays'] and not effort['measuredHours'])
    check('original-8-owner-confirmations-not-remediation', facts['ownerConfirmations']['aiss19Owner'] == facts['ownerConfirmations']['aiss20Owner'] == 'Pan' and facts['ownerConfirmations']['aiss07Owners'] == 'Liu Jin (design), Pan (verification)')
    check('original-9-exact-supplied-style-pdf-reviewed', facts['peerStylePdfBefore']['pages'] == 140 and facts['peerStylePdfBefore']['sha256'] == 'b31c14b7d67b61b536b7c6fc958473fee8a06e5c628ad5efe64fa8bb63487621')
    check('review-1-r4-chart-source-and-caption', '20261111-v3-r4' in text(report / 'diagrams/performance-v3-timeline.py') and 'zero browse errors / drops' in text(report / 'diagrams/performance-v3-timeline.svg') and 'R4 only' in perf)
    readme = text(root / 'docs/swe5001/evidence/20261111-v3-r4/README.md')
    check('review-2-dated-publication-correction', 'Publication addendum' in readme and 'original preparation-status paragraph below is historical' in readme and '0405b1f0cbfd5442cb3dfef084c69f785b272d82' in readme)
    check('review-3-metrics-answer-first', 'Each run is judged on completed versus offered' in perf.split('=== Workload', 1)[0])
    check('review-4-two-missing-metric-rows', all(s in perf for s in ('HTTPCode_Target_5XX_Count', 'scaleOutObserved', 'latencyAndScaleOutGatesPassed', 'absence is unknown')))
    harness = text(report / 'AGENTS.md')
    check('review-5-target-provenance', 'committed request-target fallback is 200' in harness and '12,000 is an environment setting' in harness)
    check('review-6-bounded-historical-diagnostic', all(s in perf for s in ('Historical diagnostic', '272–286', '425–461', '155,554', 'not accepted per-task capacity')))
    check('review-7-image-assurance-limitations', all(s in ops for s in ('without rescanning', 'Weekly Trivy', 'no image signing or provenance attestation', 'not a per-image attestation')))
    ci = text(report / 'diagrams/peer-d10a-ci-image-chain.mmd')
    delivery = text(report / 'diagrams/peer-d10b-delivery.mmd')
    check('review-8-ci-decisions-conditionals-and-finals', all(s in ci for s in ('J --> Approval', '[not triggered]', 'Mono --> Blocked', 'ImageScan --> Blocked', 'Blocked --> Failed', 'end Failed')))
    check('review-8-early-failures-reach-cleanup', all(s in delivery for s in ('ImagesOk --> Cleanup', 'PublishOk --> Cleanup', 'UpOk --> Cleanup', 'Release --> Cleanup', 'either outcome')))
    check('review-9-cross-references-own-detail', 'Section 4.1 owns sizing' in ops and 'owns task revisions, budget arithmetic' in ops and 'Sizing belongs in §4.1' in perf)
    check('review-10-no-unnecessary-account-scp-literals', all(s not in perf for s in ('402178232802', 'p-4hlb2c8g')))
    check('retained-r4-acceptance-boundary', all(s in perf for s in ('Observations must bracket execution', 'maximum capacity or controlled linear', 'not a canonical pass')))
    check('retained-chargeable-archives-and-exclusions', cleanup['retention']['storageChargesRemain'] and cleanup['archive']['encryptedGeneratorDiskSnapshots'] == 1 and cleanup['retention']['permanentFoundationAndEdgeNotTargeted'] and cleanup['retention']['swe5006NotTargeted'])

    original = json.loads((root / 'docs/swe5001/evidence/20261111-v3-r4/secret-scan.json').read_text())['files']
    core = ('verification.json', 'browse-minutes.csv', 'scaling-activities.csv', 'server-minutes.csv', 'service-observations.jsonl', 'cloudwatch-points.jsonl', 'cloudwatch-series.json', 'source-projections.json', 'results.py')
    check('nine-original-r4-core-files-unchanged', all(sha(root / (name := 'docs/swe5001/evidence/20261111-v3-r4/' + file)) == original[name] for file in core))
    frozen = root / 'docs/swe5001/evidence/20261111-v3-r4'
    for current, historical in (('README.original-20261111.md', 'README.md'), ('SHA256SUMS.original-20261111', 'SHA256SUMS')):
        p = subprocess.run(['git', '-C', str(root), 'show', '0405b1f0cbfd5442cb3dfef084c69f785b272d82:docs/swe5001/evidence/20261111-v3-r4/' + historical], capture_output=True)
        check('frozen-original-metadata:' + historical, p.returncode == 0 and (frozen / current).read_bytes() == p.stdout)
    check('peer-patch-sha256-matches-manifest', sha(package / 'peer-report.patch') == manifest['patchSha256'])
    check('peer-patch-matches-recorded-build-base', manifest['base'] == facts['peerSourceReviewed'] == '02c7b822354656a4b6b7d269c876bc0e76635295')
    check('peer-patch-excludes-shared-swe5006-changes', all(name.startswith('docs/swe5001/report/') for name in manifest['files']))
    check('closure-integration-check-and-pr-closing-keyword', '- [ ] Integrate the peer corrections' in text(package / 'closure-checklist.md') and '\nCloses #789.\n' in text(package / 'pr-draft.md'))

    if args.peer_root:
        peer = args.peer_root.resolve()
        for name, identity in manifest['files'].items():
            check('peer-after-hash:' + name, (peer / name).is_file() and sha(peer / name) == identity['afterSha256'])
        pperf = text(peer / 'docs/swe5001/report/sections/04-1-performance.typ')
        pops = text(peer / 'docs/swe5001/report/sections/05-devops.typ')
        psec = text(peer / 'docs/swe5001/report/sections/04-3-security.typ')
        check('peer-no-stale-capacity-or-scale-in-claims', all(s not in pperf for s in ('the committed 12,000', 'No record covers scale-in', 'The next server-side limit is')))
        check('peer-metric-rows-and-provenance', all(s in pperf for s in ('HTTPCode_Target_5XX_Count', 'scaleOutObserved', 'Each run is judged', 'first committed by Liu Jin')))
        check('peer-no-unnecessary-identifiers', '402178232802' not in psec and 'p-4hlb2c8g' not in psec)
        check('peer-current-cleanup-and-image-limitations', all(s in pops for s in ('12:13:20', '12:06:02', 'Later reuse and supply-chain limits', 'no image signing or provenance attestation')))

    result = {'scope': 'Read-only source/data/patch regression checks for #789; PDF/URI/font verification is recorded separately', 'checks': checks, 'passed': sum(checks.values()), 'failed': [k for k, v in checks.items() if not v], 'peerFixtureChecked': bool(args.peer_root), 'cloudOrPublicationMutation': False}
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
    return 0 if all(checks.values()) else 1


if __name__ == '__main__':
    raise SystemExit(main())
