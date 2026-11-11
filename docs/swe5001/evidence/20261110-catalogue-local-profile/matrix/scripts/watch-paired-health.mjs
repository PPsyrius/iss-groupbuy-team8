import http from 'node:http';
import readline from 'node:readline';
import {appendFileSync} from 'node:fs';
import {setTimeout as delay} from 'node:timers/promises';
const targets=new Map(['paired-base','paired-bulkhead'].map(label=>[`blocbuy-matrix2-${label}`,{label,started:false,done:false}]));
async function probe(target){return await new Promise(resolve=>{const at=new Date().toISOString(),begin=Date.now();
  const r=http.get(`http://${target}:8589/api/actuator/health`,response=>{let body='';response.on('data',x=>body+=x);response.on('end',()=>{
    let status='unknown';try{status=JSON.parse(body).status;}catch{}
    resolve({at,durationMs:Date.now()-begin,httpStatus:response.statusCode,status});
  });});r.setTimeout(5000,()=>r.destroy(Error('Five-second local health deadline exceeded')));r.on('error',e=>resolve({at,durationMs:Date.now()-begin,error:e.message}));
});}
async function collect(target,state){while(!state.done){appendFileSync(`/results/matrix-v2/${state.label}-health.jsonl`,JSON.stringify(await probe(target))+'\n');if(!state.done)await delay(15000);}}
const filters=encodeURIComponent(JSON.stringify({type:['container'],event:['start','pause']}));
http.get({socketPath:'/var/run/docker.sock',path:`/v1.44/events?filters=${filters}`},async response=>{
  const running=[];
  for await(const line of readline.createInterface({input:response})){
    const e=JSON.parse(line),target=e.Actor?.Attributes?.name,state=targets.get(target);if(!state)continue;
    if(e.Action==='start'&&!state.started){state.started=true;running.push(collect(target,state));}
    if(e.Action==='pause'){state.done=true;if([...targets.values()].every(s=>s.done)){response.destroy();await Promise.all(running);console.log('Paired health samples complete; health policy unchanged.');return;}}
  }
}).on('error',e=>{console.error(e.message);process.exitCode=1;});
