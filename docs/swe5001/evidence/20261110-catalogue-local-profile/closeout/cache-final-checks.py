"""Final local source/evidence gate summary, separate from unresolved runtime acceptance."""
import hashlib
import json
from pathlib import Path

root = Path('/results')
repo = Path('/repo')
summary = json.loads((root/'cache-backoff3-verification-summary.json').read_text())
contract = json.loads((root/'cache-backoff3-contract-summary.json').read_text())
runtime = json.loads((root/'matrix-v2/backoff-v3-analysis.json').read_text())
codec = json.loads((root/'matrix-v2/codec-startup-analysis.json').read_text())
assert summary['exitCode'] == 0 and summary['forcedTerminationMessages'] == 0
assert summary['cacheCoverageComplete'] and summary['buildSuccessBanner']
assert summary['totals']['failures'] == 0 and summary['totals']['errors'] == 0
assert contract['jsonEqual'] and contract['textEqualAfterNewlineNormalization']
assert runtime['collectionComplete'] and len(runtime['cases']) == 16
assert codec['collectionComplete'] and len(codec['cases']) == 12
failed_controls = [case for case in codec['cases'] if case['label'] in ('codec-candidate-a-control', 'codec-candidate-b-control')]
assert len(failed_controls) == 2 and all(case['exitCode'] == 99 and case['latencyMs']['p(95)'] > 800 for case in failed_controls)
rows = (('main', 'blocbuy-infrastructure/src/main/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache.java'),
    ('test', 'blocbuy-infrastructure/src/test/java/com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCacheTest.java'),
    ('it', 'blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/BestOpenDealCacheIT.java'))
for kind, relative in rows:
    source = repo/'backend'/relative
    assert source.read_text() == (root/('formatted-cache-backoff3-'+kind+'.java')).read_text(), 'Post-verification source drift'
scans = ('cache-final-code-gitleaks.json', 'cache-final-evidence-gitleaks.json')
for name in scans:
    assert json.loads((root/name).read_text()) == [], 'Scoped secret scan has findings'
result = {'kind': 'local-cache-closeout-not-performance-acceptance', 'fullVerification': summary['totals'],
    'backendBuildPolicyTestsPassed': 8,
    'cacheCoverageComplete': True, 'contractUnchanged': True, 'earlierBackoffRuntimeCases': 16, 'rejectedCodecRuntimeCases': 12,
    'sourceSnapshotMatchesCheckout': True, 'scopedGitleaksFindings': 0,
    'sourceSnapshot': 'Current source exactly equals reviewed-v3 full snapshot. Rejected codec/profile source and its policy-specific test preserved separately; no measured runtime is falsely attributed to the later reviewed correction.',
    'runtimeAcceptance': False, 'unresolved': ['Cold-start overload', 'Slow/outage p95 above the unchanged local 800-ms screen', 'Redis-dependent health remains unchanged', 'AWS V3/capacity/linearity not accepted'],
    'limits': 'No hosted CI/Sonar result, commit, push, PR, deployment or AWS operation. Existing diagnostic failures remain retained.'}
(root/'cache-final-checks.json').write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result, indent=2))
