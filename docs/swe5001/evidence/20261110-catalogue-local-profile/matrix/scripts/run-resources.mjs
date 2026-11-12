// Sequential, local-only diagnostics. No AWS operations, resource deletion or existing-container changes.
import {spawn, spawnSync} from 'node:child_process';
import {mkdirSync,readFileSync,writeFileSync,appendFileSync,existsSync} from 'node:fs';
import http from 'node:http';
import {setTimeout as delay} from 'node:timers/promises';
const ROOT=process.env.RESULTS_ROOT, REPO=process.env.REPO_ROOT, M=`${ROOT}/matrix-v2`;
if(!ROOT?.includes('/Temp/opencode/swe5001-profile-20261110') || !REPO?.endsWith('/iss-groupbuy-team8'))throw Error('Explicit local diagnostic mount paths required');
const NET='blocbuy-profile-20261110', PG='blocbuy-matrix-postgres', REDIS='blocbuy-matrix-redis';
const JDK='eclipse-temurin:21-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d';
const manifest={evidenceKind:'local-diagnostic-not-AWS-acceptance',startedAt:new Date().toISOString(),runs:[],problems:[]};
function save(){writeFileSync(`${M}/resource-index.json`,JSON.stringify(manifest,null,2));}
function docker(args,input){const r=spawnSync('docker',args,{input,encoding:'utf8',timeout:60000,maxBuffer:8*1024*1024});if(r.status!==0)throw Error(`Local docker ${args[0]} failed: ${(r.stderr||r.error?.message||'').slice(-1800)}`);return r.stdout.trim();}
function sql(query){return docker(['exec','-i',PG,'psql','--no-psqlrc','-U','blocbuy_profile','-d','Matrix2','-qAt','-v','ON_ERROR_STOP=1'],query);}
async function request(url,method='GET',body,unix=false){return await new Promise((resolve,reject)=>{
  const options=unix?{socketPath:'/var/run/docker.sock',path:url,method}:{...new URL(url),method};
  // URL properties are non-enumerable: pass the URL directly for ordinary HTTP.
  const r=unix?http.request(options,done):http.request(url,{method,headers:body?{'Content-Type':'application/json'}:{}},done);
  function done(response){let text='';response.setEncoding('utf8');response.on('data',s=>text+=s);response.on('end',()=>response.statusCode<300?resolve(text):reject(Error(`HTTP ${response.statusCode}: ${text.slice(0,200)}`)));}
  r.setTimeout(8000,()=>r.destroy(Error('Local diagnostic read timeout')));r.on('error',reject);r.end(body?JSON.stringify(body):undefined);
});}
async function toxic(name,ms=0){
  const url=`http://toxiproxy:8474/proxies/${name}`;
  const config=JSON.parse(await request(url));
  for(const t of config.toxics)await request(`${url}/toxics/${t.name}`,'DELETE');
  if(ms)await request(`${url}/toxics`,'POST',{name:'controlled-latency',type:'latency',stream:'downstream',toxicity:1,attributes:{latency:ms,jitter:0}});
}
async function clear(){await request('http://toxiproxy:8474/proxies/redis','POST',{enabled:true});await toxic('redis');await toxic('postgres');}
async function ready(name){const started=Date.now();while(Date.now()-started<240000){
  try{const h=JSON.parse(await request(`http://${name}:8589/api/actuator/health`));if(h.status==='UP')return {healthyAt:new Date().toISOString(),waitMs:Date.now()-started};}catch{}
  const state=JSON.parse(docker(['inspect',name,'--format','{{json .State}}']));if(!state.Running)throw Error(`Local ${name} exited before readiness (exit ${state.ExitCode})`);
  await delay(2000);
}throw Error(`Local ${name} readiness deadline exceeded`);}
function startApp(label,{proxy=false,jobs=false,cpu=0.5,memory='1g'}={}){
  const name=`blocbuy-matrix2-${label}`;
  const env={SPRING_PROFILES_ACTIVE:`ec2,commerce,aws${jobs?',demo':''}`,
    DB_URL:'jdbc:postgresql://toxiproxy:15432/Matrix2?currentSchema=domain&sslmode=verify-full&sslrootcert=/results/matrix/certs/ca.crt',
    REDIS_HOST:'toxiproxy',REDIS_PORT:'16379',REDIS_SSL:'true',FRONTEND_URL:'http://localhost:8589',IDENTITY_BASE_URL:'http://127.0.0.1:9',
    GOOGLE_REDIRECT_URI:'http://localhost:8589/api/v1/auth/oauth2/google/callback',SPRING_FLYWAY_LOCATIONS:'classpath:db/migration,classpath:db/swe5001,classpath:db/seed',
    SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE:'5',BLOCBUY_SCHEDULING_ENABLED:String(jobs),BLOCBUY_EVENTS_ENABLED:String(jobs),
    BLOCBUY_EVENTS_RELAY_ENABLED:String(jobs),BLOCBUY_EVENTS_CONSUMER_ENABLED:'false',BLOCBUY_STRIPE_CHECKOUT_ENABLED:'false',TYPESAFE_ENABLED:'false',
    JAVA_TOOL_OPTIONS:'-XX:MaxRAMPercentage=75 -Djavax.net.ssl.trustStore=/results/matrix/certs/truststore.p12 -Djavax.net.ssl.trustStorePassword=public-profile-ca'};
  if(jobs)Object.assign(env,{BLOCBUY_EVENTS_TRANSPORT:'sqs',BLOCBUY_EVENTS_SQS_ENDPOINT:'http://elasticmq:9324',BLOCBUY_EVENTS_SQS_QUEUE_URL:'http://elasticmq:9324/000000000000/notification-events',AWS_REGION:'us-east-1',AWS_ACCESS_KEY_ID:'test',AWS_SECRET_ACCESS_KEY:'test'});
  const args=['run','-d','--name',name,'--network',NET,`--cpus=${cpu}`,`--memory=${memory}`,'--user=10001:10001','--read-only','--tmpfs','/tmp:rw,size=256m,mode=1777',
    '--env-file',`${ROOT}/local.env`,'--mount',`type=bind,source=${ROOT},target=/results`];
  for(const [key,value] of Object.entries(env))args.push('-e',`${key}=${value}`);
  const java=`exec java -Xlog:gc*:file=/results/matrix-v2/${label}-gc.log:time,uptime,level,tags -jar /results/commerce-baseline.jar`;
  args.push(proxy?'blocbuy-profile-envoy-jdk:20261110':JDK,'bash','-c',proxy?`envoy -c /results/matrix-v2/envoy.yaml --concurrency 1 --log-level warning > /results/matrix-v2/${label}-envoy.log 2>&1 & ${java}`:java);
  const startedAt=new Date().toISOString();docker(args);return {name,startedAt,proxy,jobs};
}
function metrics(text){const out={};for(const line of text.split('\n')){const m=/^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]+)\})?\s+([0-9.eE+-]+)$/.exec(line);if(m&&(/^(hikaricp_|jvm_gc_pause_|jvm_gc_memory_|jvm_threads_|process_cpu_|system_cpu_|http_server_requests_|blocbuy_)/.test(m[1])))out[m[1]+(m[2]?`{${m[2]}}`:'')]=Number(m[3]);}return out;}
async function observe(app,label,previous){
  const row={at:new Date().toISOString(),label};
  try{row.metrics=metrics(await request(`http://${app.name}:8589/api/actuator/prometheus`));}catch(e){row.metricsError=e.message;}
  row.resources=[];
  for(const name of [app.name,PG,REDIS,`blocbuy-matrix2-k6-${label}`]){
    try{const s=JSON.parse(await request(`/v1.44/containers/${name}/stats?stream=false&one-shot=true`,'GET',undefined,true));
      const old=previous.get(name),current={cpu:s.cpu_stats.cpu_usage.total_usage,system:s.cpu_stats.system_cpu_usage};previous.set(name,current);
      row.resources.push({name,cpuPercentOneCore:old&&current.system>old.system?(current.cpu-old.cpu)/(current.system-old.system)*s.cpu_stats.online_cpus*100:null,
        memoryBytes:s.memory_stats.usage,throttling:s.cpu_stats.throttling_data,pids:s.pids_stats.current});
    }catch(e){row.resources.push({name,error:e.message});}
  }
  appendFileSync(`${M}/${label}/monitor.jsonl`,JSON.stringify(row)+'\n');
}
async function run(app,label,{rate=200,seconds=60,port=8589,actions=[]}={}){
  mkdirSync(`${M}/${label}`);const startedAt=new Date().toISOString();
  const metadata={label,app:app.name,rate,seconds,port,startedAt,actions:actions.map(a=>({afterSeconds:a.at,description:a.description}))};
  metadata.actualLimits=JSON.parse(docker(['inspect',app.name,'--format','{"nanoCpus":{{.HostConfig.NanoCpus}},"memoryBytes":{{.HostConfig.Memory}}}']));
  metadata.hostMemory=Object.fromEntries(readFileSync('/proc/meminfo','utf8').split('\n').filter(line=>/^(MemTotal|MemAvailable|SwapTotal|SwapFree):/.test(line)).map(line=>{const [key,value]=line.split(':');return [key,Number(value.trim().split(/\s+/)[0])*1024];}));
  metadata.toxiproxy=JSON.parse(await request('http://toxiproxy:8474/proxies'));
  writeFileSync(`${M}/${label}/context.json`,JSON.stringify(metadata,null,2));
  const args=['run','--name',`blocbuy-matrix2-k6-${label}`,'--user=0:0','--network',`container:${app.name}`,'--cpus=4','--memory=2g',
    '--mount',`type=bind,source=${ROOT},target=/results`,'--mount',`type=bind,source=${REPO},target=/repo,readonly`,
    '-e',`LOCAL_CASE=${label}`,'-e',`LOCAL_RATE=${rate}`,'-e',`LOCAL_SECONDS=${seconds}`,'-e',`LOCAL_PORT=${port}`,
    'grafana/k6:1.6.1','run','--quiet','--out',`json=/results/matrix-v2/${label}/samples.jsonl`,'/results/matrix-v2/driver-resources.js'];
  const child=spawn('docker',args,{stdio:['ignore','pipe','pipe']});let logs='';child.stdout.on('data',x=>logs+=x);child.stderr.on('data',x=>logs+=x);
  const finished=new Promise(resolve=>child.on('close',resolve));let done=false;finished.then(()=>done=true);
  // Actions use the host-observed generator launch, with actual timestamps retained; boundaries are diagnostic, not exact k6 event windows.
  const abort=new AbortController();
  const tasks=actions.map(action=>(async()=>{try{await delay(action.at*1000,undefined,{signal:abort.signal});if(done)return;await action.run();appendFileSync(`${M}/${label}/actions.jsonl`,JSON.stringify({at:new Date().toISOString(),description:action.description})+'\n');}catch(e){if(e.name!=='AbortError')throw e;}})());
  const previous=new Map();while(!done){await observe(app,label,previous);if(!done)await Promise.race([delay(5000),finished]);}
  const code=await finished;abort.abort();await Promise.all(tasks);writeFileSync(`${M}/${label}/generator.log`,logs);writeFileSync(`${M}/${label}/exit.txt`,String(code));
  metadata.finishedAt=new Date().toISOString();metadata.exitCode=code;
  if(existsSync(`${M}/${label}/summary.json`)){
    const s=JSON.parse(readFileSync(`${M}/${label}/summary.json`)).summary.metrics;
    metadata.result={requests:s.browse_requests?.values.count,errors:s.browse_errors?.values,latency:s.browse_latency?.values,drops:s.dropped_iterations?.values.count};
  }else metadata.problem='No summary; do not treat as zero errors or successful measurement';
  metadata.state=JSON.parse(docker(['inspect',app.name,'--format','{{json .State}}']));
  metadata.pg=JSON.parse(sql("SELECT json_build_object('connections',(SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()),'statements',(SELECT json_agg(x) FROM (SELECT queryid::text,calls,total_exec_time,mean_exec_time,max_exec_time,rows FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 12) x));"));
  metadata.tls=JSON.parse(sql("SELECT json_build_object('encryptedConnections',count(*) FILTER (WHERE ssl),'versions',array_agg(DISTINCT version) FILTER (WHERE ssl)) FROM pg_stat_ssl;"));
  writeFileSync(`${M}/${label}/result.json`,JSON.stringify(metadata,null,2));manifest.runs.push(metadata);save();console.log(JSON.stringify({label,exit:code,...metadata.result}));
  if(!metadata.result || ![0,99].includes(code))throw Error(`Generator startup/output failure in ${label}; remaining cases refused`);
  return metadata;
}
function freeze(app){docker(['pause',app.name]);}
async function main(){
  if(existsSync(`${M}/resource-index.json`))throw Error('Resource receipt already exists; no blind repeat');save();
  await clear();
  const failure={name:'blocbuy-matrix2-failure'};
  docker(['unpause',failure.name]);await ready(failure.name);
  docker(['exec',failure.name,'jcmd','1','JFR.start','name=warmed-faults','settings=profile','filename=/results/matrix-v2/warmed-faults.jfr','maxsize=128m','dumponexit=true']);
  await run(failure,'warm-fault-warmup',{rate:50,seconds:30});await run(failure,'warm-fault-control');
  await run(failure,'warm-cache-expiry',{actions:[{at:15,description:'Flush matrix-owned Redis cache once',run:async()=>docker(['exec',REDIS,'sh','-c','export REDISCLI_AUTH="$REDIS_PASSWORD"; redis-cli --tls --cacert /certs/ca.crt -p 6380 FLUSHDB'])}]});
  await run(failure,'warm-redis-slow',{seconds:70,actions:[{at:15,description:'Inject 3500 ms downstream Redis latency',run:async()=>toxic('redis',3500)},{at:40,description:'Remove Redis latency; observe recovery',run:async()=>toxic('redis')}]});
  await run(failure,'warm-redis-down',{seconds:70,actions:[{at:15,description:'Disable matrix Redis proxy',run:async()=>request('http://toxiproxy:8474/proxies/redis','POST',{enabled:false})},{at:40,description:'Restore matrix Redis proxy',run:async()=>request('http://toxiproxy:8474/proxies/redis','POST',{enabled:true})}]});
  await clear();await run(failure,'warm-fault-recovery');
  docker(['exec',failure.name,'jcmd','1','JFR.stop','name=warmed-faults','filename=/results/matrix-v2/warmed-faults.jfr']);freeze(failure);
  // The earlier 400-RPS proxy case had drops. Repeat once with all 1000 VUs preallocated before interpreting capacity.
  const proxy={name:'blocbuy-matrix2-proxy'};docker(['unpause',proxy.name]);await ready(proxy.name);
  await run(proxy,'warm-proxy-400',{rate:400,port:8588});freeze(proxy);
  const existing=Number(sql('SELECT count(*) FROM pg_stat_activity WHERE datname IS NOT NULL;'));
  if(existing+20>74)throw Error('Local four-case pool reservation does not fit conservative 74-slot budget');
  const cases=[['resource05m1',0.5,'1g'],['resource1m1',1,'1g'],['resource05m2',0.5,'2g'],['resource1m2',1,'2g']];
  manifest.resourcePlan={existingConnections:existing,maximumNewPools:20,cases,limits:'1 CPU / 1 GiB is CPU-only isolation, not a valid Fargate task size; 1 CPU / 2 GiB is the deployable-pair candidate.'};save();
  for(const [label,cpu,memory] of cases){
    docker(['exec',REDIS,'sh','-c','export REDISCLI_AUTH="$REDIS_PASSWORD"; redis-cli --tls --cacert /certs/ca.crt -p 6380 FLUSHDB']);
    const app=startApp(label,{cpu,memory});manifest[`${label}Startup`]=await ready(app.name);save();
    await run(app,`${label}-cold`);freeze(app);
  }
  manifest.finishedAt=new Date().toISOString();manifest.completed=true;save();console.log('Warmed fault isolation and cold resource factorial completed. No AWS, requirement changes or resource deletion.');
}
try{await main();}catch(error){manifest.problems.push({at:new Date().toISOString(),error:error.message});save();await clear().catch(()=>{});console.error(error.message);process.exitCode=1;}
