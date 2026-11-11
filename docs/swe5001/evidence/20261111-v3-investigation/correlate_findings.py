"""Correlate independent historical sources without asserting a unique root cause."""
from datetime import datetime,timezone
import csv
import json
from pathlib import Path
import re

ROOT,OUT=Path('/analysis'),Path('/analysis/correlation-v1')
CAMPAIGN=Path('/campaign')
def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))
def ts(value):
    return datetime.fromisoformat(value.replace('Z','+00:00')).timestamp()
def iso(value):
    return datetime.fromtimestamp(value,timezone.utc).isoformat()
OUT.mkdir(exist_ok=False)
timing=read(ROOT/'r2-timing-v2/timing-analysis.json')
summary=read(CAMPAIGN/'load/summary.json')
end=ts(summary['finishedAt'])
start=end-summary['summary']['state']['testRunDurationMs']/1000
activities=read(CAMPAIGN/'load/scaling-activities.json')['ScalingActivities']
activities=sorted([a for a in activities if start<=ts(a['StartTime'])<=end],key=lambda a:ts(a['StartTime']))
observations=[json.loads(line) for line in (CAMPAIGN/'load/observations.jsonl').read_text().splitlines()]
changes=[]
previous=1
for activity in activities:
    target=int(re.fullmatch(r'Setting desired count to (\d+)\.',activity['Description']).group(1))
    reached=[r for r in observations if ts(r['observedAt'])>=ts(activity['StartTime']) and r['services'][0]['running']>=target]
    changes.append({'at':activity['StartTime'],'fromPreviousRequestedDesired':previous,'toRequestedDesired':target,
                    'policy':'CPU' if 'cpu-headroom' in activity['Cause'] else 'request',
                    'originalStatus':activity['StatusCode'],'originalStatusMessage':activity['StatusMessage'],
                    'activityEnd':activity['EndTime'],'activityDurationSeconds':ts(activity['EndTime'])-ts(activity['StartTime']),
                    'firstSampleAtOrAboveRequestedRunningCount':reached[0]['observedAt'] if reached else None,
                    'secondsUntilThatRunningSample':ts(reached[0]['observedAt'])-ts(activity['StartTime']) if reached else None})
    previous=target
alarm_rows=[]
for alarm in read(CAMPAIGN/'load/alarm-control-history.json')['alarms']:
    for history in alarm['items']:
        if history['HistoryItemType']!='StateUpdate':
            continue
        value=json.loads(history['HistoryData'])
        state=value.get('newState',{})
        if state.get('stateValue')!='ALARM':
            continue
        data=state.get('stateReasonData',{})
        if not start<=ts(history['Timestamp'])<=end:
            continue
        points=data.get('evaluatedDatapoints',[])
        dates=[ts(p['timestamp']) for p in points]
        period=data.get('period')
        alarm_rows.append({'alarm':alarm['alarmName'],'decisionAt':history['Timestamp'],'periodSeconds':period,
                           'evaluatedPoints':points,'threshold':data.get('threshold'),
                           'secondsAfterLatestEvaluatedBucketStart':ts(history['Timestamp'])-max(dates) if dates else None,
                           'secondsAfterLatestEvaluatedBucketEnd':ts(history['Timestamp'])-max(dates)-period if dates and period else None})
perf=read(ROOT/'cloud-read-v1/container-performance-metadata.json')['records']
task_rows=[]
for item in perf:
    if item.get('Type')=='Task' and item.get('CpuReserved'):
        task_rows.append({'at':iso(item['observedAtEpochMs']/1000),'taskId':item['TaskId'],
                          'cpuPercentOfTaskReservation':100*item['CpuUtilized']/item['CpuReserved'],
                          'memoryUtilizedMiB':item.get('MemoryUtilized')})
metrics=read(ROOT/'cloud-read-v1/metrics.json')['MetricDataResults']
lookup={m['Id']:dict(zip(m['Timestamps'],m['Values'])) for m in metrics}
edge=read(ROOT/'edge-read-v1/metrics.json')['MetricDataResults']
edge_lookup={m['Id']:dict(zip(m['Timestamps'],m['Values'])) for m in edge}
minutes=[]
for minute in timing['calendarMinuteTimeline']:
    at=minute['minuteUtc']
    row={'minuteUtc':at,'completed':minute['counts'].get('httpCompletions',0),'non200':minute['counts'].get('non200',0),
         'drops':minute['counts'].get('drops',0),'clientDurationP95Ms':minute['timing']['http_req_duration']['p95Ms'],
         'clientWaitingP95Ms':minute['timing']['http_req_waiting']['p95Ms'],
         'albTargetP95Ms':minute['server']['alb_target_p95']*1000 if minute['server'].get('alb_target_p95') is not None else None,
         'albTargetMaxMs':lookup['target_response_max'].get(at,0)*1000 if at in lookup['target_response_max'] else None,
         'serviceCpuAveragePercent':minute['server'].get('commerce_cpu'),
         'maxObservedTaskCpuPercent':max((r['cpuPercentOfTaskReservation'] for r in task_rows if r['at']==at),default=None),
         'healthyTargetsMinimum':lookup['healthy_hosts'].get(at),'unhealthyTargetsMaximum':lookup['unhealthy_hosts'].get(at),
         'cloudfrontRequestsAllPaths':edge_lookup['requests'].get(at),'cloudfront5xxPercentAllPaths':edge_lookup['errors_5xx'].get(at)}
    minutes.append(row)
early=[m for m in minutes if '08:25:' in m['minuteUtc'] or '08:26:' in m['minuteUtc'] or '08:27:' in m['minuteUtc']]
burst=[m for m in minutes if '08:28:' in m['minuteUtc']][0]
slow=timing['slowHttpRequests']
first=timing['firstEventsUtc']
result={'at':datetime.now(timezone.utc).isoformat(),'runLabel':timing['runLabel'],
        'sourceRawSha256':timing['rawSha256Verified'],
        'mechanism':{'slowHttpRequests':slow['over800ms'],'waitingDominatedSlowRequests':slow['waitingAtLeast80Percent'],
                     'waitingDominatedPercent':100*slow['waitingAtLeast80Percent']/slow['over800ms'],
                     'peakInferredActiveHttp':timing['inferredActiveHttp']['peak'],
                     'peakInferredActiveHttpAt':timing['inferredActiveHttp']['peakAtUtc'],
                     'firstDropAt':first['drop'],
                     'millisecondsFromFirst1000ActiveToFirstDrop':1000*(ts(first['drop'])-ts(timing['inferredActiveHttp']['peakAtUtc'])),
                     'firstSlowRequestInferredStart':first['slow800msStart'],
                     'firstTimeoutWarningAt':timing['clientWarningMetadata']['firstUtc']},
        'scalingDecisions':changes,'alarmEvaluationDelays':alarm_rows,
        'firstScaleOutSecondsAfterFirstSlowRequestInferredStart':ts(activities[0]['StartTime'])-ts(first['slow800msStart']),
        'firstScaleOutSecondsAfterFirstDrop':ts(activities[0]['StartTime'])-ts(first['drop']),
        'earlyClientOriginMismatch':{'calendarMinutes':early,'non200Completions':sum(m['non200'] for m in early),
                                   'interpretation':'Early client timeouts are not explained by retained ALB target-response timings alone. TargetResponseTime does not cover every client/edge/ALB-pre-target phase and has no per-request correlation here.'},
        'largestDropBurst':{'calendarMinute':burst,'shareOfAllDropsPercent':100*burst['drops']/sum(m['drops'] for m in minutes),
                           'interpretation':'Most drops coincide with the first newly admitted task and high per-task CPU, hidden by service averaging. Correlation supports a cold-task/transition bottleneck hypothesis, not a unique causal proof.'},
        'highestTaskCpuMinutes':sorted(task_rows,key=lambda r:r['cpuPercentOfTaskReservation'],reverse=True)[:12],
        'existingObservability':{'edgeSettings':read(ROOT/'edge-read-v1/observability-settings.json'),
                                 'albSettings':read(ROOT/'edge-read-v1/alb-observability-settings.json'),
                                 'monitoringSubscription':read(ROOT/'edge-read-v1/monitoring-subscription.json'),
                                 'originLatencyMetricPoints':len([m for m in edge if m['Id']=='origin_latency_p95'][0]['Values'])},
        'calendarMinuteTimeline':minutes,
        'conclusion':'Late scaling and VU exhaustion are established; newly added-task CPU contention is observed. Earlier scaling alone fixing zero drops/p95 is NOT proven, because early client/origin timing mismatch and missing per-request edge/access/JVM evidence remain.',
        'runtimeMutationPerformed':False}
with (OUT/'findings.json').open('x') as f:
    json.dump(result,f,indent=2)
with (OUT/'minute-correlation.csv').open('x',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(minutes[0]))
    writer.writeheader()
    writer.writerows(minutes)
print(json.dumps({k:result[k] for k in ('mechanism','scalingDecisions','alarmEvaluationDelays','firstScaleOutSecondsAfterFirstSlowRequestInferredStart','firstScaleOutSecondsAfterFirstDrop','largestDropBurst','highestTaskCpuMinutes','conclusion')},indent=2))
