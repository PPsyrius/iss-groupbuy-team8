"""Sanitize local coverage diagnostics; preserve original logs/stacks privately by hash."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path('/results')
def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for part in iter(lambda: stream.read(1024*1024), b''): h.update(part)
    return {'bytes': path.stat().st_size, 'sha256': h.hexdigest()}
result = {'kind': 'local-verification-log-capture-diagnosis-not-AWS-root-cause', 'runs': [], 'privateArtifacts': []}
for label, prefix in (('default-pipes-host-log', 'cache-channel-pipes'), ('tcp-host-log', 'cache-channel-tcp'), ('default-pipes-linux-log', 'cache-linux-unit')):
    log = ROOT/(prefix+'.log')
    text = log.read_text()
    probes = (ROOT/(prefix+'-probes.txt')).read_text()
    summaries = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', text)
    cache_lines = [line for line in probes.splitlines() if line.startswith('blocbuy-infrastructure/target/jacoco.exec ')]
    result['runs'].append({'label': label, 'exitCode': int((ROOT/(prefix+'-exit.txt')).read_text()),
        'forcedTerminationMessages': text.count('Surefire is going to kill self fork JVM'),
        'cacheUnitExecutionData': cache_lines, 'lastModuleTestSummary': dict(zip(('tests', 'failures', 'errors', 'skipped'), map(int, summaries[-1]), strict=True)),
        'privateLog': {'path': log.name, **digest(log)}})
for path in sorted((ROOT/'cache-ack-parent-stacks').glob('*.txt')):
    text = path.read_text()
    section = re.search(r'"ThreadedStreamConsumer".*?(?=\n\n)', text, re.S)
    blocked = section is not None and 'FileOutputStream.write' in section[0] and 'DirectConsoleOutput.writeTestOutput' in section[0]
    result['privateArtifacts'].append({'path': str(path.relative_to(ROOT)), 'testOutputConsumerInNativeFileWrite': blocked, **digest(path)})
result['parentStackFinding'] = {'samples': len(result['privateArtifacts']),
    'nativeOutputWriteSamples': sum(r['testOutputConsumerInNativeFileWrite'] for r in result['privateArtifacts']),
    'safeFrameChain': ['FileOutputStream.write', 'AnsiOutputStream.write', 'DirectConsoleOutput.writeTestOutput', 'TestSetRunListener.writeTestOutput', 'ForkClient$StdOutListener.handle', 'ThreadedStreamConsumer$Pumper.run'],
    'interpretation': 'Parent log-output draining can delay BYE_ACK until the fork is halted; full test logs were captured on a Windows bind mount. This is a local verification-harness issue, not demonstrated application/AWS shutdown failure.'}
for name in ('cache-coverage-agent-probe.json', 'cache-coverage-agent-ci.txt', 'cache-coverage-agent-plain.txt', 'cache-channel-original-pom.xml', 'cache-channel-tcp-pom.xml'):
    path = ROOT/name
    result['privateArtifacts'].append({'path': name, **digest(path)})
original = (ROOT/'cache-channel-original-pom.xml').read_text()
checkout = Path('/repo/backend/pom.xml').read_text()
result['checkoutPomUnchanged'] = original == checkout
result['decision'] = 'Write Maven/Jansi output to the existing named Linux build volume, copy it to the host after the run; preserve every test/output and original POM, plugin version, timeout and coverage configuration.'
result['limits'] = 'These are focused upstream-reactor unit diagnostics, not full-backend verification or runtime acceptance. The fresh all-module aggregate must separately prove coverage; no old or synthesized probes are merged.'
(ROOT/'cache-coverage-capture-review.json').write_text(json.dumps(result, indent=2))
print(json.dumps({'runs': result['runs'], 'parentStackFinding': result['parentStackFinding'], 'checkoutPomUnchanged': result['checkoutPomUnchanged']}, indent=2))
