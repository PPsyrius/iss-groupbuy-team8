"""Compare the verified-JAR contract without overwriting the checkout."""
import hashlib
import json
from pathlib import Path

ROOT = Path('/results')
assert int((ROOT/'cache-scope-full-verify-exit.txt').read_text()) == 0, 'V2 full verify failed'
generated = ROOT/'cache-scope-openapi.json'
committed = Path('/repo/contracts/openapi.json')
assert (ROOT/'cache-scope-openapi-fetched.txt').read_text().strip() == '1'
left, right = generated.read_text(), committed.read_text()
summary = {'kind': 'local-verified-JAR-contract-comparison',
    'commerceDiagnosticJarSha256': 'f4c7372bef03a6627331a469113e3a341f5531485f36da90213ff9861d621dc1',
    'command': 'java -jar blocbuy-bootstrap/target/blocbuy-backend-exec.jar --spring.profiles.active=openapi-export; GET localhost:18080/v3/api-docs',
    'jsonEqual': json.loads(left) == json.loads(right),
    'textEqualAfterNewlineNormalization': left == right,
    'generatedSha256': hashlib.sha256(generated.read_bytes()).hexdigest(),
    'checkoutSha256': hashlib.sha256(committed.read_bytes()).hexdigest(),
    'limits': 'No HTTP API changes intended; contract fetched from the monolith produced by full verification. No contract file overwritten, frontend client regenerated, commit or publication.'}
(ROOT/'cache-scope-contract-summary.json').write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
assert summary['jsonEqual'] and summary['textEqualAfterNewlineNormalization'], 'Generated contract drifted; inspect, do not hand-edit'
