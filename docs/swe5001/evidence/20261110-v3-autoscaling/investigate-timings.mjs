import fs from 'node:fs';
import readline from 'node:readline';
const root='/results/';
const cutoff=Date.parse('2026-11-09T20:03:47.522113Z');
const names=['http_req_blocked','http_req_connecting','http_req_tls_handshaking','http_req_waiting','http_req_receiving','http_req_duration'];
const collected=new Map();
const minute=new Map();
const phases={beforeObserverLoss:{requests:0,errors:0,drops:0},afterObserverLoss:{requests:0,errors:0,drops:0}};
let firstError=null,firstDrop=null;
for await(const line of readline.createInterface({input:fs.createReadStream(root+'samples.jsonl'),crlfDelay:Infinity})){
  if(!line)continue;
  const p=JSON.parse(line); if(p.type!=='Point')continue;
  const at=Date.parse(p.data.time), value=p.data.value;
  const phase=at<=cutoff?phases.beforeObserverLoss:phases.afterObserverLoss;
  if(p.metric==='browse_requests')phase.requests+=value;
  else if(p.metric==='browse_errors'&&value){phase.errors+=value;firstError??=p.data.time;}
  else if(p.metric==='dropped_iterations'&&value){phase.drops+=value;firstDrop??=p.data.time;}
  if(!names.includes(p.metric))continue;
  const status=String(p.data.tags?.status??'unknown');
  const key=p.metric+'/'+status;
  if(!collected.has(key))collected.set(key,[]);
  collected.get(key).push(value);
  const bucket=p.data.time.slice(0,16)+':00Z';
  const mk=bucket+'/'+p.metric;
  if(!minute.has(mk))minute.set(mk,{at:bucket,metric:p.metric,values:[]});
  minute.get(mk).values.push(value);
}
function statistics(values){
  values.sort((a,b)=>a-b);
  const quantile=p=>{const at=(values.length-1)*p,low=Math.floor(at);return values[low]+(values[Math.ceil(at)]-values[low])*(at-low);};
  return {count:values.length,averageMs:values.reduce((a,b)=>a+b,0)/values.length,p50Ms:quantile(.5),p95Ms:quantile(.95),maxMs:values.at(-1)};
}
const result={schema:1,runLabel:'20261110-v3-194543',kind:'Offline analysis of original raw samples; no new HTTP workload',
  observerCutoff:'2026-11-09T20:03:47.522113Z',firstError,firstDrop,phases,
  durationByStatus:Object.fromEntries([...collected].map(([key,values])=>[key,statistics(values)])),
  minuteTimings:[...minute.values()].map(row=>({at:row.at,metric:row.metric,...statistics(row.values)})),
  limitations:'k6 timing breakdown is client-side evidence, not an attribution of timeout cause; status zero is transport/timeout, not a server status. Phase totals are grouped by sample completion timestamp.'};
fs.writeFileSync(root+'timing-investigation.json',JSON.stringify(result,null,2),{flag:'wx'});
console.log(JSON.stringify({...result,minuteTimings:undefined},null,2));
