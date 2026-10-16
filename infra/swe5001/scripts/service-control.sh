# shellcheck shell=bash
# Sourced by environment/readiness scripts. Counts after creation belong to these
# operational controls, not Terraform. Snapshots contain no credentials.

pause_services() {
  local snapshot=$1 clusters service info scaling entry
  shift
  printf '[]\n' > "$snapshot"
  # shellcheck disable=SC2016 # JMESPath literal, not a shell command substitution
  clusters=$(aws ecs describe-clusters --clusters "$CLUSTER" \
    --query 'clusters[?status==`ACTIVE`].clusterArn' --output json) || return 1
  jq -e 'type == "array"' <<< "$clusters" > /dev/null || return 1
  [ "$(jq length <<< "$clusters")" -gt 0 ] || return 0

  for service in "$@"; do
    case "$service" in identity-svc | commerce-svc | notification-svc | monolith) ;; *) return 1 ;; esac
    info=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
      --query '{services:services,failures:failures}' --output json) || return 1
    jq -e '(.services | type == "array") and all(.failures[]?; .reason == "MISSING")' <<< "$info" > /dev/null || return 1
    [ "$(jq '.services | length' <<< "$info")" -gt 0 ] || continue
    jq -e '.services[0] | .status == "ACTIVE" and (.desiredCount | type == "number" and . >= 0 and floor == .)' <<< "$info" > /dev/null || return 1
    scaling=$(aws application-autoscaling describe-scalable-targets --service-namespace ecs \
      --resource-ids "service/$CLUSTER/$service" --scalable-dimension ecs:service:DesiredCount \
      --query 'ScalableTargets[0]' --output json) || return 1
    jq -e '. == null or (.MinCapacity >= 0 and .MaxCapacity >= .MinCapacity and (.SuspendedState | type == "object"))' <<< "$scaling" > /dev/null || return 1
    entry=$(jq --arg service "$service" --argjson scaling "$scaling" \
      '{service: $service, desired: .services[0].desiredCount, scaling: $scaling}' <<< "$info") || return 1
    jq --argjson entry "$entry" '. + [$entry]' "$snapshot" > "$snapshot.next" || return 1
    mv "$snapshot.next" "$snapshot"
    if [ "$scaling" != null ]; then
      aws application-autoscaling register-scalable-target --service-namespace ecs \
        --resource-id "service/$CLUSTER/$service" --scalable-dimension ecs:service:DesiredCount \
        --min-capacity "$(jq .MinCapacity <<< "$scaling")" --max-capacity "$(jq .MaxCapacity <<< "$scaling")" \
        --suspended-state '{"DynamicScalingInSuspended":true,"DynamicScalingOutSuspended":true,"ScheduledScalingSuspended":true}' > /dev/null || return 1
    fi
    aws ecs update-service --cluster "$CLUSTER" --service "$service" --desired-count 0 > /dev/null || return 1
    aws ecs wait services-stable --cluster "$CLUSTER" --services "$service" || return 1
    info=$(aws ecs describe-services --cluster "$CLUSTER" --services "$service" \
      --query 'services[0].{desired:desiredCount,running:runningCount}' --output json) || return 1
    jq -e '.desired == 0 and .running == 0' <<< "$info" > /dev/null || return 1
  done
}

restore_services() {
  local snapshot=$1 entry service desired scaling failed=0
  jq -e 'type == "array" and all(.[]; (.service == "commerce-svc" or .service == "notification-svc" or .service == "identity-svc" or .service == "monolith") and (.desired | type == "number" and . >= 0 and floor == .) and (.scaling == null or (.scaling | type == "object")))' "$snapshot" > /dev/null || return 1
  # Continue restoration after one API failure; return failure if any action fails.
  while IFS= read -r entry; do
    service=$(jq -r .service <<< "$entry")
    desired=$(jq -r .desired <<< "$entry")
    scaling=$(jq -c .scaling <<< "$entry")
    aws ecs update-service --cluster "$CLUSTER" --service "$service" --desired-count "$desired" > /dev/null || failed=1
    if [ "$scaling" != null ]; then
      aws application-autoscaling register-scalable-target --service-namespace ecs \
        --resource-id "service/$CLUSTER/$service" --scalable-dimension ecs:service:DesiredCount \
        --min-capacity "$(jq .MinCapacity <<< "$scaling")" --max-capacity "$(jq .MaxCapacity <<< "$scaling")" \
        --suspended-state "$(jq -c .SuspendedState <<< "$scaling")" > /dev/null || failed=1
    fi
  done < <(jq -c '.[]' "$snapshot")
  return "$failed"
}
