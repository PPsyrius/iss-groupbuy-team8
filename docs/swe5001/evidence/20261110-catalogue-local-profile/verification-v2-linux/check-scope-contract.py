"""Compare the verified-JAR contract without overwriting the checkout."""
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path('/results')
mode = sys.argv[1] if len(sys.argv) > 1 else 'scope-v2'
prefix = 'cache-linux2' if mode == 'linux-log-v2' else 'cache-linux' if mode == 'linux-log' else 'cache-scope'
assert int((ROOT/(prefix+'-full-verify-exit.txt')).read_text()) == 0, 'V2 full verify failed'
generated = ROOT/(prefix+'-openapi.json')
committed = Path('/repo/contracts/openapi.json')
assert (ROOT/(prefix+'-openapi-fetched.txt')).read_text().strip() == '1'
left, right = generated.read_text(), committed.read_text()
summary = {'kind': 'local-verified-JAR-contract-comparison',
    'commerceDiagnosticJarSha256': 'f4c7372bef03a6627331a469113e3a341f5531485f36da90213ff9861d621dc1',
    'command': 'java -jar blocbuy-bootstrap/target/blocbuy-backend-exec.jar --spring.profiles.active=openapi-export; GET localhost:18080/v3/api-docs',
    'jsonEqual': json.loads(left) == json.loads(right),
    'textEqualAfterNewlineNormalization': left == right,
    'generatedSha256': hashlib.sha256(generated.read_bytes()).hexdigest(),
    'checkoutSha256': hashlib.sha256(committed.read_bytes()).hexdigest(),
    'limits': 'No HTTP API changes intended; contract fetched from the monolith produced by full verification. No contract file overwritten, frontend client regenerated, commit or publication.'}
(ROOT/(prefix+'-contract-summary.json')).write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
assert summary['jsonEqual'] and summary['textEqualAfterNewlineNormalization'], 'Generated contract drifted; inspect, do not hand-edit'
