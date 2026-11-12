"""Offline diagnostics only; immutable campaign mounted read-only. No acceptance override."""
from array import array
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
from pathlib import Path
import re

METRICS = ('http_req_duration', 'http_req_blocked', 'http_req_connecting', 'http_req_tls_handshaking',
           'http_req_sending', 'http_req_waiting', 'http_req_receiving')
REQUIRED = frozenset((*METRICS, 'http_reqs'))

def stamp(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()

def iso(value):
    return datetime.fromtimestamp(value, timezone.utc).isoformat()

def percentile(ordered, p):
    if not ordered:
        return None
    index = (len(ordered)-1)*p
    low, high = math.floor(index), math.ceil(index)
    return ordered[low]+(ordered[high]-ordered[low])*(index-low)

def stats(values):
    if not values:
        return {'count':0}
    ordered = sorted(values)
    return {'count':len(values),'meanMs':math.fsum(values)/len(values),'p50Ms':percentile(ordered,.5),
            'p95Ms':percentile(ordered,.95),'p99Ms':percentile(ordered,.99),'maxMs':ordered[-1],
            'over800ms':sum(v>800 for v in values),'over5000ms':sum(v>5000 for v in values),
            'negativeValues':sum(v<0 for v in values)}

def request_key(data):
    tags = data.get('tags',{})
    return (data['time'],tags.get('name'),tags.get('status'),tags.get('scenario'))

def request_record(key, values):
    assert set(values) == REQUIRED and values['http_reqs'] == 1
    end = stamp(key[0])
    total = values['http_req_duration']+values['http_req_blocked']
    assert total > 0
    return {'end':end,'start':end-total/1000,'durationMs':values['http_req_duration'],
            'totalHttpMs':total,'waitingMs':values['http_req_waiting'], 'blockedMs':values['http_req_blocked'],
            'route':key[1],'status':key[2],'scenario':key[3]}

def analyze(campaign, output):
    root = campaign/'load'
    read = lambda name:json.loads((root/name).read_text(encoding='utf-8-sig'))
    summary = read('summary.json')
    expected_hash = read('raw-audit.json')['rawSha256']
    end = stamp(summary['finishedAt'])
    origin = end-summary['summary']['state']['testRunDurationMs']/1000
    output.mkdir(exist_ok=False)
    full = {m:array('d') for m in METRICS}
    by_minute = defaultdict(lambda:{m:array('d') for m in METRICS})
    by_route = defaultdict(lambda:{m:array('d') for m in METRICS})
    minute_counts = defaultdict(Counter)
    seconds = defaultdict(Counter)
    statuses = Counter()
    pending, seen = {}, set()
    intervals, drops, vu_samples = [], [], []
    firsts, slow = {}, Counter()
    custom_latency = array('d')
    sha = hashlib.sha256()
    paired = setup = errors = custom_requests = 0
    slow_windows = defaultdict(Counter)
    with (root/'samples.jsonl').open('rb') as stream:
        for line in stream:
            sha.update(line)
            row = json.loads(line)
            if row.get('type') != 'Point':
                continue
            metric, data = row['metric'], row['data']
            if metric == 'browse_latency':
                custom_latency.append(data['value'])
            elif metric == 'browse_requests':
                custom_requests += data['value']
            elif metric == 'browse_errors':
                errors += data['value']
            elif metric == 'dropped_iterations':
                at = stamp(data['time'])
                drops.append((at,data['value']))
                seconds[math.floor(at-origin)]['drops'] += data['value']
                minute_counts[math.floor(at/60)*60]['drops'] += data['value']
                if data['value']:
                    firsts['drop'] = min(firsts.get('drop',at),at)
            elif metric == 'vus':
                at = stamp(data['time'])
                vu_samples.append((at,data['value']))
                seconds[math.floor(at-origin)]['peakReportedVUs'] = max(seconds[math.floor(at-origin)]['peakReportedVUs'],data['value'])
            if metric not in REQUIRED:
                continue
            key = request_key(data)
            assert key not in seen, 'Duplicate fully-paired HTTP request key'
            values = pending.setdefault(key,{})
            assert metric not in values, 'Ambiguous HTTP timestamp/tags collision'
            values[metric] = data['value']
            if set(values) != REQUIRED:
                continue
            item = request_record(key,values)
            seen.add(key)
            pending.pop(key)
            paired += 1
            for name in METRICS:
                full[name].append(values[name])
            if item['route'] == 'setup catalogue':
                setup += 1
                continue
            assert item['scenario'] == 'scaling' and item['route'] in ('product list','product detail')
            minute = math.floor(item['end']/60)*60
            sec = math.floor(item['end']-origin)
            seconds[sec]['httpCompletions'] += 1
            seconds[math.floor(item['start']-origin)]['inferredHttpStarts'] += 1
            minute_counts[minute]['httpCompletions'] += 1
            statuses[item['status']] += 1
            for name in METRICS:
                by_minute[minute][name].append(values[name])
                by_route[item['route']][name].append(values[name])
            intervals.append((item['start'],1))
            intervals.append((item['end'],-1))
            for label,condition,at in (
                ('slow800msStart',item['totalHttpMs']>800,item['start']),
                ('slow5000msStart',item['totalHttpMs']>5000,item['start']),
                ('noHttpResponseCompletion',item['status']=='0',item['end']),
                ('noHttpResponseStart',item['status']=='0',item['start']),
                ('http503Completion',item['status']=='503',item['end'])):
                if condition:
                    firsts[label] = min(firsts.get(label,at),at)
            if item['status'] != '200':
                minute_counts[minute]['non200'] += 1
            if item['totalHttpMs'] > 800:
                slow['over800ms'] += 1
                slow['waitingAtLeast80Percent'] += item['waitingMs'] >= .8*item['totalHttpMs']
                slow['blockedAtLeast80Percent'] += item['blockedMs'] >= .8*item['totalHttpMs']
                slow['durationOver5000ms'] += item['totalHttpMs'] > 5000
                slow_windows[math.floor(item['start']/60)*60]['slowStarts'] += 1
                slow_windows[math.floor(item['start']/60)*60]['totalWaitingMs'] += item['waitingMs']
                slow_windows[math.floor(item['start']/60)*60]['totalBlockedMs'] += item['blockedMs']
                slow_windows[math.floor(item['start']/60)*60]['totalHttpMs'] += item['totalHttpMs']
                seconds[math.floor(item['start']-origin)]['slowHttpStarts'] += 1
    assert sha.hexdigest() == expected_hash, 'Original raw bytes differ from sealed audit'
    assert not pending and setup == 1
    assert paired == summary['summary']['metrics']['http_reqs']['values']['count']
    assert paired-setup == custom_requests == summary['summary']['metrics']['browse_requests']['values']['count']
    assert errors == summary['summary']['metrics']['browse_errors']['values']['passes']
    assert sum(n for _,n in drops) == summary['summary']['metrics']['dropped_iterations']['values']['count']
    full_stats = {m:stats(values) for m,values in full.items()}
    for metric,result in full_stats.items():
        actual = summary['summary']['metrics'][metric]['values']
        assert abs(result['meanMs']-actual['avg']) < 1e-6, metric
        assert abs(result['p95Ms']-actual['p(95)']) < 1e-4, metric
    assert stats(custom_latency)['p95Ms'] == summary['summary']['metrics']['browse_latency']['values']['p(95)']
    concurrent, peak, peak_at = 0, 0, None
    for at,change in sorted(intervals):
        concurrent += change
        assert concurrent >= 0
        sec = math.floor(at-origin)
        seconds[sec]['peakInferredActiveHttp'] = max(seconds[sec]['peakInferredActiveHttp'],concurrent)
        if concurrent > peak:
            peak,peak_at = concurrent,at
    assert concurrent == 0
    server = {m['Id']:{math.floor(stamp(t)/60)*60:v for t,v in zip(m['Timestamps'],m['Values'])}
              for m in read('diagnostic-server-metrics.json')['MetricDataResults']}
    observations = [json.loads(line) for line in (root/'observations.jsonl').read_text().splitlines()]
    timelines = []
    for minute,values in sorted(by_minute.items()):
        obs = [r['services'][0] for r in observations if minute <= stamp(r['observedAt']) < minute+60]
        timelines.append({'minuteUtc':iso(minute),'counts':dict(minute_counts[minute]),
                          'timing':{m:stats(a) for m,a in values.items()},
                          'slowRequestsInferredToStartThisMinute':dict(slow_windows[minute]),
                          'observedRunningRange':[min(o['running'] for o in obs),max(o['running'] for o in obs)] if obs else None,
                          'observedDesiredRange':[min(o['desired'] for o in obs),max(o['desired'] for o in obs)] if obs else None,
                          'server':{name:values.get(minute) for name,values in server.items()}})
    log_counts, log_first, log_last = Counter(), None, None
    for line in (root/'k6.log').read_text(errors='replace').splitlines():
        if 'msg="Request Failed"' not in line:
            continue
        category = 'request-timeout' if 'request timeout' in line else 'other-unclassified'
        log_counts[category] += 1
        match = re.search(r'time="([^\"]+)"',line)
        if match:
            at = stamp(match.group(1))
            log_first = at if log_first is None else min(log_first,at)
            log_last = at if log_last is None else max(log_last,at)
    result = {'generatedAtUtc':datetime.now(timezone.utc).isoformat(),'runLabel':summary['context']['runLabel'],
              'rawSha256Verified':expected_hash,'pairedHttpRequests':paired,'setupRequests':setup,'browseRequests':custom_requests,
              'browseErrors':errors,'statusCounts':dict(statuses),'rawTimingMetricsMatchSummary':True,
              'allHttpTimingMsIncludingSetup':full_stats,'browseWallClockLatencyMs':stats(custom_latency),
              'slowHttpRequests':dict(slow),'firstEventsUtc':{k:iso(v) for k,v in firsts.items()},
              'inferredActiveHttp':{'peak':peak,'peakAtUtc':iso(peak_at),'reportedVuPeak':max(v for _,v in vu_samples),
                  'method':'Sweep [request-end minus (blocked+duration), request-end) intervals; TLS/connect are subsets of blocked, not added again. Not complete iteration/VU occupancy; parsing/body validation and small gaps are omitted.'},
              'clientWarningMetadata':{'categories':dict(log_counts),'firstUtc':iso(log_first) if log_first else None,'lastUtc':iso(log_last) if log_last else None},
              'byRoute':{name:{m:stats(a) for m,a in values.items()} for name,values in by_route.items()},
              'calendarMinuteTimeline':timelines,
              'limitations':['Diagnostic only; original drop/collector/normalization failures remain unchanged. No event-relative acceptance override.',
                   'Client waiting includes origin/edge/transit and cannot alone identify the slow server component.',
                   'CloudWatch minute aggregates and task samples are not per-request traces; running is not proof of target readiness.',
                   'Reconstructed request starts use k6 timings and wall-clock export timestamps, not a captured scheduler-arrival timestamp.']}
    with (output/'timing-analysis.json').open('x') as f:
        json.dump(result,f,indent=2)
    with (output/'second-timeline.jsonl').open('x') as f:
        for second,values in sorted(seconds.items()):
            f.write(json.dumps({'secondFromSummaryExecutionStart':second,'atUtc':iso(origin+second),**values})+'\n')
    print(json.dumps({k:result[k] for k in ('runLabel','pairedHttpRequests','statusCounts','slowHttpRequests','firstEventsUtc','inferredActiveHttp','clientWarningMetadata')},indent=2))

if __name__ == '__main__':
    import sys
    analyze(Path(sys.argv[1]),Path(sys.argv[2]))
