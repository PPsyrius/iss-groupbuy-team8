"""Retain only derived diagnostics/projected cloud records and reproducible sources."""
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import shutil

source=Path('/analysis')
target=Path('/repo/docs/swe5001/evidence/20261111-v3-investigation')
assert (target/'README.md').is_file() and not (target/'retained-files.json').exists()
files=[]
for directory in ('r1-timing-v2','r2-timing-v2','cloud-read-v1','edge-read-v1','correlation-v1'):
    files += [p for p in sorted((source/directory).iterdir()) if p.is_file() and p.suffix in ('.json','.jsonl','.csv')]
for name in ('timing_analysis.py','test_timing_analysis.py','read_cloud_diagnostics.py','read_edge_diagnostics.py','correlate_findings.py',
             'timing-analysis-v1-failed-source.py','timing-tests-v1-source.py','timing-first-attempt-failure.json',
             'timing-tests-v1.log','timing-tests-v2.log','r2-client-error-markers.json','retain_investigation.py'):
    files.append(source/name)
for path in files:
    assert path.is_file()
    out=target/path.relative_to(source)
    out.parent.mkdir(parents=True,exist_ok=True)
    with path.open('rb') as stream,out.open('xb') as output:
        shutil.copyfileobj(stream,output)
    assert path.read_bytes()==out.read_bytes()
entries=[{'path':p.relative_to(target).as_posix(),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
         for p in sorted(target.rglob('*')) if p.is_file() and p.name!='retained-files.json']
with (target/'retained-files.json').open('x') as stream:
    json.dump({'retainedAtUtc':datetime.now(timezone.utc).isoformat(),'files':entries,
               'scope':'Read-only investigation addendum; original campaign evidence remains separately sealed and unchanged. No raw application logs/payloads, credentials, Terraform state/plans/private inputs or new load retained here.',
               'toolingTestsPassed':7,'earlierDiagnosticFailurePreserved':True,
               'rootCauseCertainty':'Immediate VU limit and late action are supported; earlier scaling alone guaranteeing both gates is not proven.',
               'runtimeMutationPerformed':False,'publication':'Local/uncommitted draft only.'},stream,indent=2)
print(json.dumps({'filesByteVerified':len(files),'filesHashedIncludingReadme':len(entries),'rawPayloadsAndCredentialsExcluded':True},indent=2))
