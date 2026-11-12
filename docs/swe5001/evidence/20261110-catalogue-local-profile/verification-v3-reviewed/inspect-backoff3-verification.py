"""Fresh reviewed-v3 verification, never treating the earlier runtime snapshot as current."""
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

root, backend = Path('/results'), Path('/build')
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
prefix = 'cache-backoff3'
text = (root/(prefix+'-full-verify.log')).read_text()
output = {'kind': 'local-reviewed-v3-full-verification-not-performance-acceptance',
    'command': './mvnw -B -ntp -T 2 clean verify -Pci', 'preclean': 'All modules cleaned first to avoid stale skipped-module reports',
    'exitCode': int((root/(prefix+'-full-verify-exit.txt')).read_text()),
    'logSha256': sha(root/(prefix+'-full-verify.log')), 'logRetention': 'Private local Temp only; no report properties or exception bodies retained',
    'forcedTerminationMessages': text.count('Surefire is going to kill self fork JVM'),
    'buildSuccessBanner': '[INFO] BUILD SUCCESS' in text,
    'completedAt': (re.findall(r'Finished at: (\S+)', text) or [None])[-1], 'suites': []}
totals = Counter()
for folder in ('surefire-reports', 'failsafe-reports'):
    for path in sorted(backend.glob('**/target/'+folder+'/TEST-*.xml')):
        suite = ET.parse(path).getroot()
        counts = {key: int(suite.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
        totals.update(counts)
        output['suites'].append({'path': str(path.relative_to(backend)), 'name': suite.get('name'), **counts})
output['totals'] = dict(totals)
aggregate = root/(prefix+'-jacoco-aggregate.xml')
output['coverage'] = None
if aggregate.exists():
    tree = ET.parse(aggregate).getroot()
    sources = [{'name': sf.get('name'), 'counters': [c.attrib for c in sf.findall('counter')],
        'lines': [line.attrib for line in sf.findall('line')]} for sf in tree.iter('sourcefile') if sf.get('name') == 'RedisOpenDealOfferCache.java']
    output['coverage'] = {'aggregateSha256': sha(aggregate), 'files': sources}
output['cacheCoverageComplete'] = bool(output['coverage'] and output['coverage']['files']) and all(
    int(line['mi']) == 0 and int(line['mb']) == 0 for sf in output['coverage']['files'] for line in sf['lines'])
if output['exitCode'] == 0:
    def classes(path):
        with zipfile.ZipFile(path) as outer:
            names = [n for n in outer.namelist() if n.startswith('BOOT-INF/lib/blocbuy-infrastructure-') and n.endswith('.jar')]
            assert len(names) == 1
            with zipfile.ZipFile(io.BytesIO(outer.read(names[0]))) as inner:
                return {n: hashlib.sha256(inner.read(n)).hexdigest() for n in inner.namelist()
                    if n.startswith('com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache') and n.endswith('.class')}
    diagnostic = root/'commerce-backoff-v3-reviewed.jar'
    commerce = backend/'services/commerce-svc/target/commerce-svc.jar'
    left, right = classes(diagnostic), classes(commerce)
    assert len(left) == 3 and left == right, 'Reviewed package and verified cache class bytes differ'
    output['artifactAttribution'] = {'focusedReviewedJarSha256': sha(diagnostic), 'verifiedCommerceJarSha256': sha(commerce),
        'verifiedMonolithJarSha256': sha(backend/'blocbuy-bootstrap/target/blocbuy-backend-exec.jar'),
        'cacheClassSha256': right, 'allThreeCacheClassBytesMatchReviewedPackage': True,
        'earlierRuntimeJarSha256': sha(root/'commerce-backoff-v3.jar'),
        'earlierRuntimeMeasurementCoversThisSourceSnapshot': False,
        'changesSinceRuntime': 'Unused analysis annotation/import removal; ordered atomic failure publication; deterministic race tests; outage-IT context isolation. No current-source runtime acceptance is claimed.'}
(root/(prefix+'-verification-summary.json')).write_text(json.dumps(output, indent=2)+'\n')
print(json.dumps({k: output[k] for k in ('exitCode', 'totals', 'cacheCoverageComplete', 'forcedTerminationMessages')}, indent=2))
if output['exitCode'] == 0:
    generated, committed = root/(prefix+'-openapi.json'), Path('/repo/contracts/openapi.json')
    assert (root/(prefix+'-openapi-fetched.txt')).read_text().strip() == '1'
    contract = {'kind': 'local-verified-JAR-contract-comparison', 'jsonEqual': json.loads(generated.read_text()) == json.loads(committed.read_text()),
        'textEqualAfterNewlineNormalization': generated.read_text() == committed.read_text(), 'generatedSha256': sha(generated), 'checkoutSha256': sha(committed),
        'verifiedMonolithJarSha256': output['artifactAttribution']['verifiedMonolithJarSha256'], 'limits': 'Checkout contract not overwritten; no API/client change or hosted gate asserted.'}
    (root/(prefix+'-contract-summary.json')).write_text(json.dumps(contract, indent=2)+'\n')
    assert contract['jsonEqual'] and contract['textEqualAfterNewlineNormalization']
