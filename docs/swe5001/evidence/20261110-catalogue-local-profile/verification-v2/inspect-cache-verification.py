"""Summarize Maven reports without copying test properties, system output or exception bodies."""
from collections import Counter
import io
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path('/results')
BACKEND = Path('/build')
scope_v2 = len(sys.argv) > 1 and sys.argv[1] == 'scope-v2'
prefix = 'cache-scope-full-verify' if scope_v2 else 'cache-full-verify-v2'
aggregate_name = 'cache-scope-jacoco-aggregate.xml' if scope_v2 else 'cache-jacoco-aggregate-v2.xml'
summary_name = 'cache-scope-verification-summary.json' if scope_v2 else 'cache-verification-summary.json'
output = {'kind': 'local-full-backend-verification-not-runtime-acceptance',
    'candidate': 'I/O-only v2' if scope_v2 else 'whole-method v1',
    'command': './mvnw -B -ntp clean verify -Pci' if scope_v2 else './mvnw -B -ntp -T 2 clean verify -Pci',
    'scope': 'All modules, no test/static/coverage skip flags; JDK 21 in Docker with local Testcontainers.',
    'exitCode': int((ROOT/(prefix+'-exit.txt')).read_text()),
    'logSha256': hashlib.sha256((ROOT/(prefix+'.log')).read_bytes()).hexdigest(),
    'logRetention': 'Private local Temp only; test reports/properties and exception bodies not copied to Git.',
    'suites': [], 'coverage': None}
totals = Counter()
for folder in ('surefire-reports', 'failsafe-reports'):
    for path in sorted(BACKEND.glob(f'**/target/{folder}/TEST-*.xml')):
        suite = ET.parse(path).getroot()
        counts = {key: int(suite.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
        totals.update(counts)
        output['suites'].append({'path': str(path.relative_to(BACKEND)), 'name': suite.get('name'), **counts})
output['totals'] = dict(totals)
aggregate = ROOT/aggregate_name
if aggregate.exists():
    coverage = ET.parse(aggregate).getroot()
    files = []
    for package in coverage.iter('package'):
        if package.get('name') != 'com/blocbuy/groupbuy/gateway': continue
        for source in package.findall('sourcefile'):
            if source.get('name') != 'RedisOpenDealOfferCache.java': continue
            files.append({'package': package.get('name'), 'name': source.get('name'),
                'counters': [dict(c.attrib) for c in source.findall('counter')],
                'lines': [dict(line.attrib) for line in source.findall('line')]})
    output['coverage'] = {'source': 'blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml',
        'aggregateSha256': hashlib.sha256(aggregate.read_bytes()).hexdigest(), 'files': files,
        'limits': 'Aggregate coverage is local evidence, not a hosted Sonar quality-gate result. Inspect changed lines/branches before readiness.'}
text = (ROOT/(prefix+'.log')).read_text()
output['buildSuccessBanner'] = '[INFO] BUILD SUCCESS' in text
output['completedAt'] = (re.findall(r'Finished at: (\S+)', text) or [None])[-1]
if scope_v2 and output['exitCode'] == 0:
    def sha(path):
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            for block in iter(lambda: stream.read(1024*1024), b''): digest.update(block)
        return digest.hexdigest()
    def cache_classes(path):
        with zipfile.ZipFile(path) as outer:
            matches = [name for name in outer.namelist() if name.startswith('BOOT-INF/lib/blocbuy-infrastructure-') and name.endswith('.jar')]
            assert len(matches) == 1, 'Expected one infrastructure dependency in commerce executable JAR'
            with zipfile.ZipFile(io.BytesIO(outer.read(matches[0]))) as inner:
                return {name: hashlib.sha256(inner.read(name)).hexdigest() for name in inner.namelist()
                    if name.startswith('com/blocbuy/groupbuy/gateway/RedisOpenDealOfferCache') and name.endswith('.class')}
    commerce = BACKEND/'services/commerce-svc/target/commerce-svc.jar'
    monolith = BACKEND/'blocbuy-bootstrap/target/blocbuy-backend-exec.jar'
    diagnostic = ROOT/'commerce-bulkhead-scope-v2.jar'
    left, right = cache_classes(commerce), cache_classes(diagnostic)
    assert len(left) == 2 and left == right, 'Verified and diagnostic cache bytecode differs'
    output['artifactAttribution'] = {'diagnosticCommerceSha256': sha(diagnostic),
        'verifiedCommerceSha256': sha(commerce), 'verifiedMonolithSha256': sha(monolith),
        'cacheClassSha256': left, 'cacheBytecodeMatchesDiagnostic': True,
        'limits': 'Whole-JAR hashes can differ due to packaging timestamps; both cache class byte hashes must match.'}
output['limits'] = ['Does not establish cold-start non-regression, ALB availability, AWS capacity or 80% linearity.',
    'Candidates remain uncommitted; runtime acceptance and coverage attribution require their own evidence.']
(ROOT/summary_name).write_text(json.dumps(output, indent=2))
print(json.dumps({'exitCode': output['exitCode'], 'totals': output['totals'], 'aggregatePresent': aggregate.exists(), 'completedAt': output['completedAt']}, indent=2))
