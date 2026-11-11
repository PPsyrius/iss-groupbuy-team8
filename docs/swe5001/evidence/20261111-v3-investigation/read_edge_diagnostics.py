"""Read-only edge/ALB observability settings and existing global CloudFront metrics."""
from datetime import datetime,timedelta,timezone
import json
from pathlib import Path
import re
import subprocess

OUT=Path('/analysis/edge-read-v1')
ROOT=Path('/campaign')
READS={('sts','get-caller-identity'),('cloudfront','get-distribution-config'),('cloudfront','get-monitoring-subscription'),
       ('elbv2','describe-load-balancer-attributes'),('cloudwatch','get-metric-data')}
def aws(*args,region='ap-southeast-1'):
    assert args[:2] in READS and region in ('ap-southeast-1','us-east-1')
    result=subprocess.run(['aws','--region',region,'--output','json','--no-cli-pager',*args],capture_output=True,text=True,timeout=180)
    if result.returncode:
        code=re.search(r'An error occurred \(([A-Za-z0-9_.:-]{1,80})\)',result.stderr or '')
        raise RuntimeError('Historical read failed: '+args[0]+' '+args[1]+(' ('+code.group(1)+')' if code else ''))
    return json.loads(result.stdout)
def save(name,value):
    with (OUT/name).open('x') as stream:
        json.dump(value,stream,indent=2)
OUT.mkdir(exist_ok=False)
save('intent.json',{'at':datetime.now(timezone.utc).isoformat(),'scope':'Read-only selected edge/ALB settings and original-window CloudFront metrics. No custom headers/secrets, application requests, new monitoring subscriptions or runtime changes.'})
try:
    assert aws('sts','get-caller-identity')['Account']=='402178232802'
    report=json.loads((ROOT/'load/summary.json').read_text())
    distribution=report['preflight']['routing']['edge']['id']
    assert distribution=='E3N39GHVFV6CT3'
    config=aws('cloudfront','get-distribution-config','--id',distribution,'--query',
        'DistributionConfig.{loggingEnabled:Logging.Enabled,httpVersion:HttpVersion,origins:Origins.Items[].{id:Id,connectionAttempts:ConnectionAttempts,connectionTimeout:ConnectionTimeout,readTimeout:CustomOriginConfig.OriginReadTimeout,keepaliveTimeout:CustomOriginConfig.OriginKeepaliveTimeout}}')
    save('observability-settings.json',config)
    try:
        subscription=aws('cloudfront','get-monitoring-subscription','--distribution-id',distribution)
    except RuntimeError as error:
        if '(NoSuchMonitoringSubscription)' not in str(error):
            raise
        subscription={'state':'absent','awsErrorCode':'NoSuchMonitoringSubscription','readPerformedOnce':True}
    save('monitoring-subscription.json',subscription)
    alb=report['preflight']['routing']['targetGroups'][0]['loadBalancerArns'][0]
    attrs=aws('elbv2','describe-load-balancer-attributes','--load-balancer-arn',alb,
              '--query','Attributes[?Key==`access_logs.s3.enabled` || Key==`idle_timeout.timeout_seconds` || Key==`client_keep_alive.seconds`]')
    save('alb-observability-settings.json',attrs)
    dimensions=[{'Name':'DistributionId','Value':distribution},{'Name':'Region','Value':'Global'}]
    specifications=[('requests','Requests','Sum'),('errors_5xx','5xxErrorRate','Average'),('errors_total','TotalErrorRate','Average'),('origin_latency_p95','OriginLatency','p95')]
    queries=[{'Id':ident,'Label':name+'/'+stat,'MetricStat':{'Metric':{'Namespace':'AWS/CloudFront','MetricName':name,'Dimensions':dimensions},'Period':60,'Stat':stat},'ReturnData':True}
             for ident,name,stat in specifications]
    save('metric-queries.json',queries)
    end=datetime.fromisoformat(report['finishedAt'].replace('Z','+00:00'))
    start=end-timedelta(milliseconds=report['summary']['state']['testRunDurationMs'])
    metrics=aws('cloudwatch','get-metric-data','--metric-data-queries','file://'+str(OUT/'metric-queries.json'),
                '--start-time',start.isoformat(),'--end-time',end.isoformat(),'--scan-by','TimestampAscending',region='us-east-1')
    save('metrics.json',metrics)
    save('completed.json',{'at':datetime.now(timezone.utc).isoformat(),'runtimeMutationPerformed':False,
         'limitations':'Live observability settings read after the run, not historical per-request traces. Distribution-level CloudFront metrics include all paths/viewers, not exclusively k6 browse requests. Missing optional metrics are not zero latency.'})
    print(json.dumps({'settings':config,'albAttributes':attrs,'monitoringSubscription':subscription,
                      'metrics':[{'id':r['Id'],'status':r['StatusCode'],'points':len(r['Values'])} for r in metrics['MetricDataResults']]},indent=2))
except Exception as error:
    save('failure.json',{'at':datetime.now(timezone.utc).isoformat(),'errorType':type(error).__name__,
          'reason':str(error) if isinstance(error,(RuntimeError,AssertionError)) else 'Read/projection failed',
          'partialEvidencePreserved':True,'runtimeMutationPerformed':False})
    raise SystemExit('Edge read failed; partial metadata retained; no retry or configuration change.') from None
