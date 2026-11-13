#!/usr/bin/env bash
# Capture contract only: all cloud/git calls are stubbed; no AWS credentials or writes.
set -euo pipefail
dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=test CALLS="$work/calls"
export FAULT=normal
export ALB='arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:loadbalancer/app/blocbuy-swe5001/123'
export LISTENER='arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:listener/app/blocbuy-swe5001/123/789'
export CPU_POLICY='arn:aws:autoscaling:ap-southeast-1:402178232802:scalingPolicy:11111111-1111-1111-1111-111111111111:resource/ecs/service/blocbuy-swe5001/commerce-svc:policyName/blocbuy-swe5001-commerce-cpu-headroom'
export SECRET_SENTINEL='private-origin-header-fixture-do-not-emit'
export TMPDIR=$work
git() {
  case "$*" in
    *'status --porcelain') [ "$FAULT" != dirty ] || printf ' M file\n' ;;
    *'rev-parse HEAD') printf '%040d\n' 0 ;;
    *) return 1 ;;
  esac
}
terraform() { return 1; }
aws() {
  local service=${service:-monolith}
  local group="arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:targetgroup/$service/456"
  printf '%s\n' "$*" >> "$CALLS"
  [ "$FAULT" != denied ] || return 1
  case "$1 $2" in
    'sts get-caller-identity') printf '402178232802\n' ;;
    'cloudfront get-distribution') jq -nc --arg secret "$SECRET_SENTINEL" '
      {id:"E3N39GHVFV6CT3",domainName:"d3lvq02o2e7tcn.cloudfront.net",status:"Deployed",enabled:true,
       behaviors:[{pathPattern:"/api/*",targetOriginId:"alb-runtime",functions:0,lambdas:0,cachePolicyId:"4135ea2d-6df8-44a3-9df3-4b5a84be39ad"}],
       origins:[{id:"alb-runtime",domainName:"runtime.elb.amazonaws.com",path:"",protocolPolicy:"http-only",httpPort:80,httpsPort:443,
       headers:[{HeaderName:"X-Origin-Secret",HeaderValue:$secret}]}]}' ;;
    'ecs describe-services') jq -nc --arg service "$service" --arg group "$group" '
      {services:[{serviceName:$service,loadBalancers:[{targetGroupArn:$group,containerName:$service,containerPort:8589}]}],failures:[]}' ;;
    'ecs list-tasks')
      if [ "$FAULT" = extra_task ]; then printf '["task:1","task:2"]\n';
      elif [ "$FAULT" = two_tasks ]; then printf '["task:1","task:2"]\n';
      else printf '["task:1"]\n'; fi ;;
    'ecs describe-tasks') printf '{"tasks":[],"failures":[]}\n' ;;
    'application-autoscaling describe-scalable-targets') printf '{"ScalableTargets":[]}\n' ;;
    'application-autoscaling describe-scaling-policies')
      if [ "$FAULT" = cpu_headroom ] || [ "$FAULT" = cpu_alarm_denied ]; then
        printf '{"ScalingPolicies":[{"PolicyName":"blocbuy-swe5001-commerce-cpu-headroom","PolicyType":"StepScaling"}]}\n'
      else
        printf '{"ScalingPolicies":[]}\n'
      fi ;;
    'cloudwatch describe-alarms')
      [ "$FAULT" != cpu_alarm_denied ] || return 1
      local query=""
      while [ "$#" -gt 0 ]; do
        if [ "$1" = --query ]; then query=$2; shift 2; else shift; fi
      done
      [ "$query" = 'MetricAlarms[].{name:AlarmName,namespace:Namespace,metric:MetricName,statistic:Statistic,period:Period,evaluationPeriods:EvaluationPeriods,datapointsToAlarm:DatapointsToAlarm,threshold:Threshold,comparison:ComparisonOperator,missingData:TreatMissingData,actionsEnabled:ActionsEnabled,actions:AlarmActions,dimensions:Dimensions}' ] || return 1
      jq -nc --arg policy "$CPU_POLICY" '[{name:"blocbuy-swe5001-commerce-cpu-headroom",namespace:"AWS/ECS",
        metric:"CPUUtilization",statistic:"Average",period:60,evaluationPeriods:1,datapointsToAlarm:1,threshold:70,
        comparison:"GreaterThanOrEqualToThreshold",missingData:"notBreaching",actionsEnabled:true,actions:[$policy],
        dimensions:[{Name:"ClusterName",Value:"blocbuy-swe5001"},{Name:"ServiceName",Value:"commerce-svc"}]}]' ;;
    'elbv2 describe-target-groups') jq -nc --arg group "$group" --arg alb "$ALB" '[{arn:$group,loadBalancerArns:[$alb],port:8589,protocol:"HTTP"}]' ;;
    'elbv2 describe-load-balancers') jq -nc --arg alb "$ALB" '[{arn:$alb,dnsName:"runtime.elb.amazonaws.com",type:"application",state:"active"}]' ;;
    'elbv2 describe-listeners') jq -nc --arg alb "$ALB" --arg listener "$LISTENER" '[{arn:$listener,loadBalancerArn:$alb,port:80,protocol:"HTTP"}]' ;;
    'elbv2 describe-rules')
      [ "$FAULT" != route_read_denied ] || return 1
      jq -nc --arg group "$group" --arg secret "$SECRET_SENTINEL" --arg fault "$FAULT" '
        [{priority:"100",isDefault:false,transforms:null,actions:[{type:"forward",targetGroupArn:$group,forwardGroups:[{arn:$group,weight:1}]}],
          conditions:[{field:"path-pattern",pathPatterns:["/api/*"]},
          {field:"http-header",header:{HttpHeaderName:"X-Origin-Secret",Values:[if $fault=="header_mismatch" then "different" else $secret end]}}]},
         {priority:"default",isDefault:true,actions:[{type:"fixed-response"}],conditions:[]}]' ;;
    *) return 1 ;;
  esac
}
export -f git terraform aws
bash "$dir/http-preflight.sh" v2 > "$work/report.json"
jq -e '.schema == 1 and .mode == "v2" and .account == "402178232802" and .baseUrl == "https://d3lvq02o2e7tcn.cloudfront.net" and (.toolingSha | length) == 40' "$work/report.json" > /dev/null
printf 'PASS preflight records fixed account/origin and tooling source\n'
grep -q -- '--services monolith' "$CALLS"
printf 'PASS V2 captures the monolith, not distributed commerce\n'
bash "$dir/http-preflight.sh" v3 > "$work/report.json"
grep -q -- '--services commerce-svc' "$CALLS"
printf 'PASS V3 captures distributed commerce\n'
for mode in v5 identity; do
  FAULT=two_tasks bash "$dir/http-preflight.sh" "$mode" > "$work/drill.json"
  jq -e --arg mode "$mode" '.mode == $mode' "$work/drill.json" > /dev/null
  grep -q -- '--tasks task:1 task:2' "$CALLS"
  if bash "$dir/http-preflight.sh" "$mode" > /dev/null 2>&1; then exit 1; fi
  printf 'PASS %s captures two commerce tasks and refuses a one-task start\n' "$mode"
done
FAULT=cpu_headroom bash "$dir/http-preflight.sh" v3 > "$work/report.json"
jq -e --arg policy "$CPU_POLICY" '.cpuHeadroom.alarms == [{name:"blocbuy-swe5001-commerce-cpu-headroom",namespace:"AWS/ECS",
  metric:"CPUUtilization",statistic:"Average",period:60,evaluationPeriods:1,datapointsToAlarm:1,threshold:70,
  comparison:"GreaterThanOrEqualToThreshold",missingData:"notBreaching",actionsEnabled:true,actions:[$policy],
  dimensions:[{Name:"ClusterName",Value:"blocbuy-swe5001"},{Name:"ServiceName",Value:"commerce-svc"}]}]' "$work/report.json" > /dev/null
printf 'PASS opt-in CPU policy includes independent alarm binding evidence\n'
jq -e '.routing.rules[0].originHeaderMatches == true and .routing.rules[0].pathPatterns == ["/api/*"] and .routing.rulesListenerArn == .routing.listeners[0].arn' "$work/report.json" > /dev/null
printf 'PASS capture binds origin listener and compares protected header conditions\n'
if grep -Fq "$SECRET_SENTINEL" "$work/report.json"; then exit 1; fi
printf 'PASS origin/header values are absent from evidence\n'
FAULT=header_mismatch bash "$dir/http-preflight.sh" v2 > "$work/report.json"
jq -e '.routing.rules[0].originHeaderMatches == false' "$work/report.json" > /dev/null
printf 'PASS changed origin-header condition is recorded as a mismatch\n'
[ -z "$(find "$work" -mindepth 1 -maxdepth 1 -type d -name 'tmp.*' -print)" ]
printf 'PASS private route inputs are removed after capture\n'
if grep -Eq 'update-|register-|run-task|publish|send-message|put-' "$CALLS"; then exit 1; fi
printf 'PASS capture issues no mutation or task-start requests\n'
for fault in dirty denied extra_task route_read_denied cpu_alarm_denied; do
  if FAULT=$fault bash "$dir/http-preflight.sh" v2 > "$work/fault.json" 2> "$work/error"; then
    printf 'FAIL preflight accepted %s\n' "$fault" >&2
    exit 1
  fi
  [ ! -s "$work/fault.json" ]
  printf 'PASS capture fails closed on %s\n' "$fault"
done
[ -z "$(find "$work" -mindepth 1 -maxdepth 1 -type d -name 'tmp.*' -print)" ]
printf 'PASS failed captures also remove private route inputs\n'
if bash "$dir/http-preflight.sh" invalid > /dev/null 2>&1; then exit 1; fi
printf 'PASS invalid preflight mode is rejected\n'
