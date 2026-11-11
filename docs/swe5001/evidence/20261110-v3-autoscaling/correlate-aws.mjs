import fs from 'node:fs';
const root = '/results/';
const d = root + 'aws-recovery/swe5001-v3-b84uhoj0/';
const read = name => JSON.parse(fs.readFileSync(d + name, 'utf8'));
const raw = JSON.parse(fs.readFileSync(root + 'raw-audit.json', 'utf8'));
const context = JSON.parse(fs.readFileSync(root + 'generator-context.json', 'utf8'));
const start = Date.parse(context.startedAtUtc), end = Date.parse(context.finishedAtUtc);
const metrics = read('metrics.json');
const obs = fs.readFileSync(d + 'observations.jsonl', 'utf8').trim().split(/\r?\n/).map(JSON.parse);
const changes = []; let previous = '';
for (const o of obs) {
  const s = o.services[0], key = [s.desired,s.running,s.pending,s.taskDefinition].join('/');
  if (key !== previous) changes.push({ at:o.observedAt, desired:s.desired, running:s.running, pending:s.pending, taskDefinition:s.taskDefinition });
  previous = key;
}
const activities = read('scaling-activities.json').ScalingActivities.filter(a => Date.parse(a.StartTime) >= start && Date.parse(a.StartTime) <= end).sort((a,b) => Date.parse(a.StartTime)-Date.parse(b.StartTime));
let from = 1;
const events = activities.filter(a => a.StatusCode === 'Successful' && a.Cause.includes('AlarmHigh')).map(a => {
  const to = Number(a.Description.match(/desired count to (\d+)/)?.[1]);
  if (!(to > from)) throw new Error('Unexpected scale-out description');
  const event = { at:new Date(a.StartTime).toISOString(), from, to, source:'application-autoscaling', activityId:a.ActivityId };
  from = to; return event;
});
const normalized = { schema:1, toolingSha:context.driverSourceSha, runLabel:context.runLabel, events,
  timestampMeaning:'Original successful activity StartTime: desired-capacity adjustment began, not task-ready time. Original EndTime retained separately.',
  observationCoverage:'Partial only, ending 2026-11-09T20:03:47.522113Z; no fabricated post-run observations' };
fs.writeFileSync(root + 'scale-outs.json', JSON.stringify(normalized,null,2), {flag:'wx'});
const minuteKey = at => new Date(at).toISOString().slice(0,16) + ':00Z';
const table = new Map(raw.minutes.map(row => [row.at,{...row}]));
for (const m of metrics.MetricDataResults) for (let i=0;i<m.Timestamps.length;i++) {
  const at = minuteKey(m.Timestamps[i]); if (table.has(at)) table.get(at)[m.Id] = m.Values[i];
}
const map = {m0:'alb_requests_per_min',m1:'alb_requests_per_target_per_min',m3:'alb_p95_seconds',m5:'alb_target_5xx_per_min',m6:'rds_max_connections',m7:'rds_avg_cpu_pct',m8:'rds_min_freeable_bytes',m11:'commerce_avg_cpu_pct',m12:'commerce_avg_memory_pct'};
const headers = ['at','completed','errors','dropped','p95Ms',...Object.values(map)];
fs.writeFileSync(root + 'minute-correlation.csv', headers.join(',')+'\n'+[...table.values()].map(row=>[row.at,row.completed,row.errors,row.dropped,row.p95Ms,...Object.keys(map).map(k=>row[k]??'')].join(',')).join('\n')+'\n',{flag:'wx'});
const result = {schema:1, runLabel:context.runLabel,
  awsArchiveSha256:'324dcb7022acf4821dc0d716c0ebfe2ae678afa9014a60363a96fdc75bdd4f4d',
  interval:{start:context.startedAtUtc,end:context.finishedAtUtc},
  ecs:{samples:obs.length,first:obs[0].observedAt,last:obs.at(-1).observedAt,changes,
    maximumSampledRunning:Math.max(...obs.map(o=>o.services[0].running)),
    incompleteAfter:obs.at(-1).observedAt,fullRunStabilityVerified:false},
  automaticScaleOutActivities:activities.map(a=>({id:a.ActivityId,description:a.Description,start:a.StartTime,end:a.EndTime,status:a.StatusCode})),
  metricWindowNote:'UTC CloudWatch one-minute buckets overlapping the test, including partial first/last buckets. Average CPU/maximum connections use their declared per-minute statistics; not instantaneous guarantees.',
  metrics:metrics.MetricDataResults.map(m=>{
    const points=m.Timestamps.map((at,i)=>({at,value:m.Values[i]})).filter(p=>Date.parse(p.at)>=Math.floor(start/60000)*60000&&Date.parse(p.at)<=end);
    return {id:m.Id,label:m.Label,status:m.StatusCode,points:points.length,
      min:points.length?Math.min(...points.map(p=>p.value)):null,max:points.length?Math.max(...points.map(p=>p.value)):null};
  }),
  connectionPatternMatches:Object.fromEntries(['identity-svc','commerce-svc','notification-svc'].map(name=>[name,read(name+'-connection-errors.json').matches.map(e=>({at:new Date(e.timestamp).toISOString(),eventId:e.eventId}))])),
  connectionPatternLimit:'One combined OR query for too many clients / remaining connection slots / Connection is not available; six commerce matches are not individually classified. Cannot claim no connection exhaustion errors.',
  recovery:read('recovery.json'),
  assessment:{httpAcceptance:'failed independently of event-relative exclusions: errors 2.4219% and 89175 drops',
    scaling:'Successful 1 -> 2 -> 3 -> 4 target-tracking adjustments, with four running sampled at final observation; not continuous post-scale-out readiness evidence',
    bottleneck:'Commerce CPU saturation and multi-second ALB latency support an application-side overload contribution before capacity caught up. RDS sampled maximum 30 connections and average CPU below 24%; no proof of full causal isolation.',
    linearity:'Not established by this under-delivered ramp; no matched one/four-capacity experiment',
    floor:'Minimum two / maximum six and desired two restored; recovery snapshot running three, so settled two-task placement not verified',
    collector:'Process absent; last observation 20:03:47 UTC, empty log, no automatic archive/restoration. Idle shutdown plausible but unproven; stored metrics/history recovered without replacement load',
    acceptanceComplete:false,repeatPerformed:false,teardownPerformed:false}}
fs.writeFileSync(root+'aws-correlation.json',JSON.stringify(result,null,2),{flag:'wx'});
console.log(JSON.stringify({ecs:result.ecs,activities:result.automaticScaleOutActivities,assessment:result.assessment},null,2));
