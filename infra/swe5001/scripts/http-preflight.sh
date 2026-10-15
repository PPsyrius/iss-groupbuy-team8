#!/usr/bin/env bash
# Read-only, secret-free AWS observations for the #458 HTTP driver. No Terraform apply,
# scaling changes or task starts. The k6 setup validates the captured deployment/scaling
# state against the reviewed digest and refuses stale snapshots before sending HTTP.
# shellcheck source=./lib.sh source-path=SCRIPTDIR
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[ "$#" -eq 1 ] || die "usage: http-preflight.sh v2|v3"
case "$1" in
  v2) service=monolith ;;
  v3) service=commerce-svc ;;
  *) die "usage: http-preflight.sh v2|v3" ;;
esac
guard_account
umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ] || die "preflight requires a clean reviewed checkout"
sha=$(git -C "$REPO_ROOT" rev-parse HEAD)
collected=$(date -u +%FT%TZ)
# Origin headers and listener header conditions stay in private temporary files,
# only long enough to compare them. No header values enter the snapshot or argv.
aws cloudfront get-distribution --id E3N39GHVFV6CT3 \
  --query 'Distribution.{id:Id,domainName:DomainName,status:Status,enabled:DistributionConfig.Enabled,behaviors:DistributionConfig.CacheBehaviors.Items[].{pathPattern:PathPattern,targetOriginId:TargetOriginId,functions:FunctionAssociations.Quantity,lambdas:LambdaFunctionAssociations.Quantity,cachePolicyId:CachePolicyId},origins:DistributionConfig.Origins.Items[].{id:Id,domainName:DomainName,path:OriginPath,protocolPolicy:CustomOriginConfig.OriginProtocolPolicy,httpPort:CustomOriginConfig.HTTPPort,httpsPort:CustomOriginConfig.HTTPSPort,headers:CustomHeaders.Items}}' \
  --output json > "$work/edge.json"
domain=$(jq -er '.domainName' "$work/edge.json")
[[ "$domain" =~ ^[a-z0-9.-]+\.cloudfront\.net$ ]] || die "invalid CloudFront domain"
services=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
  --query '{services:services[].{serviceName:serviceName,status:status,desiredCount:desiredCount,runningCount:runningCount,pendingCount:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState},loadBalancers:loadBalancers[].{targetGroupArn:targetGroupArn,containerName:containerName,containerPort:containerPort}},failures:failures}' \
  --output json)
group=$(jq -er '.services | select(length == 1) | .[0].loadBalancers | select(length == 1) | .[0].targetGroupArn | select(type == "string" and length > 0)' <<< "$services") || die "expected one ECS target group"
groups=$(aws elbv2 describe-target-groups --target-group-arns "$group" \
  --query 'TargetGroups[].{arn:TargetGroupArn,loadBalancerArns:LoadBalancerArns,port:Port,protocol:Protocol}' --output json)
alb=$(jq -er 'select(length == 1) | .[0].loadBalancerArns | select(length == 1) | .[0] | select(type == "string" and length > 0)' <<< "$groups") || die "expected one target-group ALB"
balancers=$(aws elbv2 describe-load-balancers --load-balancer-arns "$alb" \
  --query 'LoadBalancers[].{arn:LoadBalancerArn,dnsName:DNSName,type:Type,state:State.Code}' --output json)
listeners=$(aws elbv2 describe-listeners --load-balancer-arn "$alb" \
  --query 'Listeners[].{arn:ListenerArn,loadBalancerArn:LoadBalancerArn,port:Port,protocol:Protocol}' --output json)
listener=$(jq -ner --slurpfile edge "$work/edge.json" --argjson listeners "$listeners" '
  [$edge[0].origins[] | select(.id == "alb-runtime")] | select(length == 1) | .[0] as $origin |
  (if $origin.protocolPolicy == "http-only" then {protocol:"HTTP",port:$origin.httpPort}
   elif ($origin.protocolPolicy == "https-only" or $origin.protocolPolicy == "match-viewer") then {protocol:"HTTPS",port:$origin.httpsPort}
   else error("unsupported origin protocol") end) as $leg |
  [$listeners[] | select(.protocol == $leg.protocol and .port == $leg.port)] |
  select(length == 1) | .[0].arn') || die "cannot resolve the origin listener"
aws elbv2 describe-rules --listener-arn "$listener" \
  --query 'Rules[].{priority:Priority,isDefault:IsDefault,transforms:Transforms[].Type,actions:Actions[].{type:Type,targetGroupArn:TargetGroupArn,forwardGroups:ForwardConfig.TargetGroups[].{arn:TargetGroupArn,weight:Weight}},conditions:Conditions[].{field:Field,pathPatterns:PathPatternConfig.Values,pathRegex:PathPatternConfig.RegexValues,header:HttpHeaderConfig}}' \
  --output json > "$work/rules.json"
rules=$(jq -c --slurpfile edge "$work/edge.json" '
  [$edge[0].origins[] | select(.id == "alb-runtime") | .headers[]? |
    select((.HeaderName | ascii_downcase) == "x-origin-secret") | .HeaderValue] as $secret |
  map([.conditions[]? | select(.field == "http-header") | .header] as $headers |
    {priority,isDefault,actions,hasTransforms:((.transforms // []) | length > 0),
     conditionFields:[.conditions[]?.field],
     pathPatterns:[.conditions[]? | select(.field == "path-pattern") | (.pathPatterns // [])[]],
     hasPathRegex:any(.conditions[]?; ((.pathRegex // []) | length) > 0),
     originHeaderMatches:(($secret | length) == 1 and ($secret[0] | type == "string" and length > 0) and
       ($headers | length) == 1 and (($headers[0].HttpHeaderName // "" | ascii_downcase) == "x-origin-secret") and
       $headers[0].Values == $secret)})' "$work/rules.json")
edge=$(jq -c 'del(.origins[].headers)' "$work/edge.json")
tasks=$(aws ecs list-tasks --cluster "$CLUSTER" --service-name "$service" --desired-status RUNNING \
  --query taskArns --output json)
jq -e 'type == "array" and length == 1 and all(.[]; type == "string")' <<< "$tasks" > /dev/null || die "expected exactly one running task"
task=$(jq -r '.[0]' <<< "$tasks")
observed=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$task" \
  --query '{tasks:tasks[].{taskArn:taskArn,taskDefinitionArn:taskDefinitionArn,lastStatus:lastStatus,desiredStatus:desiredStatus,availabilityZone:availabilityZone,containers:containers[].{name:name,imageDigest:imageDigest}},failures:failures}' \
  --output json)
resource="service/$CLUSTER/$service"
targets=$(aws application-autoscaling describe-scalable-targets --service-namespace ecs \
  --resource-ids "$resource" --scalable-dimension ecs:service:DesiredCount --output json)
policies=$(aws application-autoscaling describe-scaling-policies --service-namespace ecs \
  --resource-id "$resource" --scalable-dimension ecs:service:DesiredCount --output json)
jq -n --arg at "$collected" --arg sha "$sha" --arg mode "$1" --arg base "https://$domain" \
  --argjson services "$services" --argjson tasks "$observed" --argjson targets "$targets" --argjson policies "$policies" \
  --argjson edge "$edge" --argjson groups "$groups" --argjson balancers "$balancers" --argjson listeners "$listeners" --arg listener "$listener" --argjson rules "$rules" \
  '{schema:1,account:"402178232802",region:"ap-southeast-1",cluster:"blocbuy-swe5001",
    collectedAt:$at,toolingSha:$sha,mode:$mode,baseUrl:$base,
    services:$services,tasks:$tasks,targets:$targets,policies:$policies,
    routing:{edge:$edge,targetGroups:$groups,loadBalancers:$balancers,listeners:$listeners,rulesListenerArn:$listener,rules:$rules}}'
