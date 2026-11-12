"""Scoped offline hashes, syntax, links and private-value checks; no raw secret output."""
import ast
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import unquote

repo = Path('/repo')
root = repo/'docs/swe5001/evidence/20261110-catalogue-local-profile'
files = [p for p in root.rglob('*') if p.is_file()]
hashed = 0
addenda = 0
for manifest in root.rglob('retained-files.json'):
    value = json.loads(manifest.read_text())
    remapped = {}
    addendum_path = manifest.parent/'retained-files-addendum.json'
    if addendum_path.exists():
        addendum = json.loads(addendum_path.read_text())
        assert addendum['kind'] == 'codec-startup-attribution-only-retention-addendum'
        original = addendum['originalManifest']
        data = manifest.read_bytes()
        assert original['unchanged'] and len(data) == original['bytes'] and hashlib.sha256(data).hexdigest() == original['sha256']
        preserved = addendum['originalReadmeEntryPreservedAt']
        assert preserved['originalPath'] == 'README.md' and preserved['path'] == 'README-as-initially-retained.md'
        assert preserved['matchesOriginalManifestEntry'] and addendum['measurementAndAnalysisReceiptsChanged'] is False
        original_row = next(row for row in value['files'] if row['path'] == preserved['originalPath'])
        assert all(original_row[key] == preserved[key] for key in ('bytes', 'sha256'))
        remapped[preserved['originalPath']] = preserved['path']
        amended = addendum['amendedReadme']
        assert amended['path'] == 'README.md'
        data = (manifest.parent/amended['path']).read_bytes()
        assert len(data) == amended['bytes'] and hashlib.sha256(data).hexdigest() == amended['sha256']
        addenda += 1
        hashed += 2
    for row in value.get('files', value.get('retained', [])):
        name = row.get('path', row.get('name'))
        path = manifest.parent/remapped.get(name, name)
        assert path.is_file(), str(path)+' absent'
        data = path.read_bytes()
        assert len(data) == row['bytes'] and hashlib.sha256(data).hexdigest() == row['sha256'], str(path)+' hash drift'
        hashed += 1
for path in files:
    assert path.suffix not in ('.jfr', '.exec', '.jar', '.p12', '.key', '.pem'), str(path)+' private/binary artifact'
    if path.suffix == '.json': json.loads(path.read_text())
    if path.suffix == '.py': ast.parse(path.read_text(), filename=str(path))
private = {}
for line in Path('/results/local.env').read_text().splitlines():
    if '=' not in line or line.lstrip().startswith('#'): continue
    key, value = line.split('=', 1)
    value = value.strip().strip('"').strip("'")
    if re.search(r'PASSWORD|SECRET|TOKEN|ACCESS_KEY', key) and len(value) >= 12:
        private[key] = value
extra = [repo/'backend/blocbuy-infrastructure/src'/kind/'java/com/blocbuy/groupbuy/gateway'/name
    for kind, name in (('main', 'RedisOpenDealOfferCache.java'), ('test', 'RedisOpenDealOfferCacheTest.java'))]
extra += [repo/path for path in ('backend/docs/design/best-open-deal-cache.md', 'docs/quality/nfr-register.md',
    'docs/agile/sprint-04/log/2026-11-10.md', 'docs/swe5001/http-load-collection.md',
    'backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/BestOpenDealCacheIT.java')]
hits = []
for path in files+extra:
    text = path.read_text()
    if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', text): hits.append(str(path.relative_to(repo)))
    if any(value in text for value in private.values()): hits.append(str(path.relative_to(repo)))
assert not hits, 'Private material in scoped paths: '+str(hits)
linked = 0
markdown = [root/'MATRIX.md']+[p for p in files if p.name == 'README.md']
markdown += [repo/'backend/docs/design/best-open-deal-cache.md', repo/'docs/swe5001/http-load-collection.md']
for path in markdown:
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', path.read_text()):
        if target.startswith(('https:', 'http:', '#')): continue
        resolved = (path.parent/unquote(target.split('#', 1)[0])).resolve()
        assert resolved.exists(), str(path.relative_to(repo))+' broken link: '+target
        linked += 1
summary = {'kind': 'offline-scoped-evidence-audit-not-secret-scanner-or-runtime-acceptance',
    'hashedFiles': hashed, 'filesParsedAndChecked': len(files), 'localLinksChecked': linked,
    'verifiedAttributionOnlyRetentionAddenda': addenda,
    'privateConfiguredValuesChecked': len(private), 'privateValueHits': 0,
    'limits': 'Only scoped retained evidence/touched cache files; complements Gitleaks, does not replace it. No external link or hosted quality gate asserted.'}
Path('/results/cache-retained-evidence-audit.json').write_text(json.dumps(summary, indent=2)+'\n')
print(json.dumps(summary, indent=2))
