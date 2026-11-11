"""Bounded read-only historical diagnostics; project numeric/exception metadata only.

No application requests, exec, SQL, settings changes, private inputs, or raw log retention.
"""
from collections import Counter,defaultdict
from datetime import datetime,timedelta,timezone
import json
from pathlib import Path
import re
import subprocess

ROOT,OUT = Path('/campaign'),Path('/analysis/cloud-read-v1')
READS = {('sts','get-caller-identity'),('logs','filter-log-events'),('cloudwatch','get-metric-data')}
def aws(*args):
    assert args[:2] in READS,'Non-allowlisted operation'
    result = subprocess.run(['aws','--region','ap-southeast-1','--output','json','--no-cli-pager',*args],capture_output=True,text=True,timeout=180)
    if result.returncode:
        code = re.search(r'An error occurred \(([A-Za-z0-9_.:-]{1,80})\)',result.stderr or '')
        raise RuntimeError('Read failed: '+args[0]+' '+args[1]+(' ('+code.group(1)+')' if code else ''))
    return json.loads(result.stdout)
def save(name,value):
    with (OUT/name).open('x') as stream:
        json.dump(value,stream,indent=2)
def parse(value):
    return datetime.fromisoformat(value.replace('Z','+00:00'))
OUT.mkdir(exist_ok=False)
save('intent.json',{'at':datetime.now(timezone.utc).isoformat(),'scope':'Historical CloudWatch metric/log reads only, account 402178232802/ap-southeast-1, original r2 workload window. No application load/settings/teardown.',
                    'awsMaxAttempts':1,'rawMessagesPersisted':False})
try:
    assert aws('sts','get-caller-identity')['Account']=='402178232802'
    report = json.loads((ROOT/'load/summary.json').read_text())
    end = parse(report['finishedAt'])
    start = end-timedelta(milliseconds=report['summary']['state']['testRunDurationMs'])
    group = report['preflight']['routing']['targetGroups'][0]
    alb = group['loadBalancerArns'][0].split(':loadbalancer/')[1]
    tg = group['arn'].split(':')[-1]
    lb = [{'Name':'LoadBalancer','Value':alb}]
    target = lb+[{'Name':'TargetGroup','Value':tg}]
    db = [{'Name':'DBInstanceIdentifier','Value':'blocbuy-swe5001'}]
    cache_id=json.loads((ROOT/'prechecks/subnets/cache-node.json').read_text())[0]['id']
    cache=[{'Name':'CacheClusterId','Value':cache_id},{'Name':'CacheNodeId','Value':'0001'}]
    specs=[('elb_5xx','AWS/ApplicationELB','HTTPCode_ELB_5XX_Count','Sum',lb),
           ('target_connection_errors','AWS/ApplicationELB','TargetConnectionErrorCount','Sum',target),
           ('healthy_hosts','AWS/ApplicationELB','HealthyHostCount','Minimum',target),
           ('unhealthy_hosts','AWS/ApplicationELB','UnHealthyHostCount','Maximum',target),
           ('target_response_max','AWS/ApplicationELB','TargetResponseTime','Maximum',target),
           ('target_response_samples','AWS/ApplicationELB','TargetResponseTime','SampleCount',target),
           ('new_connections','AWS/ApplicationELB','NewConnectionCount','Sum',lb),
           ('rejected_connections','AWS/ApplicationELB','RejectedConnectionCount','Sum',lb),
           ('db_cpu_credits','AWS/RDS','CPUCreditBalance','Minimum',db),
           ('db_surplus_credits','AWS/RDS','CPUSurplusCreditBalance','Maximum',db),
           ('db_read_latency','AWS/RDS','ReadLatency','Average',db),
           ('db_write_latency','AWS/RDS','WriteLatency','Average',db),
           ('db_disk_queue','AWS/RDS','DiskQueueDepth','Maximum',db),
           ('cache_cpu','AWS/ElastiCache','EngineCPUUtilization','Average',cache),
           ('cache_connections','AWS/ElastiCache','CurrConnections','Maximum',cache),
           ('cache_new_connections','AWS/ElastiCache','NewConnections','Sum',cache)]
    queries=[{'Id':ident,'Label':namespace+'/'+metric+'/'+stat,'MetricStat':{'Metric':{'Namespace':namespace,'MetricName':metric,'Dimensions':dims},'Period':60,'Stat':stat},'ReturnData':True}
             for ident,namespace,metric,stat,dims in specs]
    save('metric-queries.json',queries)
    metrics=aws('cloudwatch','get-metric-data','--metric-data-queries','file://'+str(OUT/'metric-queries.json'),
                '--start-time',start.isoformat(),'--end-time',end.isoformat(),'--scan-by','TimestampAscending')
    save('metrics.json',metrics)
    perf=aws('logs','filter-log-events','--log-group-name','/aws/ecs/containerinsights/blocbuy-swe5001/performance',
             '--start-time',str(int(start.timestamp()*1000)),'--end-time',str(int(end.timestamp()*1000)),
             '--filter-pattern','{ $.ServiceName = "commerce-svc" }')
    keys=('Type','Timestamp','ClusterName','ServiceName','TaskId','ContainerName','TaskDefinitionFamily','TaskDefinitionRevision',
          'CpuUtilized','CpuReserved','MemoryUtilized','MemoryReserved','NetworkRxBytes','NetworkTxBytes','StorageReadBytes','StorageWriteBytes')
    projected=[]
    types=Counter()
    for event in perf['events']:
        value=json.loads(event['message'])
        types[value.get('Type','unknown')]+=1
        projected.append({'observedAtEpochMs':event['timestamp'],**{key:value[key] for key in keys if key in value and isinstance(value[key],(str,int,float))}})
    del perf
    save('container-performance-metadata.json',{'records':projected,'types':types,'rawMessagesRetained':False,
         'limit':'Existing Container Insights records only; missing dimensions are not inferred or a reason to enable new instrumentation.'})
    logs=aws('logs','filter-log-events','--log-group-name','/blocbuy/swe5001/commerce-svc',
             '--start-time',str(int(start.timestamp()*1000)),'--end-time',str(int(end.timestamp()*1000)),
             '--filter-pattern','?"WARN" ?"ERROR" ?"Best-offer cache unavailable" ?"503"')
    categories=Counter()
    records=[]
    for event in logs['events']:
        raw=event.get('message','')
        try:
            document=json.loads(raw)
        except ValueError:
            document={}
        text=raw.lower()
        labels=[]
        for label,terms in (
            ('cache-unavailable',('best-offer cache unavailable',)),('pool-wait',('connection is not available',)),
            ('instance-connection-limit',('too many clients','remaining connection slots')),
            ('redis-timeout',('rediscommandtimeoutexception',)),('unhandled-exception',('unhandled exception',)),
            ('client-abort',('clientabortexception','broken pipe','connection reset by peer')),
            ('out-of-memory',('outofmemoryerror',)),('tomcat-overload',('rejectedexecutionexception','executor queue is full'))):
            if any(term in text for term in terms):
                labels.append(label)
        categories.update(labels or ['other-warn-error-or-503-match'])
        classes=sorted(set(re.findall(r'\b(?:[a-z][A-Za-z0-9_]*\.)+[A-Z][A-Za-z0-9_$]*(?:Exception|Error)\b',raw)))
        cause=re.search(r'cause=([A-Za-z0-9_$]+)',raw)
        records.append({'timestamp':event['timestamp'],'eventId':event['eventId'],'logStreamName':event['logStreamName'],
                        'categories':labels,'exceptionClasses':classes,'cacheCauseClass':cause.group(1) if cause else None,
                        'level':document.get('level') if document.get('level') in ('WARN','ERROR','INFO') else None})
    del logs
    save('application-warning-metadata.json',{'matchedEvents':len(records),'categoryCounts':categories,'records':records,
         'rawMessagesRetained':False,'limit':'Filtered log-event counts, not distinct requests. No raw messages, SQL, tokens, request bodies or response payloads retained. Absence of logged errors does not prove absence of pool/dependency stalls.'})
    save('completion.json',{'at':datetime.now(timezone.utc).isoformat(),'result':'historical reads completed','runtimeMutationPerformed':False,
                           'metricSeries':len(metrics['MetricDataResults']),'performanceRecords':len(projected),'warningMatches':len(records)})
    print(json.dumps({'result':'read-only historical diagnostics completed','metricSeries':len(metrics['MetricDataResults']),
                      'performanceTypes':types,'warningCategories':categories,'warningMatches':len(records)},indent=2))
except Exception as error:
    save('failure.json',{'at':datetime.now(timezone.utc).isoformat(),'errorType':type(error).__name__,
         'reason':str(error) if isinstance(error,(RuntimeError,AssertionError)) else 'Read/projection failed; no raw response retained',
         'partialEvidencePreserved':True,'runtimeMutationPerformed':False})
    raise SystemExit('Historical read failed; partial metadata preserved; no retry or runtime mutation.') from None
