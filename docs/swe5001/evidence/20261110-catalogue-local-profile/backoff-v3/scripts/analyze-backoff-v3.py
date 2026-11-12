"""Independent streaming audit of local diagnostic cases; failures stay failures."""
from collections import Counter,defaultdict
from datetime import datetime
import hashlib,json,math,sys
from pathlib import Path

ROOT=Path('/results/matrix-v2')
def stamp(value): return datetime.fromisoformat(value.replace('Z','+00:00')).timestamp()
def percentile(values,p):
    if not values:return None
    values=sorted(values);position=(len(values)-1)*p;low=int(position)
    return values[low]+(values[min(low+1,len(values)-1)]-values[low])*(position-low)
def metric(summary,name,key):return summary.get(name,{}).get('values',{}).get(key)
def gauge(metrics,name):
    values=[value for key,value in metrics.items() if key==name or key.startswith(name+'{')]
    return sum(values) if values else None
kind='backoff-v3'
index=json.loads((ROOT/f'{kind}-index.json').read_text())
results=[]
for run in index['runs']:
    label=run['label'];folder=ROOT/label
    if run['exitCode'] not in (0,99):
        results.append({'label':label,'usableMeasurement':False,'reason':'Generator setup/output failed; zero-valued empty metrics are not a pass','exitCode':run['exitCode']});continue
    if not (folder/'summary.json').exists():
        results.append({'label':label,'usableMeasurement':False,'reason':'Missing summary','exitCode':run['exitCode']});continue
    report=json.loads((folder/'summary.json').read_text());summary=report['summary']['metrics']
    counts=Counter();latencies=[];seconds=defaultdict(lambda:{'requests':0,'errors':0,'drops':0,'latencies':[]})
    for line in (folder/'samples.jsonl').open():
        item=json.loads(line)
        if item['type']!='Point':continue
        name=item['metric'];point=item['data'];value=point['value'];second=int(stamp(point['time']))
        if name in ('browse_requests','browse_errors','dropped_iterations','list_requests','detail_requests'):
            counts[name]+=int(value)
            key={'browse_requests':'requests','browse_errors':'errors','dropped_iterations':'drops'}.get(name)
            if key:seconds[second][key]+=int(value)
        if name=='browse_latency':latencies.append(value);seconds[second]['latencies'].append(value)
    assert counts['browse_requests']==metric(summary,'browse_requests','count'),label
    assert counts['dropped_iterations']==metric(summary,'dropped_iterations','count'),label
    expected_errors=metric(summary,'browse_errors','rate')*counts['browse_requests']
    assert abs(counts['browse_errors']-expected_errors)<1e-5,label
    assert abs(percentile(latencies,.95)-metric(summary,'browse_latency','p(95)'))<1e-6,label
    assert counts['list_requests']+counts['detail_requests']==counts['browse_requests'],label
    monitor=[json.loads(line) for line in (folder/'monitor.jsonl').read_text().splitlines()]
    metrics=[r['metrics'] for r in monitor if 'metrics' in r]
    gauges={name:{'first':gauge(metrics[0],name),'last':gauge(metrics[-1],name),'max':max((value for m in metrics if (value:=gauge(m,name)) is not None),default=None)}
        for name in ('hikaricp_connections_pending','hikaricp_connections_active','hikaricp_connections_timeout_total',
                     'hikaricp_connections_acquire_seconds_sum','hikaricp_connections_acquire_seconds_count',
                     'hikaricp_connections_acquire_seconds_max','jvm_threads_live_threads','jvm_gc_pause_seconds_count',
                     'jvm_gc_pause_seconds_sum','jvm_gc_pause_seconds_max')} if metrics else {}
    resources={}
    for name in sorted(set(r['name'] for row in monitor for r in row['resources'])):
        samples=[r for row in monitor for r in row['resources'] if r['name']==name and 'error' not in r]
        cpus=[r['cpuPercentOneCore'] for r in samples if r.get('cpuPercentOneCore') is not None and r['cpuPercentOneCore']>=0]
        throttle=[r['throttling']['throttled_periods'] for r in samples if isinstance(r.get('throttling'),dict) and 'throttled_periods' in r['throttling']]
        resources[name]={'samples':len(samples),'cpuPercentOneCoreMedian':percentile(cpus,.5),'cpuPercentOneCoreMax':max(cpus) if cpus else None,
                         'memoryBytesMax':max((r['memoryBytes'] for r in samples if r.get('memoryBytes') is not None),default=None),
                         'missingMemorySamples':sum(r.get('memoryBytes') is None for r in samples),
                         'invalidNegativeCpuSamples':sum(r.get('cpuPercentOneCore') is not None and r['cpuPercentOneCore']<0 for r in samples),
                         'throttledPeriodsDelta':throttle[-1]-throttle[0] if len(throttle)>1 and throttle[-1]>=throttle[0] else None}
    result={'label':label,'evidenceKind':'local-diagnostic-not-AWS-acceptance','exitCode':run['exitCode'],
        'nominalRequests':report['rate']*report['seconds'],'rawCounts':dict(counts),'latencyMs':{k:metric(summary,'browse_latency',k) for k in ('avg','med','p(95)','p(99)','max')},
        'usableMeasurement':True,'allDiagnosticScreensPassed':run['exitCode']==0,'resources':resources,'gauges':gauges,
        'monitorSamples':len(monitor),'metricReadFailures':sum('metricsError' in r for r in monitor),
        'secondsByCompletion':[{ 'at':datetime.fromtimestamp(second).astimezone().isoformat(),**{k:v for k,v in values.items() if k!='latencies'},'p95Ms':percentile(values['latencies'],.95)} for second,values in sorted(seconds.items())]}
    if gauges and all(gauges[name][edge] is not None for name in ('hikaricp_connections_acquire_seconds_count','hikaricp_connections_acquire_seconds_sum') for edge in ('first','last')):
        count=gauges['hikaricp_connections_acquire_seconds_count']['last']-gauges['hikaricp_connections_acquire_seconds_count']['first']
        total=gauges['hikaricp_connections_acquire_seconds_sum']['last']-gauges['hikaricp_connections_acquire_seconds_sum']['first']
        result['sampleBracketMeanAcquireMs']=1000*total/count if count else None
    (folder/'audit.json').write_text(json.dumps(result,indent=2));results.append(result)
    print(label,'requests',counts['browse_requests'],'errors',counts['browse_errors'],'drops',counts['dropped_iterations'],'p95ms',result['latencyMs']['p(95)'])
output={'kind':'independent-local-matrix-audit','group':kind,'refusedFaultCases':index.get('refusedFaultCases'),'baseSourceSha':'93984f615df79666f4c3ba62f4d6ae90fbf1f4e1',
        'sourceAttribution':'backoff-base-a/b use released baseline; backoff-v3-a/b use separately hashed uncommitted failure-triggered backoff v3. Both use default JVM/full logging, not the rejected fixed-slot bulkheads or C1/logging diagnostics.',
        'artifactHashes':index.get('artifactHashes'),'cases':results,
        'collectionComplete':index.get('completed') is True and all(r['usableMeasurement'] for r in results),
        'limits':'No AWS acceptance/capacity/linearity inference. Fault-window samples are grouped by completion, not request start. Resource/gauge brackets exclude unsampled endpoints; missing readings are not zero.'}
(ROOT/f'{kind}-analysis.json').write_text(json.dumps(output,indent=2))
