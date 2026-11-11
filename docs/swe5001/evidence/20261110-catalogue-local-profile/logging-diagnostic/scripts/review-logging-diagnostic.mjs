// Stream existing private Docker logs, retaining only counts/byte sizes/class names, never messages or stacks.
import {spawn} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
import readline from 'node:readline';
const root=process.env.RESULTS_ROOT;
if(!root?.includes('/Temp/opencode/swe5001-profile-20261110'))throw Error('Explicit local diagnostic results root required');
const index=JSON.parse(readFileSync(`${root}/matrix-v2/logging-diagnostic-index.json`));
const result={kind:'safe-log-volume-diagnosis-not-causal-proof',cases:[],limits:'Byte counts include JSON log envelopes and stack traces. Counts are logs, not independent request counts; no raw log body is retained here.'};
for(const app of [...new Set(index.runs.map(r=>r.app))]){
  const runs=index.runs.filter(r=>r.app===app);
  const rows=Object.fromEntries(runs.map(r=>[r.label,{label:r.label,records:0,bytes:0,errorRecords:0,cacheUnavailableRecords:0,stackTraceCharacters:0,largestStackTraceCharacters:0,errorClasses:{}}]));
  const child=spawn('docker',['logs','--timestamps',app],{stdio:['ignore','pipe','pipe']});
  const completed=new Promise(resolve=>child.on('close',resolve));
  await Promise.all([child.stdout,child.stderr].map(async stream=>{
    for await(const line of readline.createInterface({input:stream})){
      const split=line.indexOf(' ');if(split<0)continue;
      const at=Date.parse(line.slice(0,split));
      const run=runs.find(r=>at>=Date.parse(r.startedAt)&&at<=Date.parse(r.finishedAt));if(!run)continue;
      const row=rows[run.label];row.bytes+=Buffer.byteLength(line)+1;row.records++;
      try{
        const item=JSON.parse(line.slice(split+1));
        const level=item.log?.level||item.level;
        if(level==='ERROR')row.errorRecords++;
        if(String(item.message||'').startsWith('Best-offer cache unavailable;'))row.cacheUnavailableRecords++;
        const stack=item.error?.stack_trace||item.stack_trace||'';
        row.stackTraceCharacters+=stack.length;
        row.largestStackTraceCharacters=Math.max(row.largestStackTraceCharacters,stack.length);
        const type=item.error?.type;
        if(type&&/^[a-zA-Z0-9_.$]+$/.test(type))row.errorClasses[type]=(row.errorClasses[type]||0)+1;
      }catch{}
    }
  }));
  const exit=await completed;if(exit!==0)throw Error('Private local Docker log stream failed; no zero-count inference');
  result.cases.push(...Object.values(rows));
}
result.localStackTraceCapEffective=result.cases.some(row=>row.stackTraceCharacters>0)&&result.cases.every(row=>row.largestStackTraceCharacters<=2048);
if(!result.localStackTraceCapEffective)throw Error('Stack-trace cap not confirmed from retained local log counts; do not claim a tested cap');
writeFileSync(`${root}/matrix-v2/logging-diagnostic-log-counts.json`,JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
