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
[ -z "$(git -C "$REPO_ROOT" status --porcelain)" ] || die "preflight requires a clean reviewed checkout"
sha=$(git -C "$REPO_ROOT" rev-parse HEAD)
collected=$(date -u +%FT%TZ)
domain=$(aws cloudfront get-distribution --id E3N39GHVFV6CT3 --query Distribution.DomainName --output text)
[[ "$domain" =~ ^[a-z0-9.-]+\.cloudfront\.net$ ]] || die "invalid CloudFront domain"
services=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
  --query '{services:services[].{serviceName:serviceName,status:status,desiredCount:desiredCount,runningCount:runningCount,pendingCount:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState}},failures:failures}' \
  --output json)
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
  '{schema:1,account:"402178232802",region:"ap-southeast-1",cluster:"blocbuy-swe5001",
    collectedAt:$at,toolingSha:$sha,mode:$mode,baseUrl:$base,
    services:$services,tasks:$tasks,targets:$targets,policies:$policies}'
