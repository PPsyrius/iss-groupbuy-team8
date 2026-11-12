import fs from 'node:fs';
const root='/repo/docs/swe5001/evidence/20261110-v3-autoscaling';
const csv=fs.readFileSync(`${root}/minute-correlation.csv`,'utf8').trim().split(/\r?\n/);
const headings=csv.shift().split(',');
const rows=csv.map(line=>Object.fromEntries(line.split(',').map((value,i)=>[headings[i],value])));
const timing=JSON.parse(fs.readFileSync(`${root}/timing-investigation.json`));
const correlation=JSON.parse(fs.readFileSync(`${root}/aws-correlation.json`));
const actualFirst=correlation.automaticScaleOutActivities[0];
const threshold=70;
const candidates=rows.filter(r=>Number(r.commerce_avg_cpu_pct)>=threshold && Date.parse(r.at)<Date.parse(actualFirst.start)).map(r=>({
  bucketStart:r.at,bucketEnd:new Date(Date.parse(r.at)+60000).toISOString(),cpuAveragePercent:Number(r.commerce_avg_cpu_pct),
  errorsCompletedInBucket:Number(r.errors),droppedInBucket:Number(r.dropped)}));
const result={kind:'offline-signal-review-not-counterfactual-AWS-scaling',threshold,candidates,
  firstFailure:timing.firstError,firstDrop:timing.firstDrop,actualFirstScaleOut:actualFirst,
  taskGrowthFirstSample:correlation.ecs.changes.find(r=>r.running===2),
  budget:{maximumRuntimePools:55,ownerReservation:3,operatorReservation:1,safetyReservation:15,postgresReserved:5,maximumConnections:79},
  findings:[
    'CPU already exceeds 70% in the 19:47 minute with no errors/drops, then falls below threshold: cold/JIT sensitivity and unnecessary scale-out cost must be measured.',
    'CPU again exceeds threshold in 19:50 and 19:51 before 19:53 failures; a different signal can potentially buy headroom, not guarantee it.',
    'Bucket end is only the earliest theoretical decision boundary, not observed CloudWatch ingestion/evaluation, task launch or JVM readiness.',
    'One-point CPU guard is scale-out-only, but the existing target-tracking policy can still scale in; coexistence/cooldown behavior needs AWS validation.',
    'Default off, commerce only, maximum six unchanged; shared pools/backend defaults and error/drop/latency gates unchanged.',
    'A complete one-to-four 80%-linearity experiment is not supplied by this replay or the original 600-RPS V3 ramp.'
  ],authorization:'No deployment, session startup or AWS query performed for this offline review.'};
fs.writeFileSync('/results/matrix-v2/scaling-review.json',JSON.stringify(result,null,2));
console.log(JSON.stringify({firstCandidate:candidates[0],actualFirstScaleOut:actualFirst.start,firstFailure:timing.firstError},null,2));
